package com.fly.rent.miniapp.callback.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.aftersale.service.RentDepositDeductService;
import com.fly.rent.capability.billing.InstallmentPaymentSyncService;
import com.fly.rent.capability.buyout.RentBuyoutPaymentService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.web.support.WebOperLogHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayRentPayNotifyHandlerTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private WebOperLogHelper operLogHelper;
    @Mock
    private AlipayOrderLifecycleNotifyService lifecycleNotifyService;
    @Mock
    private RentDepositDeductService deductService;
    @Mock
    private InstallmentPaymentSyncService paymentSyncService;
    @Mock
    private RentBuyoutPaymentService buyoutPaymentService;

    @Test
    void paidCallbackWithoutPayItemsMarksNextUnpaidPeriodPaid() {
        Order order = new Order();
        order.setOrderId(31);
        order.setOrderNo("178252251916130");
        order.setAlipayStatus(AlipayRentConstants.STATUS_APPROVED);

        when(orderMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(order));

        String result = handler().handle(callbackWithoutPayItems());

        assertEquals("success", result);
        verify(paymentSyncService).markNextUnpaidPeriodPaid(eq(order), eq("TRADE-1"), any(Date.class));
        verify(orderMapper).updateById(order);
        assertEquals(AlipayRentConstants.STATUS_PAID, order.getAlipayStatus());
        assertEquals("TRADE-1", order.getPaymentTradeNo());
    }

    private AlipayRentPayNotifyHandler handler() {
        return new AlipayRentPayNotifyHandler(orderMapper, operLogHelper, lifecycleNotifyService,
                deductService, paymentSyncService, buyoutPaymentService);
    }

    private Map<String, String> callbackWithoutPayItems() {
        Map<String, String> params = new HashMap<>();
        params.put("biz_content", "{"
                + "\"pay_status\":\"PAID\","
                + "\"out_order_id\":\"178252251916130\","
                + "\"out_trade_no\":\"OUT-TRADE-1\","
                + "\"trade_no\":\"TRADE-1\","
                + "\"pay_amount\":\"120.00\""
                + "}");
        return params;
    }
}
