package com.fly.rent.capability.withhold;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AccessParams;
import com.alipay.api.domain.AgreementParams;
import com.alipay.api.domain.AlipayTradePayModel;
import com.alipay.api.domain.AlipayUserAgreementPageSignModel;
import com.alipay.api.domain.PeriodRuleParams;
import com.alipay.api.request.AlipayTradePayRequest;
import com.alipay.api.request.AlipayUserAgreementPageSignRequest;
import com.alipay.api.response.AlipayTradePayResponse;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.entity.InstallmentBill;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.WithholdAgreement;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlipayWithholdAdapter {

    private final AlipayClientService alipayClientService;
    private final CapabilityConfigService configService;

    public WithholdAgreement createAgreement(Order order) {
        String notifyUrl = configService.value("withhold.alipay.notify-url", "");
        if (!StringUtils.hasText(notifyUrl)) {
            throw new IllegalStateException("中台配置缺失: withhold.alipay.notify-url");
        }
        String externalAgreementNo = "EMSWH" + order.getOrderId() + System.currentTimeMillis();
        try {
            AlipayUserAgreementPageSignRequest request = new AlipayUserAgreementPageSignRequest();
            request.setNotifyUrl(notifyUrl);
            AlipayUserAgreementPageSignModel model = new AlipayUserAgreementPageSignModel();
            model.setExternalAgreementNo(externalAgreementNo);
            model.setSignScene(configService.value("withhold.alipay.sign-scene", "INDUSTRY|DIGITAL_MEDIA"));
            model.setProductCode(configService.value("withhold.alipay.product-code", "GENERAL_WITHHOLDING"));
            model.setPersonalProductCode(configService.value("withhold.alipay.personal-product-code", "CYCLE_PAY_AUTH_P"));
            model.setExternalLogonId(order.getUserTel());
            AccessParams accessParams = new AccessParams();
            accessParams.setChannel(configService.value("withhold.alipay.access-channel", "ALIPAYAPP"));
            model.setAccessParams(accessParams);
            PeriodRuleParams rule = new PeriodRuleParams();
            rule.setPeriodType(configService.value("withhold.alipay.period-type", "DAY"));
            rule.setPeriod((long) configService.intValue("withhold.alipay.period", 30));
            rule.setSingleAmount(OrderUtil.convertCentToYuan(order.getOrderTotal()).toPlainString());
            model.setPeriodRuleParams(rule);
            request.setBizModel(model);
            String signStr = alipayClientService.sdkExecute(request);
            log.info("支付宝自动扣款签约参数已生成: orderId={}, externalAgreementNo={}, channel={}",
                    order.getOrderId(), externalAgreementNo, accessParams.getChannel());
            Date now = new Date();
            return new WithholdAgreement()
                    .setOrderId(order.getOrderId())
                    .setUserId(order.getUserUuid())
                    .setProvider(CapabilityConstants.PROVIDER_ALIPAY)
                    .setExternalAgreementNo(externalAgreementNo)
                    .setStatus(CapabilityConstants.WITHHOLD_SIGNING)
                    .setSignStr(signStr)
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝自动扣款签约失败: " + ex.getMessage(), ex);
        }
    }

    public String executeDeduct(InstallmentBill bill, WithholdAgreement agreement) {
        if (!StringUtils.hasText(agreement.getAgreementNo())) {
            throw new IllegalStateException("协议号为空，无法自动扣款");
        }
        try {
            String outTradeNo = StringUtils.hasText(bill.getOutTradeNo())
                    ? bill.getOutTradeNo()
                    : "EMSDED" + bill.getId();
            AlipayTradePayRequest request = new AlipayTradePayRequest();
            AlipayTradePayModel model = new AlipayTradePayModel();
            model.setOutTradeNo(outTradeNo);
            model.setSubject("租赁账单自动扣款-" + bill.getBillNo());
            model.setTotalAmount(OrderUtil.convertCentToYuan(bill.getAmount()).toPlainString());
            model.setProductCode(configService.value("withhold.alipay.trade-product-code", "GENERAL_WITHHOLDING"));
            AgreementParams params = new AgreementParams();
            params.setAgreementNo(agreement.getAgreementNo());
            model.setAgreementParams(params);
            request.setBizModel(model);
            request.setNotifyUrl(configService.value("withhold.alipay.deduct-notify-url", configService.value("billing.alipay.notify-url", "")));
            AlipayTradePayResponse response = alipayClientService.execute(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getSubMsg());
            }
            log.info("支付宝自动扣款成功: billId={}, outTradeNo={}, tradeNo={}, agreementNo={}",
                    bill.getId(), outTradeNo, response.getTradeNo(), agreement.getAgreementNo());
            return response.getTradeNo();
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝自动扣款失败: " + ex.getMessage(), ex);
        }
    }
}
