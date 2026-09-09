package com.fly.rent.capability.withhold;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CallbackLogService;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.InstallmentBill;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.WithholdAgreement;
import com.fly.rent.mapper.InstallmentBillMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.WithholdAgreementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithholdAgreementService {

    private final OrderMapper orderMapper;
    private final WithholdAgreementMapper agreementMapper;
    private final InstallmentBillMapper billMapper;
    private final RentCurrentUserService currentUserService;
    private final AlipayWithholdAdapter alipayWithholdAdapter;
    private final CallbackLogService callbackLogService;
    private final CapabilityConfigService configService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sign(Integer orderId) {
        Order order = requireOwnOrder(orderId);
        return signForOrder(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> signForAdmin(Integer orderId) {
        return signForOrder(requireOrder(orderId));
    }

    private Map<String, Object> signForOrder(Order order) {
        WithholdAgreement exists = findAgreement(order.getOrderId());
        if (exists != null && CapabilityConstants.WITHHOLD_SIGNED.equals(exists.getStatus())) {
            return wrap(exists);
        }
        if (!configService.booleanValue("withhold.sign-entry.enabled", false)) {
            throw new IllegalArgumentException("自动代扣签约入口暂未启用，请通过分期账单主动支付");
        }
        requireSignableOrder(order);
        WithholdAgreement agreement = alipayWithholdAdapter.createAgreement(order);
        if (exists == null) {
            agreementMapper.insert(agreement);
        } else {
            agreement.setId(exists.getId());
            agreement.setCreatedAt(exists.getCreatedAt());
            agreementMapper.updateById(agreement);
        }
        log.info("自动代扣签约发起成功: orderId={}, externalAgreementNo={}, status={}",
                agreement.getOrderId(), agreement.getExternalAgreementNo(), agreement.getStatus());
        return wrap(agreement);
    }

    public WithholdAgreement findAgreement(Integer orderId) {
        if (orderId == null) {
            return null;
        }
        return agreementMapper.selectOne(new QueryWrapper<WithholdAgreement>()
                .eq("order_id", orderId)
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleAgreementCallback(Map<String, String> payload) {
        String externalAgreementNo = payload.get("external_agreement_no");
        String agreementNo = payload.get("agreement_no");
        callbackLogService.log(CapabilityConstants.PROVIDER_ALIPAY, "withhold_agreement", externalAgreementNo, payload, true);
        if (!StringUtils.hasText(externalAgreementNo) || !StringUtils.hasText(agreementNo)) {
            return;
        }
        WithholdAgreement agreement = agreementMapper.selectOne(new QueryWrapper<WithholdAgreement>()
                .eq("external_agreement_no", externalAgreementNo)
                .last("LIMIT 1"));
        if (agreement == null) {
            return;
        }
        if (StringUtils.hasText(agreementNo)) {
            agreement.setAgreementNo(agreementNo);
        }
        agreement.setStatus(CapabilityConstants.WITHHOLD_SIGNED);
        agreement.setSignedAt(new Date());
        agreement.setUpdatedAt(new Date());
        agreementMapper.updateById(agreement);
        log.info("自动代扣签约回调已确认: orderId={}, externalAgreementNo={}, agreementNo={}",
                agreement.getOrderId(), externalAgreementNo, agreementNo);
    }

    public Map<String, Object> toView(WithholdAgreement agreement) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (agreement == null) {
            view.put("status", CapabilityConstants.WITHHOLD_UNSIGNED);
            view.put("statusText", "未签约");
            return view;
        }
        view.put("agreementNo", StringUtils.hasText(agreement.getAgreementNo())
                ? agreement.getAgreementNo()
                : agreement.getExternalAgreementNo());
        view.put("status", agreement.getStatus());
        view.put("statusText", statusText(agreement.getStatus()));
        view.put("signUrl", agreement.getSignUrl());
        view.put("signStr", agreement.getSignStr());
        view.put("openType", StringUtils.hasText(agreement.getSignStr()) ? "paySignCenter" : "url");
        view.put("signedAt", agreement.getSignedAt());
        view.put("nextDeductAt", agreement.getNextDeductAt());
        return view;
    }

    private Map<String, Object> wrap(WithholdAgreement agreement) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signUrl", agreement.getSignUrl());
        result.put("signStr", agreement.getSignStr());
        result.put("openType", StringUtils.hasText(agreement.getSignStr()) ? "paySignCenter" : "url");
        result.put("withholdSign", toView(agreement));
        return result;
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

    private void requireSignableOrder(Order order) {
        String status = order.getAlipayStatus();
        boolean active = AlipayRentConstants.STATUS_APPROVED.equals(status)
                || AlipayRentConstants.STATUS_PAID.equals(status)
                || AlipayRentConstants.STATUS_DELIVERED.equals(status)
                || AlipayRentConstants.STATUS_RECEIVED.equals(status)
                || AlipayRentConstants.STATUS_RETURN_DELIVERED.equals(status)
                || AlipayRentConstants.STATUS_RETURN_RECEIVED.equals(status);
        if (!active) {
            throw new IllegalArgumentException("当前订单状态不支持发起自动扣款签约: " + status);
        }
        Long unpaidBills = billMapper.selectCount(new QueryWrapper<InstallmentBill>()
                .eq("order_id", order.getOrderId())
                .in("status", CapabilityConstants.BILL_WAIT_PAY, CapabilityConstants.BILL_OVERDUE, CapabilityConstants.BILL_PAYING));
        if (unpaidBills == null || unpaidBills <= 0) {
            throw new IllegalArgumentException("当前订单没有待收账单，不需要发起自动扣款签约");
        }
    }

    private String statusText(String status) {
        if (CapabilityConstants.WITHHOLD_UNSIGNED.equals(status)) return "未签约";
        if (CapabilityConstants.WITHHOLD_SIGNING.equals(status)) return "签约中";
        if (CapabilityConstants.WITHHOLD_SIGNED.equals(status)) return "已签约";
        if (CapabilityConstants.WITHHOLD_FAILED.equals(status)) return "签约失败";
        return status;
    }
}
