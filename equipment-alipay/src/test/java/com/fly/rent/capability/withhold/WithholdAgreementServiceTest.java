package com.fly.rent.capability.withhold;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CallbackLogService;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.WithholdAgreement;
import com.fly.rent.mapper.InstallmentBillMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.WithholdAgreementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithholdAgreementServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private WithholdAgreementMapper agreementMapper;
    @Mock
    private InstallmentBillMapper billMapper;
    @Mock
    private RentCurrentUserService currentUserService;
    @Mock
    private AlipayWithholdAdapter alipayWithholdAdapter;
    @Mock
    private CallbackLogService callbackLogService;
    @Mock
    private CapabilityConfigService configService;

    @Test
    void signForAdminRejectsOrderWithoutUnpaidBills() {
        Order order = order(31, AlipayRentConstants.STATUS_APPROVED);
        when(configService.booleanValue("withhold.sign-entry.enabled", false)).thenReturn(true);
        when(orderMapper.selectById(31)).thenReturn(order);
        when(billMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service().signForAdmin(31));

        assertEquals("当前订单没有待收账单，不需要发起自动扣款签约", ex.getMessage());
        verifyNoMoreInteractions(alipayWithholdAdapter);
    }

    @Test
    void signForAdminRejectsCreatedOrder() {
        when(configService.booleanValue("withhold.sign-entry.enabled", false)).thenReturn(true);
        when(orderMapper.selectById(31)).thenReturn(order(31, AlipayRentConstants.STATUS_CREATED));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service().signForAdmin(31));

        assertEquals("当前订单状态不支持发起自动扣款签约: CREATED", ex.getMessage());
        verifyNoMoreInteractions(billMapper, alipayWithholdAdapter);
    }

    @Test
    void signForAdminRejectsWhenEntryDisabled() {
        when(orderMapper.selectById(31)).thenReturn(order(31, AlipayRentConstants.STATUS_APPROVED));
        when(configService.booleanValue("withhold.sign-entry.enabled", false)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service().signForAdmin(31));

        assertEquals("自动代扣签约入口暂未启用，请通过分期账单主动支付", ex.getMessage());
        verifyNoMoreInteractions(billMapper, alipayWithholdAdapter);
    }

    @Test
    void agreementCallbackWithoutAgreementNoDoesNotMarkSigned() {
        Map<String, String> payload = new HashMap<>();
        payload.put("external_agreement_no", "EMSWH31");

        service().handleAgreementCallback(payload);

        verifyNoMoreInteractions(agreementMapper);
    }

    @Test
    void agreementCallbackWithAgreementNoMarksSigned() {
        Map<String, String> payload = new HashMap<>();
        payload.put("external_agreement_no", "EMSWH31");
        payload.put("agreement_no", "AGREEMENT-31");
        WithholdAgreement agreement = new WithholdAgreement()
                .setId(1L)
                .setOrderId(31)
                .setExternalAgreementNo("EMSWH31")
                .setStatus(CapabilityConstants.WITHHOLD_SIGNING);
        when(agreementMapper.selectOne(any(QueryWrapper.class))).thenReturn(agreement);

        service().handleAgreementCallback(payload);

        assertEquals("AGREEMENT-31", agreement.getAgreementNo());
        assertEquals(CapabilityConstants.WITHHOLD_SIGNED, agreement.getStatus());
    }

    private WithholdAgreementService service() {
        return new WithholdAgreementService(orderMapper, agreementMapper, billMapper,
                currentUserService, alipayWithholdAdapter, callbackLogService, configService);
    }

    private Order order(Integer orderId, String status) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserUuid("USER-1");
        order.setAlipayStatus(status);
        return order;
    }
}
