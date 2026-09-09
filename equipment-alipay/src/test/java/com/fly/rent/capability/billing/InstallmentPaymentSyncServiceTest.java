package com.fly.rent.capability.billing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.InstallmentBill;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentInstallmentInfoEntity;
import com.fly.rent.mapper.InstallmentBillMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentPaymentSyncServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private InstallmentBillMapper billMapper;
    @Mock
    private RentInstallmentInfoEntityMapper installmentMapper;

    @Test
    void markNextUnpaidPeriodPaidUpdatesLegacyPlanAndBill() {
        Order order = order();
        RentInstallmentInfoEntity installment = new RentInstallmentInfoEntity()
                .setOrderId(31)
                .setInstallmentNo(1L)
                .setInstallmentPrice("120.00")
                .setStatus(AlipayRentConstants.INSTALLMENT_STATUS_UNPAID);
        InstallmentBill bill = new InstallmentBill()
                .setId(31001L)
                .setOrderId(31)
                .setPeriodNo(1)
                .setPeriodTotal(1)
                .setAmount(12_000)
                .setPaidAmount(0)
                .setStatus(CapabilityConstants.BILL_WAIT_PAY);

        when(billMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(installmentMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(installment))
                .thenReturn(Collections.singletonList(installment))
                .thenReturn(Collections.singletonList(installment));
        when(billMapper.selectOne(any(QueryWrapper.class))).thenReturn(bill);

        boolean synced = service().markNextUnpaidPeriodPaid(order, "TRADE-1", new Date());

        assertTrue(synced);
        verify(installmentMapper).updateById(installment);
        ArgumentCaptor<InstallmentBill> billCaptor = ArgumentCaptor.forClass(InstallmentBill.class);
        verify(billMapper).updateById(billCaptor.capture());
        InstallmentBill updatedBill = billCaptor.getValue();
        assertEquals(CapabilityConstants.BILL_PAID, updatedBill.getStatus());
        assertEquals(12_000, updatedBill.getPaidAmount());
        assertEquals("TRADE-1", updatedBill.getPaymentTradeNo());
        verify(orderMapper).updateById(order);
    }

    @Test
    void markNextUnpaidPeriodPaidIsIdempotentForAlreadySyncedTradeNo() {
        when(billMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        boolean synced = service().markNextUnpaidPeriodPaid(order(), "TRADE-1", new Date());

        assertTrue(synced);
        verifyNoInteractions(orderMapper, installmentMapper);
    }

    private InstallmentPaymentSyncService service() {
        return new InstallmentPaymentSyncService(orderMapper, billMapper, installmentMapper);
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId(31);
        order.setOrderTotal(12_000);
        order.setOrderTotalRentPeriods(1);
        return order;
    }
}
