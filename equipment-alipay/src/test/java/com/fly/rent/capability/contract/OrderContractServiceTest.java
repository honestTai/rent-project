package com.fly.rent.capability.contract;

import com.fly.rent.capability.CallbackLogService;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.OrderContract;
import com.fly.rent.mapper.OrderContractMapper;
import com.fly.rent.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderContractServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderContractMapper contractMapper;
    @Mock
    private RentCurrentUserService currentUserService;
    @Mock
    private CapabilityConfigService configService;
    @Mock
    private EsignContractAdapter esignContractAdapter;
    @Mock
    private CallbackLogService callbackLogService;
    @Mock
    private RentExtensionStore rentExtensionStore;

    @Test
    void getContractForAdminReturnsPdfModeWhenEsignDisabled() {
        Order order = order(100);
        when(orderMapper.selectById(100)).thenReturn(order);
        mockEsignRequired(order);
        when(configService.booleanValue("esign.enabled", false)).thenReturn(false);

        Map<String, Object> result = service().getContractForAdmin(100);

        assertFalse((Boolean) result.get("enabled"));
        assertEquals(true, result.get("required"));
        assertEquals("pdf", result.get("mode"));
        assertEquals(null, result.get("contract"));
        verifyNoInteractions(contractMapper, esignContractAdapter);
    }

    @Test
    void requireReceiptSignReadySkipsCheckWhenOrderDoesNotRequireEsign() {
        Order order = order(100);

        assertDoesNotThrow(() -> service().requireReceiptSignReady(order));

        verifyNoInteractions(configService, contractMapper, esignContractAdapter);
    }

    @Test
    void requireReceiptSignReadyRejectsWhenEsignEnabledAndContractIncomplete() {
        Order order = order(100);
        mockEsignRequired(order);
        when(configService.booleanValue("esign.enabled", false)).thenReturn(true);
        when(contractMapper.selectOne(any())).thenReturn(new OrderContract()
                .setOrderId(100)
                .setStatus(CapabilityConstants.CONTRACT_SIGNING));
        when(esignContractAdapter.queryStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service().requireReceiptSignReady(order));

        assertEquals("请先完成电子合同签署", ex.getMessage());
    }

    @Test
    void requireReceiptSignReadyAllowsCompletedEsignContract() {
        Order order = order(100);
        mockEsignRequired(order);
        when(configService.booleanValue("esign.enabled", false)).thenReturn(true);
        when(contractMapper.selectOne(any())).thenReturn(new OrderContract()
                .setOrderId(100)
                .setStatus(CapabilityConstants.CONTRACT_COMPLETED));
        when(esignContractAdapter.queryStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service().requireReceiptSignReady(order));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getContractForAdminReturnsDefaultEsignAlipayMiniappConfig() {
        Order order = order(100);
        when(orderMapper.selectById(100)).thenReturn(order);
        mockEsignRequired(order);
        when(configService.booleanValue("esign.enabled", false)).thenReturn(true);
        when(contractMapper.selectOne(any())).thenReturn(new OrderContract()
                .setId(10L)
                .setOrderId(100)
                .setFlowId("FLOW-1")
                .setSignerId("SIGNER-1")
                .setStatus(CapabilityConstants.CONTRACT_NONE));
        when(configService.value("esign.miniapp.env", "prod")).thenReturn("prod");
        when(configService.value("esign.miniapp.path", "pages/startup/index")).thenReturn("pages/startup/index");
        when(configService.value("esign.miniapp.page", "sign")).thenReturn("sign");
        when(configService.value("esign.miniapp.forward-home", "true")).thenReturn("true");
        when(configService.value("esign.miniapp.plugin-page", "esign")).thenReturn("esign");
        when(configService.value("esign.miniapp.app-id", "2019042964339413")).thenReturn("2019042964339413");
        when(configService.value("esign.miniapp.open-type", "miniProgram")).thenReturn("miniProgram");
        when(configService.value("esign.miniapp.skip-result", "false")).thenReturn("false");
        when(configService.value("esign.miniapp.skip-guide", "false")).thenReturn("false");

        Map<String, Object> result = service().getContractForAdmin(100);
        Map<String, Object> contract = (Map<String, Object>) result.get("contract");
        Map<String, Object> mini = (Map<String, Object>) contract.get("signMiniProgram");
        Map<String, Object> query = (Map<String, Object>) mini.get("query");
        Map<String, Object> extraData = (Map<String, Object>) mini.get("extraData");

        assertEquals("2019042964339413", mini.get("appId"));
        assertEquals("pages/startup/index", mini.get("path"));
        assertEquals("miniProgram", mini.get("openType"));
        assertEquals("sign", query.get("page"));
        assertEquals("FLOW-1", query.get("flowId"));
        assertEquals("SIGNER-1", query.get("signerId"));
        assertEquals("true", query.get("forwardHome"));
        assertEquals("pages/startup/index", extraData.get("page"));
        assertEquals(query, extraData.get("query"));
    }

    private OrderContractService service() {
        return new OrderContractService(orderMapper, contractMapper, currentUserService,
                configService, esignContractAdapter, callbackLogService, rentExtensionStore);
    }

    private Order order(Integer orderId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderNo("ORDER-" + orderId);
        order.setUserUuid("USER-1");
        return order;
    }

    private void mockEsignRequired(Order order) {
        RentOrderExtension extension = new RentOrderExtension();
        extension.setEsignRequired(true);
        when(rentExtensionStore.loadOrderExtension(order.getOrderNo())).thenReturn(extension);
    }
}
