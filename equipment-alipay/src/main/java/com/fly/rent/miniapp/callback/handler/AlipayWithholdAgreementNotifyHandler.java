package com.fly.rent.miniapp.callback.handler;

import com.fly.rent.capability.withhold.WithholdAgreementService;
import com.fly.rent.miniapp.callback.AlipayNotifyHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AlipayWithholdAgreementNotifyHandler implements AlipayNotifyHandler {

    private final WithholdAgreementService withholdAgreementService;

    @Override
    public boolean supports(Map<String, String> params) {
        String externalAgreementNo = params.get("external_agreement_no");
        return StringUtils.hasText(externalAgreementNo) && externalAgreementNo.startsWith("EMSWH");
    }

    @Override
    public String handle(Map<String, String> params) {
        withholdAgreementService.handleAgreementCallback(params);
        return "success";
    }
}
