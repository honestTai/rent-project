package com.fly.rent.miniapp.callback.handler;

import com.fly.rent.capability.billing.InstallmentBillService;
import com.fly.rent.miniapp.callback.AlipayNotifyHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AlipayBillTradeNotifyHandler implements AlipayNotifyHandler {

    private final InstallmentBillService installmentBillService;

    @Override
    public boolean supports(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        return StringUtils.hasText(outTradeNo)
                && (outTradeNo.startsWith("EMSBILL") || outTradeNo.startsWith("EMSDED"));
    }

    @Override
    public String handle(Map<String, String> params) {
        installmentBillService.handlePaymentCallback(params);
        return "success";
    }
}
