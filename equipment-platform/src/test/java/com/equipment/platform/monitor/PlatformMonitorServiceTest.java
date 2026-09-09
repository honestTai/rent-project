package com.equipment.platform.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PlatformMonitorServiceTest {

    private DiscoveryClient discoveryClient;
    private EurekaClientConfigBean eurekaConfig;
    private RestTemplate restTemplate;
    private MockRestServiceServer http;
    private PlatformMonitorService service;

    @BeforeEach
    void setUp() {
        discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances(anyString())).thenReturn(Collections.emptyList());
        eurekaConfig = new EurekaClientConfigBean();
        eurekaConfig.getServiceUrl().clear();
        restTemplate = new RestTemplate();
        http = MockRestServiceServer.bindTo(restTemplate).build();
        service = new PlatformMonitorService(discoveryClient, new ObjectMapper(), eurekaConfig, "", restTemplate);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void registeredServiceAcceptsRawAndWrappedHealth(boolean wrapped) {
        registerPlatform();
        http.expect(requestTo("http://platform.local:7780/actuator/health"))
                .andRespond(withSuccess(payload("{\"status\":\"UP\"}", wrapped), MediaType.APPLICATION_JSON));

        Map<String, Object> row = serviceRow("platform");

        assertThat(row).containsEntry("status", "UP").containsEntry("healthStatus", "UP")
                .containsEntry("healthyInstanceCount", 1);
        http.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"data\":{\"status\":\"DOWN\"}}", "{\"code\":500,\"data\":null}", "{}"})
    void unsuccessfulOrMissingHealthIsNeverReportedUp(String body) {
        registerPlatform();
        http.expect(requestTo("http://platform.local:7780/actuator/health"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Map<String, Object> row = serviceRow("platform");

        assertThat(row).containsEntry("status", "DOWN").containsEntry("healthyInstanceCount", 0);
        assertThat(row.get("healthStatus")).isIn("DOWN", "UNKNOWN");
        http.verify();
    }

    @Test
    void unregisteredEurekaUsesConfiguredPortAndContextPathWithoutExposingCredentials() {
        eurekaConfig.getServiceUrl().put("defaultZone", "http://monitor:private@registry.local:18761/registry/eureka/");
        http.expect(requestTo("http://registry.local:18761/registry/actuator/health"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> row = serviceRow("eureka");

        assertThat(row).containsEntry("status", "UP").containsEntry("instanceCount", 1)
                .containsEntry("port", 18761)
                .containsEntry("healthUrl", "http://registry.local:18761/registry/actuator/health");
        assertThat(row.toString()).doesNotContain("monitor:", "private");
        http.verify();
    }

    @Test
    void eurekaFallbackReportsPartialOutageAcrossConfiguredServers() {
        eurekaConfig.getServiceUrl().put("defaultZone", "http://first.local:8761/eureka/, http://second.local:8762/eureka/");
        http.expect(requestTo("http://first.local:8761/actuator/health"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));
        http.expect(requestTo("http://second.local:8762/actuator/health"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        Map<String, Object> row = serviceRow("eureka");

        assertThat(row).containsEntry("status", "DEGRADED").containsEntry("instanceCount", 2)
                .containsEntry("healthyInstanceCount", 1);
        http.verify();
    }

    @Test
    void explicitEurekaHealthAddressOverridesDiscoveryAndRegistrationUrls() {
        when(discoveryClient.getInstances("equipment-eureka-server")).thenReturn(Collections.singletonList(
                new DefaultServiceInstance("eureka", "equipment-eureka-server", "registry.local", 8761, false)));
        eurekaConfig.getServiceUrl().put("defaultZone", "http://unused.local:8761/eureka/");
        service = new PlatformMonitorService(discoveryClient, new ObjectMapper(), eurekaConfig,
                "http://management.local:9876/healthz", restTemplate);
        http.expect(requestTo("http://management.local:9876/healthz"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        assertThat(serviceRow("eureka")).containsEntry("status", "UP")
                .containsEntry("healthUrl", "http://management.local:9876/healthz");
        http.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectedServiceMetricsRetainValuesAndTagsForBothResponseFormats(boolean wrapped) {
        registerPlatform();
        expectMachineMetricsAt("http://platform.local:7780", wrapped);

        Map<String, Object> machine = ReflectionTestUtils.invokeMethod(service, "collectActuatorMachine", "platform");

        assertThat(machine).containsEntry("heapMemoryUsedMb", 64D).containsEntry("heapMemoryMaxMb", 128D)
                .containsEntry("heapMemoryUsage", 50D).containsEntry("jvmMemoryUsedMb", 80D)
                .containsEntry("diskTotalGb", 10D).containsEntry("processCpuUsage", 25D)
                .containsEntry("systemCpuUsage", 50D).containsEntry("uptimeMs", 120000L);
        http.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void customHealthOverridePreservesDiscoveredOrConfiguredMetricsBase(boolean registered) {
        if (registered) {
            when(discoveryClient.getInstances("equipment-eureka-server")).thenReturn(Collections.singletonList(
                    new DefaultServiceInstance("eureka", "equipment-eureka-server", "registry.local", 18761, false)));
        }
        eurekaConfig.getServiceUrl().put("defaultZone", "http://registry.local:18761/registry/eureka/");
        service = new PlatformMonitorService(discoveryClient, new ObjectMapper(), eurekaConfig,
                "http://management.local:9876/healthz", restTemplate);
        http.expect(requestTo("http://management.local:9876/healthz"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));
        expectMachineMetricsAt(registered ? "http://registry.local:18761" : "http://registry.local:18761/registry", false);

        assertThat(serviceRow("eureka")).containsEntry("status", "UP")
                .containsEntry("healthUrl", "http://management.local:9876/healthz");
        Map<String, Object> machine = ReflectionTestUtils.invokeMethod(service, "collectActuatorMachine", "eureka");
        assertThat(machine).containsEntry("hostName", "registry.local:18761")
                .containsEntry("heapMemoryUsedMb", 64D).containsEntry("heapMemoryUsage", 50D);
        http.verify();
    }

    private void registerPlatform() {
        when(discoveryClient.getInstances("equipment-platform-service")).thenReturn(Collections.singletonList(
                new DefaultServiceInstance("platform", "equipment-platform-service", "platform.local", 7780, false)));
    }

    private Map<String, Object> serviceRow(String code) {
        // Exercise the complete discovery/HTTP path without consulting a developer machine's Docker socket.
        List<Map<String, Object>> rows = ReflectionTestUtils.invokeMethod(service, "collectDiscoveryServices");
        return rows.stream().filter(row -> code.equals(row.get("code"))).findFirst().orElseThrow(() -> new AssertionError("Missing monitor row: " + code));
    }

    private void expectMachineMetricsAt(String baseUrl, boolean wrapped) {
        expectMetric(baseUrl, "jvm.memory.used?tag=area:heap", 64 * 1024 * 1024, wrapped);
        expectMetric(baseUrl, "jvm.memory.max?tag=area:heap", 128 * 1024 * 1024, wrapped);
        expectMetric(baseUrl, "jvm.memory.used?tag=area:nonheap", 16 * 1024 * 1024, wrapped);
        expectMetric(baseUrl, "jvm.memory.max?tag=area:nonheap", 32 * 1024 * 1024, wrapped);
        expectMetric(baseUrl, "disk.total", 10L * 1024 * 1024 * 1024, wrapped);
        expectMetric(baseUrl, "disk.free", 4L * 1024 * 1024 * 1024, wrapped);
        expectMetric(baseUrl, "process.cpu.usage", 0.25, wrapped);
        expectMetric(baseUrl, "system.cpu.usage", 0.5, wrapped);
        expectMetric(baseUrl, "system.cpu.count", 4, wrapped);
        expectMetric(baseUrl, "process.uptime", 120, wrapped);
    }

    private void expectMetric(String baseUrl, String path, double value, boolean wrapped) {
        String body = "{\"measurements\":[{\"statistic\":\"VALUE\",\"value\":" + value + "}]}";
        http.expect(requestTo(baseUrl + "/actuator/metrics/" + path))
                .andRespond(withSuccess(payload(body, wrapped), MediaType.APPLICATION_JSON));
    }

    private String payload(String body, boolean wrapped) {
        return wrapped ? "{\"code\":0,\"msg\":\"操作成功\",\"data\":" + body + "}" : body;
    }
}
