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
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstallmentPaymentSyncService {

    private final OrderMapper orderMapper;
    private final InstallmentBillMapper billMapper;
    private final RentInstallmentInfoEntityMapper legacyInstallmentMapper;

    @Transactional(rollbackFor = Exception.class)
    public void markBillPaid(InstallmentBill bill, String tradeNo, Date paidAt) {
        if (bill == null || bill.getOrderId() == null || bill.getPeriodNo() == null) {
            return;
        }
        Order order = orderMapper.selectById(bill.getOrderId());
        if (order == null) {
            updateBillOnly(bill, tradeNo, paidAt);
            return;
        }
        markPeriodPaid(order, bill.getPeriodNo(), tradeNo, paidAt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markPeriodPaid(Order order, Integer periodNo, String tradeNo, Date paidAt) {
        if (order == null || order.getOrderId() == null || periodNo == null || periodNo <= 0) {
            return;
        }
        Date payTime = paidAt == null ? new Date() : paidAt;
        syncLegacyInstallment(order.getOrderId(), periodNo);
        syncBill(order, periodNo, tradeNo, payTime);
        refreshOrderPeriod(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markNextUnpaidPeriodPaid(Order order, String tradeNo, Date paidAt) {
        if (order == null || order.getOrderId() == null) {
            return false;
        }
        if (hasPaidBillWithTradeNo(tradeNo)) {
            return true;
        }
        RentInstallmentInfoEntity nextInstallment = findNextUnpaidInstallment(order.getOrderId());
        if (nextInstallment != null && nextInstallment.getInstallmentNo() != null) {
            markPeriodPaid(order, nextInstallment.getInstallmentNo().intValue(), tradeNo, paidAt);
            return true;
        }
        InstallmentBill nextBill = findNextUnpaidBill(order.getOrderId());
        if (nextBill != null) {
            markBillPaid(nextBill, tradeNo, paidAt);
            return true;
        }
        markPeriodPaid(order, 1, tradeNo, paidAt);
        return true;
    }

    private void syncLegacyInstallment(Integer orderId, Integer periodNo) {
        List<RentInstallmentInfoEntity> installments = legacyInstallmentMapper.selectList(
                new QueryWrapper<RentInstallmentInfoEntity>()
                        .eq("order_id", orderId)
                        .eq("period_no", periodNo));
        if (installments == null) {
            return;
        }
        for (RentInstallmentInfoEntity installment : installments) {
            if (installment.getStatus() == null
                    || installment.getStatus() == AlipayRentConstants.INSTALLMENT_STATUS_UNPAID) {
                installment.setStatus(AlipayRentConstants.INSTALLMENT_STATUS_PAID);
                legacyInstallmentMapper.updateById(installment);
            }
        }
    }

    private void syncBill(Order order, Integer periodNo, String tradeNo, Date paidAt) {
        InstallmentBill bill = billMapper.selectOne(new QueryWrapper<InstallmentBill>()
                .eq("order_id", order.getOrderId())
                .eq("period_no", periodNo)
                .last("LIMIT 1"));
        if (bill == null) {
            bill = createBillFromPlan(order, periodNo, paidAt);
        }
        updateBillOnly(bill, tradeNo, paidAt);
    }

    private boolean hasPaidBillWithTradeNo(String tradeNo) {
        if (!StringUtils.hasText(tradeNo)) {
            return false;
        }
        Long count = billMapper.selectCount(new QueryWrapper<InstallmentBill>()
                .eq("payment_trade_no", tradeNo)
                .eq("status", CapabilityConstants.BILL_PAID));
        return count != null && count > 0;
    }

    private RentInstallmentInfoEntity findNextUnpaidInstallment(Integer orderId) {
        List<RentInstallmentInfoEntity> installments = legacyInstallmentMapper.selectList(
                new QueryWrapper<RentInstallmentInfoEntity>()
                        .eq("order_id", orderId)
                        .orderByAsc("period_no"));
        if (installments == null) {
            return null;
        }
        for (RentInstallmentInfoEntity installment : installments) {
            if (installment.getStatus() == null
                    || installment.getStatus() == AlipayRentConstants.INSTALLMENT_STATUS_UNPAID) {
                return installment;
            }
        }
        return null;
    }

    private InstallmentBill findNextUnpaidBill(Integer orderId) {
        List<InstallmentBill> bills = billMapper.selectList(new QueryWrapper<InstallmentBill>()
                .eq("order_id", orderId)
                .ne("status", CapabilityConstants.BILL_PAID)
                .orderByAsc("period_no"));
        return bills == null || bills.isEmpty() ? null : bills.get(0);
    }

    private InstallmentBill createBillFromPlan(Order order, Integer periodNo, Date paidAt) {
        RentInstallmentInfoEntity plan = legacyInstallmentMapper.selectOne(new QueryWrapper<RentInstallmentInfoEntity>()
                .eq("order_id", order.getOrderId())
                .eq("period_no", periodNo)
                .last("LIMIT 1"));
        Date now = new Date();
        InstallmentBill bill = new InstallmentBill()
                .setOrderId(order.getOrderId())
                .setBillNo("BILL" + order.getOrderId() + String.format("%03d", periodNo))
                .setPeriodNo(periodNo)
                .setPeriodTotal(order.getOrderTotalRentPeriods() == null ? 1 : order.getOrderTotalRentPeriods())
                .setAmount(plan == null ? safe(order.getOrderTotal()) : yuanToCent(plan.getInstallmentPrice()))
                .setPaidAmount(0)
                .setDueDate(plan == null ? paidAt : plan.getPlanPayTime())
                .setStatus(CapabilityConstants.BILL_WAIT_PAY)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        billMapper.insert(bill);
        return bill;
    }

    private void updateBillOnly(InstallmentBill bill, String tradeNo, Date paidAt) {
        if (bill == null) {
            return;
        }
        Date payTime = paidAt == null ? new Date() : paidAt;
        bill.setStatus(CapabilityConstants.BILL_PAID);
        bill.setPaidAmount(bill.getAmount() == null ? 0 : bill.getAmount());
        bill.setPaidAt(payTime);
        if (StringUtils.hasText(tradeNo)) {
            bill.setPaymentTradeNo(tradeNo);
        }
        bill.setUpdatedAt(new Date());
        billMapper.updateById(bill);
    }

    private void refreshOrderPeriod(Order order) {
        List<RentInstallmentInfoEntity> installments = legacyInstallmentMapper.selectList(
                new QueryWrapper<RentInstallmentInfoEntity>()
                        .eq("order_id", order.getOrderId())
                        .orderByAsc("period_no"));
        if (installments == null || installments.isEmpty()) {
            return;
        }
        int total = installments.size();
        int nextUnpaid = total;
        boolean allPaid = true;
        for (RentInstallmentInfoEntity installment : installments) {
            boolean paid = installment.getStatus() != null
                    && installment.getStatus() == AlipayRentConstants.INSTALLMENT_STATUS_PAID;
            if (!paid) {
                allPaid = false;
                nextUnpaid = installment.getInstallmentNo() == null ? 1 : installment.getInstallmentNo().intValue();
                break;
            }
        }
        order.setOrderTotalRentPeriods(total);
        order.setOrderRentPeriods(allPaid ? total : nextUnpaid);
        order.setOrderLastpay(System.currentTimeMillis());
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);
    }

    private int yuanToCent(String value) {
        try {
            return OrderUtil.convertYuanToCent(value);
        } catch (Exception ex) {
            return 0;
        }
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
