package com.fly.rent.legacy.service.impl;

import com.alipay.api.response.AlipayCommerceRentOrderFulfillmentReceiveResponse;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.order.RentContractAlipaySyncService;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentOrderFulfillmentServiceImplTest {

    @Mock
    private AlipayClientService alipayClientService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RentOrderService rentOrderService;
    @Mock
    private AlipayOrderLifecycleNotifyService lifecycleNotifyService;
    @Mock
    private RentContractAlipaySyncService contractAlipaySyncService;

    @Test
    void receiveSyncsContractAfterMerchantDeliveryReceived() throws Exception {
        Order order = order();
        when(alipayClientService.execute(any())).thenReturn(successResponse());

        service().receive(order, "MERCHANT_DELIVERY_RECEIVED");

        verify(contractAlipaySyncService).syncContractAfterReceive(order.getOrderId(), "履约收货");
    }

    @Test
    void receiveDoesNotSyncContractAfterUserReturnReceived() throws Exception {
        Order order = order();
        order.setAlipayStatus(AlipayRentConstants.STATUS_RETURN_DELIVERED);
        when(alipayClientService.execute(any())).thenReturn(successResponse());

        service().receive(order, "USER_RETURN_RECEIVED");

        verify(contractAlipaySyncService, never()).syncContractAfterReceive(any(), any());
    }

    private RentOrderFulfillmentServiceImpl service() {
        RentOrderFulfillmentServiceImpl service = new RentOrderFulfillmentServiceImpl();
        ReflectionTestUtils.setField(service, "alipayClientService", alipayClientService);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "rentOrderService", rentOrderService);
        ReflectionTestUtils.setField(service, "alipayOrderLifecycleNotifyService", lifecycleNotifyService);
        ReflectionTestUtils.setField(service, "contractAlipaySyncService", contractAlipaySyncService);
        return service;
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId(7);
        order.setOrderNo("ORD-7");
        order.setRentOrderId("RENT-7");
        order.setUserUuid("USER-7");
        order.setAlipayStatus(AlipayRentConstants.STATUS_DELIVERED);
        return order;
    }

    private AlipayCommerceRentOrderFulfillmentReceiveResponse successResponse() {
        AlipayCommerceRentOrderFulfillmentReceiveResponse response =
                new AlipayCommerceRentOrderFulfillmentReceiveResponse();
        response.setCode("10000");
        response.setMsg("Success");
        return response;
    }
}
