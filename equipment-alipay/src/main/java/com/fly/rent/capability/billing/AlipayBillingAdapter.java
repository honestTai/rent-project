package com.fly.rent.capability.billing;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayTradeCreateModel;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.response.AlipayTradeCreateResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.entity.InstallmentBill;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.mapper.InstallmentBillMapper;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AlipayBillingAdapter {

    private static final String JSAPI_PAY_PRODUCT_CODE = "JSAPI_PAY";

    private final AlipayClientService alipayClientService;
    private final CapabilityConfigService configService;
    private final InstallmentBillMapper billMapper;
    private final InstallmentPaymentSyncService paymentSyncService;

    public String createBillPayment(Order order, InstallmentBill bill) {
        String notifyUrl = configService.value("billing.alipay.notify-url", "");
        if (!StringUtils.hasText(notifyUrl)) {
            throw new IllegalStateException("中台配置缺失: billing.alipay.notify-url");
        }
        if (!StringUtils.hasText(order.getUserUuid())) {
            throw new IllegalStateException("订单缺少支付宝用户标识，无法创建账单支付单");
        }
        try {
            String outTradeNo = "EMSBILL" + bill.getId() + System.currentTimeMillis();
            AlipayTradeCreateRequest request = new AlipayTradeCreateRequest();
            request.setNotifyUrl(notifyUrl);
            AlipayTradeCreateModel model = new AlipayTradeCreateModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(OrderUtil.convertCentToYuan(bill.getAmount()).toPlainString());
            model.setSubject("租赁账单-" + bill.getBillNo());
            AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
            String productCode = configService.value("billing.alipay.product-code", JSAPI_PAY_PRODUCT_CODE);
            model.setProductCode(productCode);
            if (JSAPI_PAY_PRODUCT_CODE.equalsIgnoreCase(productCode)) {
                model.setOpAppId(resolveBillingOpAppId());
            }
            request.setBizModel(model);
            AlipayTradeCreateResponse response = alipayClientService.execute(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getSubMsg());
            }
            bill.setOutTradeNo(outTradeNo);
            bill.setPaymentTradeNo(response.getTradeNo());
            bill.setStatus(CapabilityConstants.BILL_PAYING);
            bill.setUpdatedAt(new Date());
            billMapper.updateById(bill);
            return response.getTradeNo();
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝账单支付单创建失败: " + ex.getMessage(), ex);
        }
    }

    private String resolveBillingOpAppId() {
        String appId = configService.value("billing.alipay.op-app-id", "");
        if (!StringUtils.hasText(appId)) {
            appId = configService.value("alipay.trade-app-id", "");
        }
        if (!StringUtils.hasText(appId)) {
            appId = configService.value("alipay.appid", "");
        }
        if (!StringUtils.hasText(appId)) {
            throw new IllegalStateException("中台配置缺失: billing.alipay.op-app-id");
        }
        return appId;
    }

    public void handlePaymentCallback(Map<String, String> payload) {
        String outTradeNo = payload.get("out_trade_no");
        String tradeNo = payload.get("trade_no");
        String status = payload.get("trade_status");
        if (!StringUtils.hasText(outTradeNo)) {
            return;
        }
        InstallmentBill bill = billMapper.selectOne(new QueryWrapper<InstallmentBill>()
                .eq("out_trade_no", outTradeNo)
                .last("LIMIT 1"));
        if (bill == null) {
            return;
        }
        if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status)) {
            paymentSyncService.markBillPaid(bill, tradeNo, new Date());
            return;
        }
        bill.setUpdatedAt(new Date());
        billMapper.updateById(bill);
    }
}
