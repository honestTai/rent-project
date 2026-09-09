package com.fly.rent.capability.withhold;

import com.alipay.api.AlipayRequest;
import com.alipay.api.domain.AlipayUserAgreementPageSignModel;
import com.alipay.api.request.AlipayUserAgreementPageSignRequest;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.WithholdAgreement;
import com.fly.rent.legacy.service.AlipayClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayWithholdAdapterTest {

    @Mock
    private AlipayClientService alipayClientService;
    @Mock
    private CapabilityConfigService configService;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createAgreementReturnsMiniappPaySignCenterSignStr() throws Exception {
        when(configService.value("withhold.alipay.notify-url", "")).thenReturn("https://example.com/withhold/notify");
        when(configService.value("withhold.alipay.sign-scene", "INDUSTRY|DIGITAL_MEDIA")).thenReturn("INDUSTRY|DIGITAL_MEDIA");
        when(configService.value("withhold.alipay.product-code", "GENERAL_WITHHOLDING")).thenReturn("GENERAL_WITHHOLDING");
        when(configService.value("withhold.alipay.personal-product-code", "CYCLE_PAY_AUTH_P")).thenReturn("CYCLE_PAY_AUTH_P");
        when(configService.value("withhold.alipay.access-channel", "ALIPAYAPP")).thenReturn("ALIPAYAPP");
        when(configService.value("withhold.alipay.period-type", "DAY")).thenReturn("DAY");
        when(configService.intValue("withhold.alipay.period", 30)).thenReturn(30);
        when(alipayClientService.sdkExecute(any(AlipayUserAgreementPageSignRequest.class))).thenReturn("signed-pay-sign-center-payload");

        WithholdAgreement agreement = service().createAgreement(order());

        assertEquals(CapabilityConstants.WITHHOLD_SIGNING, agreement.getStatus());
        assertEquals("signed-pay-sign-center-payload", agreement.getSignStr());
        ArgumentCaptor<AlipayRequest> requestCaptor = ArgumentCaptor.forClass(AlipayRequest.class);
        verify(alipayClientService).sdkExecute(requestCaptor.capture());
        AlipayUserAgreementPageSignRequest request = (AlipayUserAgreementPageSignRequest) requestCaptor.getValue();
        AlipayUserAgreementPageSignModel model = (AlipayUserAgreementPageSignModel) request.getBizModel();
        assertNotNull(model.getAccessParams());
        assertEquals("ALIPAYAPP", model.getAccessParams().getChannel());
        assertEquals("https://example.com/withhold/notify", request.getNotifyUrl());
    }

    private AlipayWithholdAdapter service() {
        return new AlipayWithholdAdapter(alipayClientService, configService);
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId(31);
        order.setUserUuid("2088123456789012");
        order.setUserTel("13800138000");
        order.setOrderTotal(12_000);
        return order;
    }
}
