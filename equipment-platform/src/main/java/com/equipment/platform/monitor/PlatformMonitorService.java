package com.equipment.platform.monitor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 中台运行监控服务。
 * <p>
 * Docker 部署时优先通过 Docker Engine socket 读取容器状态、CPU、内存、网络和可写层指标；未挂载 socket 时保留注册中心和当前 JVM 指标展示。
 * </p>
 */
@Service
public class PlatformMonitorService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MB = 1024L * 1024L;
    private static final String MODE_DOCKER = "DOCKER";
    private static final String MODE_ACTUATOR = "ACTUATOR";
    private static final String ALL_SERVICE_CODE = "all";
    private static final String DOCKER_SOCKET_ENV = "PLATFORM_MONITOR_DOCKER_SOCKET";
    private static final String DEFAULT_DOCKER_SOCKET = "/var/run/docker.sock";
    private static final List<ServiceMeta> SERVICE_METAS = Arrays.asList(
            new ServiceMeta("eureka", "注册中心", "equipment-eureka-server", "equipment-eureka", true),
            new ServiceMeta("gateway", "网关服务", "equipment-gateway", "equipment-gateway", true),
            new ServiceMeta("platform", "中台服务", "equipment-platform-service", "equipment-platform", true),
            new ServiceMeta("alipay", "支付宝租赁服务", "equipment-alipay-service", "equipment-alipay", true),
            new ServiceMeta("nginx", "Nginx 网关", "", "equipment-nginx", false)
    );

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EurekaClientConfigBean eurekaClientConfig;
    private final String eurekaHealthUrl;

    /**
     * 创建运行监控服务。
     *
     * @param discoveryClient 注册中心客户端
     * @param objectMapper JSON 解析器
     */
    @Autowired
    public PlatformMonitorService(DiscoveryClient discoveryClient, ObjectMapper objectMapper,
                                  EurekaClientConfigBean eurekaClientConfig,
                                  @Value("${platform.monitor.eureka-health-url:}") String eurekaHealthUrl) {
        this(discoveryClient, objectMapper, eurekaClientConfig, eurekaHealthUrl, createRestTemplate());
    }

    PlatformMonitorService(DiscoveryClient discoveryClient, ObjectMapper objectMapper,
                           EurekaClientConfigBean eurekaClientConfig, String eurekaHealthUrl,
                           RestTemplate restTemplate) {
        this.discoveryClient = discoveryClient;
        this.objectMapper = objectMapper;
        this.eurekaClientConfig = eurekaClientConfig;
        this.eurekaHealthUrl = eurekaHealthUrl;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1200);
        requestFactory.setReadTimeout(1800);
        return new RestTemplate(requestFactory);
    }

    /**
     * 构建系统监控总览数据。
     *
     * @param serviceCode 当前选中的 Docker 服务编码
     * @return 前端图表可直接消费的监控快照
     */
    public Map<String, Object> overview(String serviceCode) {
        return overview(serviceCode, null);
    }

    /**
     * 构建系统监控总览数据，并按授权服务过滤服务列表和可选项。
     *
     * @param serviceCode 当前选中的服务编码
     * @param allowedServiceCodes 当前用户可查看的服务编码；为空表示不限制
     * @return 前端图表可直接消费的监控快照
     */
    public Map<String, Object> overview(String serviceCode, Set<String> allowedServiceCodes) {
        Set<String> allowedCodes = normalizeAllowedServiceCodes(allowedServiceCodes);
        String selectedServiceCode = normalizeSelectedServiceCode(serviceCode);
        if (!allowedCodes.isEmpty()) {
            if (ALL_SERVICE_CODE.equals(selectedServiceCode) && allowedCodes.size() == 1) {
                selectedServiceCode = allowedCodes.iterator().next();
            } else if (!ALL_SERVICE_CODE.equals(selectedServiceCode) && !allowedCodes.contains(selectedServiceCode)) {
                selectedServiceCode = allowedCodes.iterator().next();
            }
        }
        DockerSnapshot dockerSnapshot = collectDockerSnapshot();
        boolean dockerMode = dockerSnapshot.available;

        List<Map<String, Object>> services = dockerMode ? collectDockerServices(dockerSnapshot) : collectDiscoveryServices();
        services = filterServices(services, allowedCodes);
        Map<String, Object> machine = dockerMode ? collectDockerMachine(selectedServiceCode, services) : collectActuatorMachine(selectedServiceCode);
        Map<String, Object> summary = buildSummary(machine, services, dockerMode, selectedServiceCode);
        Map<String, Object> charts = buildCharts(machine, services, dockerMode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        result.put("monitorMode", dockerMode ? MODE_DOCKER : MODE_ACTUATOR);
        result.put("dockerAvailable", dockerMode);
        result.put("dockerMessage", dockerSnapshot.message);
        result.put("selectedServiceCode", selectedServiceCode);
        result.put("serviceOptions", serviceOptions(dockerMode, allowedCodes));
        result.put("summary", summary);
        result.put("machine", machine);
        result.put("services", services);
        result.put("charts", charts);
        return result;
    }

    /**
     * 返回系统监控支持的服务编码。
     *
     * @return 服务编码集合
     */
    public Set<String> supportedServiceCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (ServiceMeta meta : SERVICE_METAS) {
            codes.add(meta.code);
        }
        return codes;
    }

    private DockerSnapshot collectDockerSnapshot() {
        DockerSnapshot snapshot = new DockerSnapshot();
        try {
            String body = dockerGet("/containers/json?all=1&size=true");
            List<Map<String, Object>> containers = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {
            });
            for (Map<String, Object> container : containers) {
                String containerName = extractContainerName(container);
                ServiceMeta meta = findMetaByContainerName(containerName);
                if (meta == null) {
                    continue;
                }
                snapshot.containers.put(meta.code, container);
                if (isRunning(container)) {
                    snapshot.stats.put(meta.code, dockerStats(container));
                }
            }
            snapshot.available = true;
            snapshot.message = "Docker Engine 已连接";
        } catch (Exception ex) {
            snapshot.available = false;
            snapshot.message = "Docker Engine 不可用: " + safeMessage(ex.getMessage());
        }
        return snapshot;
    }

    private Map<String, Object> dockerStats(Map<String, Object> container) {
        String id = stringValue(container.get("Id"));
        if (!StringUtils.hasText(id)) {
            return Collections.emptyMap();
        }
        try {
            String body = dockerGet("/containers/" + id + "/stats?stream=false");
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private String dockerGet(String path) throws Exception {
        String socket = System.getenv(DOCKER_SOCKET_ENV);
        if (!StringUtils.hasText(socket)) {
            socket = DEFAULT_DOCKER_SOCKET;
        }
        File socketFile = new File(socket);
        if (!socketFile.exists()) {
            throw new IllegalStateException("Docker socket 未挂载: " + socket);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                "curl",
                "--silent",
                "--show-error",
                "--max-time",
                "2",
                "--unix-socket",
                socket,
                "http://docker" + path
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        boolean finished = process.waitFor(2500, TimeUnit.MILLISECONDS);
        String output = readProcessOutput(process.getInputStream());
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Docker API 请求超时");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(output);
        }
        return output;
    }

    private String readProcessOutput(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> collectDockerServices(DockerSnapshot snapshot) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceMeta meta : SERVICE_METAS) {
            rows.add(toDockerServiceRow(meta, snapshot.containers.get(meta.code), snapshot.stats.get(meta.code)));
        }
        return rows;
    }

    private Map<String, Object> toDockerServiceRow(ServiceMeta meta, Map<String, Object> container, Map<String, Object> stats) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", meta.code);
        row.put("name", meta.name);
        row.put("applicationName", meta.applicationName);
        row.put("containerName", meta.containerName);
        row.put("instanceCount", container == null ? 0 : 1);

        if (container == null) {
            row.put("status", "DOWN");
            row.put("healthStatus", "NO_CONTAINER");
            row.put("healthyInstanceCount", 0);
            row.put("responseTimeMs", 0);
            row.put("host", meta.containerName);
            row.put("port", "-");
            row.put("message", "未找到 Docker 容器");
            row.put("cpuUsage", 0D);
            row.put("memoryUsage", 0D);
            row.put("memoryUsedMb", 0D);
            row.put("memoryLimitMb", 0D);
            row.put("diskUsedGb", 0D);
            return row;
        }

        String state = stringValue(container.get("State"));
        String statusText = stringValue(container.get("Status"));
        String serviceStatus = dockerServiceStatus(state, statusText);
        DockerMetric metric = parseDockerMetric(stats, container);
        row.put("status", serviceStatus);
        row.put("healthStatus", dockerHealthStatus(state, statusText));
        row.put("healthyInstanceCount", "UP".equals(serviceStatus) ? 1 : 0);
        row.put("responseTimeMs", 0);
        row.put("host", meta.containerName);
        row.put("port", "-");
        row.put("image", container.get("Image"));
        row.put("state", state);
        row.put("containerId", shortContainerId(stringValue(container.get("Id"))));
        row.put("message", StringUtils.hasText(statusText) ? statusText : state);
        row.put("cpuUsage", metric.cpuUsage);
        row.put("memoryUsage", metric.memoryUsage);
        row.put("memoryUsedMb", metric.memoryUsedMb);
        row.put("memoryLimitMb", metric.memoryLimitMb);
        row.put("networkRxMb", metric.networkRxMb);
        row.put("networkTxMb", metric.networkTxMb);
        row.put("blockReadMb", metric.blockReadMb);
        row.put("blockWriteMb", metric.blockWriteMb);
        row.put("diskUsedGb", metric.diskUsedGb);
        row.put("diskTotalGb", metric.diskTotalGb);
        row.put("uptimeText", statusText);
        return row;
    }

    private DockerMetric parseDockerMetric(Map<String, Object> stats, Map<String, Object> container) {
        DockerMetric metric = new DockerMetric();
        metric.diskUsedGb = toGb(longValue(container.get("SizeRw")));
        metric.diskTotalGb = toGb(longValue(container.get("SizeRootFs")));
        if (stats == null || stats.isEmpty()) {
            return metric;
        }

        Map<String, Object> cpuStats = mapValue(stats.get("cpu_stats"));
        Map<String, Object> preCpuStats = mapValue(stats.get("precpu_stats"));
        Map<String, Object> cpuUsage = mapValue(cpuStats.get("cpu_usage"));
        Map<String, Object> preCpuUsage = mapValue(preCpuStats.get("cpu_usage"));
        long cpuDelta = longValue(cpuUsage.get("total_usage")) - longValue(preCpuUsage.get("total_usage"));
        long systemDelta = longValue(cpuStats.get("system_cpu_usage")) - longValue(preCpuStats.get("system_cpu_usage"));
        double onlineCpus = number(cpuStats.get("online_cpus"));
        if (onlineCpus <= 0D) {
            onlineCpus = safeArray(cpuUsage.get("percpu_usage")).size();
        }
        if (cpuDelta > 0 && systemDelta > 0 && onlineCpus > 0) {
            metric.cpuUsage = roundOne(Math.min(100D, cpuDelta / (double) systemDelta * onlineCpus * 100D));
        }

        Map<String, Object> memoryStats = mapValue(stats.get("memory_stats"));
        long memoryUsage = longValue(memoryStats.get("usage"));
        long memoryLimit = longValue(memoryStats.get("limit"));
        Map<String, Object> memoryDetail = mapValue(memoryStats.get("stats"));
        long cache = longValue(memoryDetail.get("cache"));
        long actualMemoryUsage = Math.max(0L, memoryUsage - cache);
        metric.memoryUsedMb = toMb(actualMemoryUsage);
        metric.memoryLimitMb = toMb(memoryLimit);
        metric.memoryUsage = percent(actualMemoryUsage, memoryLimit);

        Map<String, Object> networks = mapValue(stats.get("networks"));
        for (Object network : networks.values()) {
            Map<String, Object> row = mapValue(network);
            metric.networkRxMb += toMb(longValue(row.get("rx_bytes")));
            metric.networkTxMb += toMb(longValue(row.get("tx_bytes")));
        }
        metric.networkRxMb = roundOne(metric.networkRxMb);
        metric.networkTxMb = roundOne(metric.networkTxMb);

        Map<String, Object> blockStats = mapValue(stats.get("blkio_stats"));
        for (Object item : safeArray(blockStats.get("io_service_bytes_recursive"))) {
            Map<String, Object> row = mapValue(item);
            String op = stringValue(row.get("op"));
            if ("Read".equalsIgnoreCase(op)) {
                metric.blockReadMb += toMb(longValue(row.get("value")));
            } else if ("Write".equalsIgnoreCase(op)) {
                metric.blockWriteMb += toMb(longValue(row.get("value")));
            }
        }
        metric.blockReadMb = roundOne(metric.blockReadMb);
        metric.blockWriteMb = roundOne(metric.blockWriteMb);
        return metric;
    }

    private Map<String, Object> collectDockerMachine(String selectedServiceCode, List<Map<String, Object>> services) {
        if (!ALL_SERVICE_CODE.equals(selectedServiceCode)) {
            for (Map<String, Object> service : services) {
                if (selectedServiceCode.equals(service.get("code"))) {
                    return dockerMachineFromService(service);
                }
            }
        }
        return aggregateDockerMachine(services);
    }

    private Map<String, Object> dockerMachineFromService(Map<String, Object> service) {
        Map<String, Object> machine = baseDockerMachine(
                String.valueOf(service.get("containerName")),
                String.valueOf(service.get("name")),
                String.valueOf(service.get("uptimeText"))
        );
        machine.put("systemCpuUsage", number(service.get("cpuUsage")));
        machine.put("processCpuUsage", number(service.get("cpuUsage")));
        machine.put("physicalMemoryTotalMb", number(service.get("memoryLimitMb")));
        machine.put("physicalMemoryUsedMb", number(service.get("memoryUsedMb")));
        machine.put("physicalMemoryFreeMb", Math.max(0D, number(service.get("memoryLimitMb")) - number(service.get("memoryUsedMb"))));
        machine.put("physicalMemoryUsage", number(service.get("memoryUsage")));
        machine.put("diskTotalGb", number(service.get("diskTotalGb")));
        machine.put("diskUsedGb", number(service.get("diskUsedGb")));
        machine.put("diskFreeGb", Math.max(0D, number(service.get("diskTotalGb")) - number(service.get("diskUsedGb"))));
        machine.put("diskUsage", percent(number(service.get("diskUsedGb")), number(service.get("diskTotalGb"))));
        machine.put("networkRxMb", number(service.get("networkRxMb")));
        machine.put("networkTxMb", number(service.get("networkTxMb")));
        machine.put("blockReadMb", number(service.get("blockReadMb")));
        machine.put("blockWriteMb", number(service.get("blockWriteMb")));
        return machine;
    }

    private Map<String, Object> aggregateDockerMachine(List<Map<String, Object>> services) {
        double cpuUsage = 0D;
        double memoryUsed = 0D;
        double memoryLimit = 0D;
        double diskUsed = 0D;
        double diskTotal = 0D;
        double rx = 0D;
        double tx = 0D;
        double read = 0D;
        double write = 0D;
        for (Map<String, Object> service : services) {
            cpuUsage += number(service.get("cpuUsage"));
            memoryUsed += number(service.get("memoryUsedMb"));
            memoryLimit += number(service.get("memoryLimitMb"));
            diskUsed += number(service.get("diskUsedGb"));
            diskTotal += number(service.get("diskTotalGb"));
            rx += number(service.get("networkRxMb"));
            tx += number(service.get("networkTxMb"));
            read += number(service.get("blockReadMb"));
            write += number(service.get("blockWriteMb"));
        }
        Map<String, Object> machine = baseDockerMachine("Docker Compose", "全部容器", "-");
        machine.put("systemCpuUsage", roundOne(Math.min(100D, cpuUsage)));
        machine.put("processCpuUsage", roundOne(Math.min(100D, cpuUsage)));
        machine.put("physicalMemoryTotalMb", roundOne(memoryLimit));
        machine.put("physicalMemoryUsedMb", roundOne(memoryUsed));
        machine.put("physicalMemoryFreeMb", roundOne(Math.max(0D, memoryLimit - memoryUsed)));
        machine.put("physicalMemoryUsage", percent(memoryUsed, memoryLimit));
        machine.put("diskTotalGb", roundOne(diskTotal));
        machine.put("diskUsedGb", roundOne(diskUsed));
        machine.put("diskFreeGb", roundOne(Math.max(0D, diskTotal - diskUsed)));
        machine.put("diskUsage", percent(diskUsed, diskTotal));
        machine.put("networkRxMb", roundOne(rx));
        machine.put("networkTxMb", roundOne(tx));
        machine.put("blockReadMb", roundOne(read));
        machine.put("blockWriteMb", roundOne(write));
        return machine;
    }

    private Map<String, Object> baseDockerMachine(String hostName, String osName, String uptimeText) {
        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("hostName", hostName);
        machine.put("osName", osName);
        machine.put("osArch", "Docker");
        machine.put("availableProcessors", 0);
        machine.put("uptimeMs", 0);
        machine.put("uptimeText", uptimeText);
        machine.put("heapMemoryMaxMb", 0D);
        machine.put("heapMemoryUsedMb", 0D);
        machine.put("heapMemoryUsage", 0D);
        machine.put("jvmMemoryUsedMb", 0D);
        machine.put("jvmMemoryFreeMb", 0D);
        return machine;
    }

    private List<Map<String, Object>> collectDiscoveryServices() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceMeta meta : SERVICE_METAS) {
            if (meta.discoveryEnabled) {
                rows.add(probeService(meta));
            }
        }
        return rows;
    }

    private Map<String, Object> probeService(ServiceMeta meta) {
        List<ServiceInstance> instances = monitorInstances(meta);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", meta.code);
        row.put("name", meta.name);
        row.put("applicationName", meta.applicationName);
        row.put("containerName", meta.containerName);
        row.put("instanceCount", instances.size());

        if (instances.isEmpty()) {
            row.put("status", "DOWN");
            row.put("healthStatus", "NO_INSTANCE");
            row.put("healthyInstanceCount", 0);
            row.put("responseTimeMs", 0);
            row.put("host", "-");
            row.put("port", "-");
            row.put("healthUrl", "");
            row.put("message", "注册中心暂无实例");
            return row;
        }

        int healthyCount = 0;
        long bestCost = Long.MAX_VALUE;
        String healthStatus = "UNKNOWN";
        String message = "等待健康检查";
        String host = "";
        int port = 0;
        String healthUrl = "";

        for (ServiceInstance instance : instances) {
            String url = healthUrl(instance);
            long start = System.currentTimeMillis();
            try {
                Map response = restTemplate.getForObject(url, Map.class);
                long cost = Math.max(1L, System.currentTimeMillis() - start);
                String status = stringValue(actuatorPayload(response).get("status"));
                if ("UP".equalsIgnoreCase(status)) {
                    healthyCount++;
                }
                if (cost < bestCost) {
                    bestCost = cost;
                    healthStatus = normalizeStatus(status);
                    message = "健康端点返回 " + healthStatus;
                    host = instance.getHost();
                    port = instance.getPort();
                    healthUrl = url;
                }
            } catch (Exception ex) {
                long cost = Math.max(1L, System.currentTimeMillis() - start);
                if (cost < bestCost) {
                    bestCost = cost;
                    healthStatus = "DOWN";
                    message = ex.getClass().getSimpleName() + ": " + safeMessage(ex.getMessage());
                    host = instance.getHost();
                    port = instance.getPort();
                    healthUrl = url;
                }
            }
        }

        row.put("status", serviceStatus(healthyCount, instances.size()));
        row.put("healthStatus", healthStatus);
        row.put("healthyInstanceCount", healthyCount);
        row.put("responseTimeMs", bestCost == Long.MAX_VALUE ? 0 : bestCost);
        row.put("host", StringUtils.hasText(host) ? host : instances.get(0).getHost());
        row.put("port", port > 0 ? port : instances.get(0).getPort());
        row.put("healthUrl", StringUtils.hasText(healthUrl) ? healthUrl : healthUrl(instances.get(0)));
        row.put("message", message);
        return row;
    }

    private List<ServiceInstance> safeInstances(String applicationName) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(applicationName);
            return instances == null ? Collections.emptyList() : instances;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<ServiceInstance> monitorInstances(ServiceMeta meta) {
        return monitorInstances(meta, true);
    }

    private List<ServiceInstance> monitorInstances(ServiceMeta meta, boolean useHealthOverride) {
        boolean explicitHealthOverride = useHealthOverride && StringUtils.hasText(eurekaHealthUrl);
        List<ServiceInstance> instances = safeInstances(meta.applicationName);
        if (!"eureka".equals(meta.code) || (!instances.isEmpty() && !explicitHealthOverride)) {
            return instances;
        }
        // 独立 Eureka 默认不向自身注册；从客户端实际配置探测服务器，而不是要求它自注册。
        Set<String> configuredUrls = new LinkedHashSet<>();
        if (explicitHealthOverride) {
            configuredUrls.add(eurekaHealthUrl.trim());
        } else {
            for (String urls : eurekaClientConfig.getServiceUrl().values()) {
                if (StringUtils.hasText(urls)) {
                    for (String url : urls.split(",")) {
                        if (StringUtils.hasText(url)) {
                            configuredUrls.add(url.trim());
                        }
                    }
                }
            }
        }
        List<ServiceInstance> configuredInstances = new ArrayList<>();
        for (String url : configuredUrls) {
            try {
                URI uri = URI.create(url);
                boolean secure = "https".equalsIgnoreCase(uri.getScheme());
                if ((!secure && !"http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                    continue;
                }
                String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
                String basePath = explicitHealthOverride
                        ? path.replaceFirst("/actuator/health$", "") : path.replaceFirst("/eureka$", "");
                // 不将注册 URL 的认证信息、查询参数带入可见监控结果。
                URI baseUri = UriComponentsBuilder.fromUri(uri).userInfo(null).replaceQuery(null)
                        .fragment(null).replacePath(basePath).build().toUri();
                int port = uri.getPort() >= 0 ? uri.getPort() : secure ? 443 : 80;
                DefaultServiceInstance instance = new DefaultServiceInstance(meta.code + "-" + configuredInstances.size(),
                        meta.applicationName, uri.getHost(), port, secure) {
                    @Override
                    public URI getUri() {
                        return baseUri;
                    }
                };
                if (explicitHealthOverride) {
                    instance.getMetadata().put("monitorHealthUrl", UriComponentsBuilder.fromUri(baseUri)
                            .replacePath(path).toUriString());
                }
                configuredInstances.add(instance);
            } catch (IllegalArgumentException ignored) {
                // 无效配置不生成健康实例，界面仍显示未发现实例，避免误报正常。
            }
        }
        return configuredInstances;
    }

    private String healthUrl(ServiceInstance instance) {
        String configuredHealthUrl = instance.getMetadata().get("monitorHealthUrl");
        if (StringUtils.hasText(configuredHealthUrl)) {
            return configuredHealthUrl;
        }
        return UriComponentsBuilder.fromUri(instance.getUri()).path("/actuator/health").toUriString();
    }

    private Map<String, Object> actuatorPayload(Object response) {
        Map<String, Object> body = mapValue(response);
        return body.get("data") instanceof Map ? mapValue(body.get("data")) : body;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private String serviceStatus(int healthyCount, int total) {
        if (total <= 0 || healthyCount <= 0) {
            return "DOWN";
        }
        return healthyCount == total ? "UP" : "DEGRADED";
    }

    private String dockerServiceStatus(String state, String statusText) {
        String normalizedState = state == null ? "" : state.toLowerCase(Locale.ROOT);
        String normalizedText = statusText == null ? "" : statusText.toLowerCase(Locale.ROOT);
        if (!"running".equals(normalizedState) || normalizedText.contains("unhealthy")) {
            return "DOWN";
        }
        if (normalizedText.contains("starting") || normalizedText.contains("health: starting")) {
            return "DEGRADED";
        }
        return "UP";
    }

    private String dockerHealthStatus(String state, String statusText) {
        String normalizedText = statusText == null ? "" : statusText.toLowerCase(Locale.ROOT);
        if (normalizedText.contains("unhealthy")) {
            return "UNHEALTHY";
        }
        if (normalizedText.contains("healthy")) {
            return "HEALTHY";
        }
        if (normalizedText.contains("starting")) {
            return "STARTING";
        }
        return StringUtils.hasText(state) ? state.toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "探测失败";
        }
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    private Map<String, Object> collectActuatorMachine(String selectedServiceCode) {
        if (ALL_SERVICE_CODE.equals(selectedServiceCode)) {
            return collectMachine();
        }
        ServiceMeta meta = findMetaByCode(selectedServiceCode);
        if (meta == null || !meta.discoveryEnabled) {
            return collectMachine();
        }
        // 自定义健康地址只覆盖健康探测；任意 /healthz 路径不能作为 metrics 的基础路径。
        // 指标仍使用服务发现或注册中心配置的原始服务地址及上下文。
        List<ServiceInstance> instances = monitorInstances(meta, false);
        if (instances.isEmpty()) {
            Map<String, Object> machine = baseActuatorMachine(meta, null, "注册中心暂无实例");
            return machine;
        }
        ServiceInstance instance = instances.get(0);
        try {
            Map<String, Object> machine = baseActuatorMachine(meta, instance, "Actuator metrics");
            double heapUsed = actuatorMetric(instance, "jvm.memory.used", "area", "heap");
            double heapMax = actuatorMetric(instance, "jvm.memory.max", "area", "heap");
            double nonHeapUsed = actuatorMetric(instance, "jvm.memory.used", "area", "nonheap");
            double nonHeapMax = actuatorMetric(instance, "jvm.memory.max", "area", "nonheap");
            double jvmUsed = heapUsed + nonHeapUsed;
            double jvmMax = positive(heapMax) + positive(nonHeapMax);
            double memoryTotal = positive(jvmMax) > 0D ? jvmMax : positive(heapMax);
            double diskTotal = actuatorMetric(instance, "disk.total");
            double diskFree = actuatorMetric(instance, "disk.free");
            double diskUsed = Math.max(0D, diskTotal - diskFree);
            double processCpu = actuatorMetric(instance, "process.cpu.usage");
            double systemCpu = actuatorMetric(instance, "system.cpu.usage");
            double cpuCount = actuatorMetric(instance, "system.cpu.count");
            double uptimeSeconds = actuatorMetric(instance, "process.uptime");

            machine.put("availableProcessors", Math.round(cpuCount));
            machine.put("uptimeMs", Math.round(uptimeSeconds * 1000D));
            machine.put("uptimeText", formatDuration(Math.round(uptimeSeconds * 1000D)));
            machine.put("systemCpuUsage", roundPercent(systemCpu));
            machine.put("processCpuUsage", roundPercent(processCpu));
            machine.put("physicalMemoryTotalMb", toMb(Math.round(memoryTotal)));
            machine.put("physicalMemoryUsedMb", toMb(Math.round(jvmUsed)));
            machine.put("physicalMemoryFreeMb", toMb(Math.round(Math.max(0D, memoryTotal - jvmUsed))));
            machine.put("physicalMemoryUsage", percent(jvmUsed, memoryTotal));
            machine.put("heapMemoryMaxMb", toMb(Math.round(heapMax)));
            machine.put("heapMemoryUsedMb", toMb(Math.round(heapUsed)));
            machine.put("heapMemoryUsage", percent(heapUsed, heapMax));
            machine.put("diskTotalGb", toGb(Math.round(diskTotal)));
            machine.put("diskUsedGb", toGb(Math.round(diskUsed)));
            machine.put("diskFreeGb", toGb(Math.round(diskFree)));
            machine.put("diskUsage", percent(diskUsed, diskTotal));
            machine.put("jvmMemoryUsedMb", toMb(Math.round(jvmUsed)));
            machine.put("jvmMemoryFreeMb", toMb(Math.round(Math.max(0D, memoryTotal - jvmUsed))));
            return machine;
        } catch (Exception ex) {
            return baseActuatorMachine(meta, instance, "Actuator metrics 不可用: " + safeMessage(ex.getMessage()));
        }
    }

    private Map<String, Object> baseActuatorMachine(ServiceMeta meta, ServiceInstance instance, String message) {
        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("hostName", instance == null ? meta.name : instance.getHost() + ":" + instance.getPort());
        machine.put("osName", meta.name);
        machine.put("osArch", meta.applicationName);
        machine.put("availableProcessors", 0);
        machine.put("uptimeMs", 0);
        machine.put("uptimeText", "-");
        machine.put("systemCpuUsage", 0D);
        machine.put("processCpuUsage", 0D);
        machine.put("physicalMemoryTotalMb", 0D);
        machine.put("physicalMemoryUsedMb", 0D);
        machine.put("physicalMemoryFreeMb", 0D);
        machine.put("physicalMemoryUsage", 0D);
        machine.put("heapMemoryMaxMb", 0D);
        machine.put("heapMemoryUsedMb", 0D);
        machine.put("heapMemoryUsage", 0D);
        machine.put("diskTotalGb", 0D);
        machine.put("diskUsedGb", 0D);
        machine.put("diskFreeGb", 0D);
        machine.put("diskUsage", 0D);
        machine.put("jvmMemoryUsedMb", 0D);
        machine.put("jvmMemoryFreeMb", 0D);
        machine.put("metricsMessage", message);
        return machine;
    }

    private double actuatorMetric(ServiceInstance instance, String metricName, String... tags) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(instance.getUri())
                .path("/actuator/metrics/{metricName}");
        if (tags != null) {
            for (int index = 0; index + 1 < tags.length; index += 2) {
                builder.queryParam("tag", tags[index] + ":" + tags[index + 1]);
            }
        }
        Map response = restTemplate.getForObject(builder.buildAndExpand(metricName).toUriString(), Map.class);
        for (Object measurement : safeArray(actuatorPayload(response).get("measurements"))) {
            Map<String, Object> row = mapValue(measurement);
            if (row.containsKey("value")) {
                return number(row.get("value"));
            }
        }
        return 0D;
    }

    private Map<String, Object> collectMachine() {
        Map<String, Object> machine = new LinkedHashMap<>();
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        File[] roots = File.listRoots();

        long totalDisk = 0L;
        long freeDisk = 0L;
        if (roots != null) {
            for (File root : roots) {
                totalDisk += Math.max(0L, root.getTotalSpace());
                freeDisk += Math.max(0L, root.getFreeSpace());
            }
        }

        long totalMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long physicalTotal = totalMemory;
        long physicalFree = freeMemory;
        double systemCpuLoad = normalizeLoad(osBean.getSystemLoadAverage(), runtime.availableProcessors());
        double processCpuLoad = 0D;

        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunBean = (com.sun.management.OperatingSystemMXBean) osBean;
            physicalTotal = sunBean.getTotalPhysicalMemorySize();
            physicalFree = sunBean.getFreePhysicalMemorySize();
            systemCpuLoad = normalizeRatio(sunBean.getSystemCpuLoad());
            processCpuLoad = normalizeRatio(sunBean.getProcessCpuLoad());
        }

        long physicalUsed = Math.max(0L, physicalTotal - physicalFree);
        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax() > 0 ? heapUsage.getMax() : totalMemory;
        long diskUsed = Math.max(0L, totalDisk - freeDisk);

        machine.put("hostName", hostName());
        machine.put("osName", osBean.getName());
        machine.put("osArch", osBean.getArch());
        machine.put("availableProcessors", osBean.getAvailableProcessors());
        machine.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        machine.put("uptimeText", formatDuration(ManagementFactory.getRuntimeMXBean().getUptime()));
        machine.put("systemCpuUsage", roundPercent(systemCpuLoad));
        machine.put("processCpuUsage", roundPercent(processCpuLoad));
        machine.put("physicalMemoryTotalMb", toMb(physicalTotal));
        machine.put("physicalMemoryUsedMb", toMb(physicalUsed));
        machine.put("physicalMemoryFreeMb", toMb(physicalFree));
        machine.put("physicalMemoryUsage", percent(physicalUsed, physicalTotal));
        machine.put("heapMemoryMaxMb", toMb(heapMax));
        machine.put("heapMemoryUsedMb", toMb(heapUsed));
        machine.put("heapMemoryUsage", percent(heapUsed, heapMax));
        machine.put("diskTotalGb", toGb(totalDisk));
        machine.put("diskUsedGb", toGb(diskUsed));
        machine.put("diskFreeGb", toGb(freeDisk));
        machine.put("diskUsage", percent(diskUsed, totalDisk));
        machine.put("jvmMemoryUsedMb", toMb(usedMemory));
        machine.put("jvmMemoryFreeMb", toMb(freeMemory));
        return machine;
    }

    private Map<String, Object> buildSummary(Map<String, Object> machine,
                                             List<Map<String, Object>> services,
                                             boolean dockerMode,
                                             String selectedServiceCode) {
        int total = services.size();
        int up = 0;
        int degraded = 0;
        int down = 0;
        for (Map<String, Object> service : services) {
            String status = String.valueOf(service.get("status"));
            if ("UP".equals(status)) {
                up++;
            } else if ("DEGRADED".equals(status)) {
                degraded++;
            } else {
                down++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("monitorMode", dockerMode ? MODE_DOCKER : MODE_ACTUATOR);
        summary.put("selectedServiceCode", selectedServiceCode);
        summary.put("selectedServiceName", selectedServiceName(selectedServiceCode, dockerMode));
        summary.put("totalServices", total);
        summary.put("healthyServices", up);
        summary.put("degradedServices", degraded);
        summary.put("downServices", down);
        summary.put("availabilityRate", percent(up, total));
        summary.put("systemCpuUsage", machine.get("systemCpuUsage"));
        summary.put("physicalMemoryUsage", machine.get("physicalMemoryUsage"));
        summary.put("heapMemoryUsage", machine.get("heapMemoryUsage"));
        summary.put("diskUsage", machine.get("diskUsage"));
        summary.put("uptimeText", machine.get("uptimeText"));
        return summary;
    }

    private Map<String, Object> buildCharts(Map<String, Object> machine, List<Map<String, Object>> services, boolean dockerMode) {
        List<Map<String, Object>> statusRows = new ArrayList<>();
        statusRows.add(chartRow("正常", countStatus(services, "UP")));
        statusRows.add(chartRow("部分异常", countStatus(services, "DEGRADED")));
        statusRows.add(chartRow("不可用", countStatus(services, "DOWN")));

        List<Map<String, Object>> usageRows = new ArrayList<>();
        usageRows.add(chartRow("CPU", machine.get("systemCpuUsage")));
        usageRows.add(chartRow(dockerMode ? "容器内存" : "物理内存", machine.get("physicalMemoryUsage")));
        if (dockerMode) {
            usageRows.add(chartRow("可写层", machine.get("diskUsage")));
        } else {
            usageRows.add(chartRow("JVM堆", machine.get("heapMemoryUsage")));
            usageRows.add(chartRow("磁盘", machine.get("diskUsage")));
        }

        List<Map<String, Object>> latencyRows = new ArrayList<>();
        for (Map<String, Object> service : services) {
            latencyRows.add(chartRow(String.valueOf(service.get("name")), dockerMode ? service.get("cpuUsage") : service.get("responseTimeMs")));
        }

        List<Map<String, Object>> memoryRows = new ArrayList<>();
        memoryRows.add(chartRow(dockerMode ? "已用容器内存" : "已用物理内存", machine.get("physicalMemoryUsedMb")));
        memoryRows.add(chartRow(dockerMode ? "剩余容器内存" : "空闲物理内存", machine.get("physicalMemoryFreeMb")));
        if (!dockerMode) {
            memoryRows.add(chartRow("JVM已用", machine.get("heapMemoryUsedMb")));
            memoryRows.add(chartRow("JVM剩余", Math.max(0D, number(machine.get("heapMemoryMaxMb")) - number(machine.get("heapMemoryUsedMb")))));
        }

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("serviceStatusRows", statusRows);
        charts.put("resourceUsageRows", usageRows);
        charts.put("serviceLatencyRows", latencyRows);
        charts.put("memoryRows", memoryRows);
        return charts;
    }

    private int countStatus(List<Map<String, Object>> services, String status) {
        int count = 0;
        for (Map<String, Object> service : services) {
            if (status.equals(String.valueOf(service.get("status")))) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Object> chartRow(String name, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("value", number(value));
        return row;
    }

    private List<Map<String, Object>> serviceOptions(boolean dockerMode, Set<String> allowedServiceCodes) {
        Set<String> allowedCodes = normalizeAllowedServiceCodes(allowedServiceCodes);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (allowedCodes.isEmpty()) {
            Map<String, Object> all = new LinkedHashMap<>();
            all.put("code", ALL_SERVICE_CODE);
            all.put("name", dockerMode ? "全部容器" : "全部服务");
            rows.add(all);
        }
        for (ServiceMeta meta : SERVICE_METAS) {
            if ((dockerMode || meta.discoveryEnabled) && (allowedCodes.isEmpty() || allowedCodes.contains(meta.code))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", meta.code);
                row.put("name", meta.name);
                row.put("containerName", meta.containerName);
                rows.add(row);
            }
        }
        return rows;
    }

    private Set<String> normalizeAllowedServiceCodes(Set<String> allowedServiceCodes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (allowedServiceCodes == null) {
            return normalized;
        }
        for (String code : allowedServiceCodes) {
            if (StringUtils.hasText(code)) {
                normalized.add(code.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private List<Map<String, Object>> filterServices(List<Map<String, Object>> services, Set<String> allowedCodes) {
        if (allowedCodes == null || allowedCodes.isEmpty()) {
            return services;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> service : services) {
            if (allowedCodes.contains(String.valueOf(service.get("code")))) {
                rows.add(service);
            }
        }
        return rows;
    }

    private String normalizeSelectedServiceCode(String serviceCode) {
        String normalized = StringUtils.hasText(serviceCode) ? serviceCode.trim().toLowerCase(Locale.ROOT) : ALL_SERVICE_CODE;
        if (ALL_SERVICE_CODE.equals(normalized)) {
            return normalized;
        }
        for (ServiceMeta meta : SERVICE_METAS) {
            if (meta.code.equals(normalized)) {
                return normalized;
            }
        }
        return ALL_SERVICE_CODE;
    }

    private String selectedServiceName(String serviceCode, boolean dockerMode) {
        if (ALL_SERVICE_CODE.equals(serviceCode)) {
            return dockerMode ? "全部容器" : "全部服务";
        }
        for (ServiceMeta meta : SERVICE_METAS) {
            if (meta.code.equals(serviceCode)) {
                return meta.name;
            }
        }
        return dockerMode ? "全部容器" : "全部服务";
    }

    private ServiceMeta findMetaByContainerName(String containerName) {
        for (ServiceMeta meta : SERVICE_METAS) {
            if (meta.containerName.equals(containerName)) {
                return meta;
            }
        }
        return null;
    }

    private ServiceMeta findMetaByCode(String code) {
        for (ServiceMeta meta : SERVICE_METAS) {
            if (meta.code.equals(code)) {
                return meta;
            }
        }
        return null;
    }

    private String extractContainerName(Map<String, Object> container) {
        List<Object> names = safeArray(container.get("Names"));
        if (names.isEmpty()) {
            return "";
        }
        String name = String.valueOf(names.get(0));
        return name.startsWith("/") ? name.substring(1) : name;
    }

    private boolean isRunning(Map<String, Object> container) {
        return "running".equalsIgnoreCase(stringValue(container.get("State")));
    }

    private String shortContainerId(String id) {
        if (!StringUtils.hasText(id)) {
            return "";
        }
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private double normalizeLoad(double loadAverage, int processors) {
        if (loadAverage < 0 || processors <= 0) {
            return 0D;
        }
        return Math.min(1D, loadAverage / processors);
    }

    private double normalizeRatio(double value) {
        if (value < 0D || Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    private double percent(double used, double total) {
        if (total <= 0D) {
            return 0D;
        }
        return roundPercent(used / total);
    }

    private double roundPercent(double ratio) {
        return Math.round(normalizeRatio(ratio) * 1000D) / 10D;
    }

    private double roundOne(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private double positive(double value) {
        return value > 0D ? value : 0D;
    }

    private double toMb(long bytes) {
        return Math.round((bytes / (double) MB) * 10D) / 10D;
    }

    private double toGb(long bytes) {
        return Math.round((bytes / (double) (MB * 1024L)) * 10D) / 10D;
    }

    private double number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Object> safeArray(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return Collections.emptyList();
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) {
            return days + "天 " + hours + "小时";
        }
        if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        }
        return minutes + "分钟";
    }

    private static class ServiceMeta {
        private final String code;
        private final String name;
        private final String applicationName;
        private final String containerName;
        private final boolean discoveryEnabled;

        ServiceMeta(String code, String name, String applicationName, String containerName, boolean discoveryEnabled) {
            this.code = code;
            this.name = name;
            this.applicationName = applicationName;
            this.containerName = containerName;
            this.discoveryEnabled = discoveryEnabled;
        }
    }

    private static class DockerSnapshot {
        private boolean available;
        private String message = "";
        private final Map<String, Map<String, Object>> containers = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
    }

    private static class DockerMetric {
        private double cpuUsage;
        private double memoryUsage;
        private double memoryUsedMb;
        private double memoryLimitMb;
        private double networkRxMb;
        private double networkTxMb;
        private double blockReadMb;
        private double blockWriteMb;
        private double diskUsedGb;
        private double diskTotalGb;
    }
}
