package com.fly.rent.capability.billing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.capability.withhold.WithholdAgreementService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.InstallmentBill;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentInstallmentInfoEntity;
import com.fly.rent.entity.WithholdAgreement;
import com.fly.rent.mapper.InstallmentBillMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstallmentBillService {

    private final OrderMapper orderMapper;
    private final InstallmentBillMapper billMapper;
    private final RentInstallmentInfoEntityMapper legacyInstallmentMapper;
    private final RentCurrentUserService currentUserService;
    private final AlipayBillingAdapter alipayBillingAdapter;
    private final WithholdAgreementService withholdAgreementService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> queryPlan(Integer orderId) {
        Order order = requireOwnOrder(orderId);
        return queryPlanForOrder(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> queryPlanForAdmin(Integer orderId) {
        return queryPlanForOrder(requireOrder(orderId));
    }

    private Map<String, Object> queryPlanForOrder(Order order) {
        List<InstallmentBill> bills = createBillsIfAbsent(order);
        WithholdAgreement agreement = withholdAgreementService.findAgreement(order.getOrderId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", planView(bills, agreement));
        result.put("bills", billViews(bills));
        result.put("withholdSign", withholdAgreementService.toView(agreement));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payBill(Integer orderId, Long billId) {
        Order order = requireOwnOrder(orderId);
        InstallmentBill bill = billMapper.selectById(billId);
        if (bill == null || !order.getOrderId().equals(bill.getOrderId())) {
            throw new IllegalArgumentException("账单不存在或不属于该订单");
        }
        String status = normalizeBillStatus(bill);
        if (!CapabilityConstants.BILL_WAIT_PAY.equals(status) && !CapabilityConstants.BILL_OVERDUE.equals(status)) {
            throw new IllegalArgumentException("当前账单状态不允许支付");
        }
        String tradeNo = alipayBillingAdapter.createBillPayment(order, bill);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradeNO", tradeNo);
        result.put("bill", billView(billMapper.selectById(billId)));
        return result;
    }

    public void handlePaymentCallback(Map<String, String> payload) {
        alipayBillingAdapter.handlePaymentCallback(payload);
    }

    private Order requireOwnOrder(Integer orderId) {
        String userUuid = currentUserService.requireUserUuid();
        Order order = requireOrder(orderId);
        if (!userUuid.equals(order.getUserUuid())) {
            throw new IllegalArgumentException("该订单不属于当前用户");
        }
        return order;
    }

    private Order requireOrder(Integer orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private List<InstallmentBill> createBillsIfAbsent(Order order) {
        List<InstallmentBill> exists = queryBills(order);
        if (!exists.isEmpty()) {
            return exists;
        }
        List<RentInstallmentInfoEntity> legacy = legacyInstallmentMapper.selectList(
                new QueryWrapper<RentInstallmentInfoEntity>()
                        .eq("order_id", order.getOrderId())
                        .orderByAsc("period_no"));
        if (legacy == null || legacy.isEmpty()) {
            createBill(order, 1, 1, safe(order.getOrderTotal()), new Date(safeTime(order.getOrderStart())));
        } else {
            int total = legacy.size();
            for (RentInstallmentInfoEntity item : legacy) {
                createBill(
                        order,
                        item.getInstallmentNo() == null ? 1 : item.getInstallmentNo().intValue(),
                        total,
                        yuanToCent(item.getInstallmentPrice()),
                        item.getPlanPayTime() == null ? new Date(safeTime(order.getOrderStart())) : item.getPlanPayTime()
                );
            }
        }
        return queryBills(order);
    }

    private List<InstallmentBill> queryBills(Order order) {
        List<InstallmentBill> bills = billMapper.selectList(new QueryWrapper<InstallmentBill>()
                .eq("order_id", order.getOrderId())
                .orderByAsc("period_no"));
        return bills == null ? new java.util.ArrayList<>() : bills;
    }

    private void createBill(Order order, int periodNo, int periodTotal, int amount, Date dueDate) {
        InstallmentBill exists = billMapper.selectOne(new QueryWrapper<InstallmentBill>()
                .eq("order_id", order.getOrderId())
                .eq("period_no", periodNo)
                .last("LIMIT 1"));
        if (exists != null) {
            return;
        }
        Date now = new Date();
        String status = CapabilityConstants.BILL_WAIT_PAY;
        Integer legacyStatus = findLegacyStatus(order.getOrderId(), periodNo);
        if (legacyStatus != null && legacyStatus == AlipayRentConstants.INSTALLMENT_STATUS_PAID) {
            status = CapabilityConstants.BILL_PAID;
        }
        billMapper.insert(new InstallmentBill()
                .setOrderId(order.getOrderId())
                .setBillNo("BILL" + order.getOrderId() + String.format("%03d", periodNo))
                .setPeriodNo(periodNo)
                .setPeriodTotal(periodTotal)
                .setAmount(amount)
                .setPaidAmount(CapabilityConstants.BILL_PAID.equals(status) ? amount : 0)
                .setDueDate(dueDate)
                .setPaidAt(CapabilityConstants.BILL_PAID.equals(status) ? now : null)
                .setStatus(status)
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }

    private Integer findLegacyStatus(Integer orderId, int periodNo) {
        RentInstallmentInfoEntity entity = legacyInstallmentMapper.selectOne(new QueryWrapper<RentInstallmentInfoEntity>()
                .eq("order_id", orderId)
                .eq("period_no", periodNo)
                .last("LIMIT 1"));
        return entity == null ? null : entity.getStatus();
    }

    private int yuanToCent(String value) {
        try {
            return OrderUtil.convertYuanToCent(value);
        } catch (Exception ex) {
            return 0;
        }
    }

    private long safeTime(Long value) {
        return value == null || value <= 0 ? System.currentTimeMillis() : value;
    }

    private Map<String, Object> planView(List<InstallmentBill> bills, WithholdAgreement agreement) {
        int total = bills == null ? 0 : bills.size();
        int totalAmount = 0;
        int paidAmount = 0;
        int currentPeriod = 0;
        Date nextDue = null;
        if (bills != null) {
            for (InstallmentBill bill : bills) {
                totalAmount += safe(bill.getAmount());
                paidAmount += safe(bill.getPaidAmount());
                if (!CapabilityConstants.BILL_PAID.equals(normalizeBillStatus(bill)) && currentPeriod == 0) {
                    currentPeriod = bill.getPeriodNo() == null ? 0 : bill.getPeriodNo();
                    nextDue = bill.getDueDate();
                }
            }
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("enabled", total > 0);
        plan.put("periodTotal", total);
        plan.put("currentPeriod", currentPeriod);
        plan.put("totalAmount", totalAmount);
        plan.put("paidAmount", paidAmount);
        plan.put("unpaidAmount", Math.max(0, totalAmount - paidAmount));
        plan.put("nextDueDate", nextDue);
        plan.put("withholdSign", withholdAgreementService.toView(agreement));
        plan.put("bills", billViews(bills));
        return plan;
    }

    private List<Map<String, Object>> billViews(List<InstallmentBill> bills) {
        java.util.ArrayList<Map<String, Object>> result = new java.util.ArrayList<>();
        if (bills != null) {
            for (InstallmentBill bill : bills) {
                result.add(billView(bill));
            }
        }
        return result;
    }

    private Map<String, Object> billView(InstallmentBill bill) {
        String status = normalizeBillStatus(bill);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("billId", String.valueOf(bill.getId()));
        view.put("billNo", bill.getBillNo());
        view.put("periodNo", bill.getPeriodNo());
        view.put("periodTotal", bill.getPeriodTotal());
        view.put("amount", bill.getAmount());
        view.put("paidAmount", bill.getPaidAmount());
        view.put("dueDate", bill.getDueDate());
        view.put("paidAt", bill.getPaidAt());
        view.put("status", status);
        view.put("statusText", statusText(status));
        view.put("canPay", CapabilityConstants.BILL_WAIT_PAY.equals(status) || CapabilityConstants.BILL_OVERDUE.equals(status));
        view.put("canWithhold", CapabilityConstants.BILL_WAIT_PAY.equals(status) || CapabilityConstants.BILL_OVERDUE.equals(status));
        return view;
    }

    private String normalizeBillStatus(InstallmentBill bill) {
        if (bill == null) {
            return CapabilityConstants.BILL_WAIT_PAY;
        }
        String status = bill.getStatus();
        if (CapabilityConstants.BILL_WAIT_PAY.equals(status)
                && bill.getDueDate() != null
                && bill.getDueDate().before(new Date())) {
            return CapabilityConstants.BILL_OVERDUE;
        }
        return status;
    }

    private String statusText(String status) {
        if (CapabilityConstants.BILL_WAIT_PAY.equals(status)) return "待支付";
        if (CapabilityConstants.BILL_PAYING.equals(status)) return "支付中";
        if (CapabilityConstants.BILL_PAID.equals(status)) return "已支付";
        if (CapabilityConstants.BILL_OVERDUE.equals(status)) return "已逾期";
        if (CapabilityConstants.BILL_CLOSED.equals(status)) return "已关闭";
        if (CapabilityConstants.BILL_REFUNDED.equals(status)) return "已退款";
        return status;
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
