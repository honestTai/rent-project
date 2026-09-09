package com.fly.rent.capability.billing;

import com.alipay.api.AlipayRequest;
import com.alipay.api.domain.AlipayTradeCreateModel;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.response.AlipayTradeCreateResponse;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.entity.InstallmentBill;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.mapper.InstallmentBillMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayBillingAdapterTest {

    @Mock
    private AlipayClientService alipayClientService;
    @Mock
    private CapabilityConfigService configService;
    @Mock
    private InstallmentBillMapper billMapper;
    @Mock
    private InstallmentPaymentSyncService paymentSyncService;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void createBillPaymentUsesJsapiPayAndOpAppIdFallback() throws Exception {
        when(configService.value("billing.alipay.notify-url", "")).thenReturn("https://example.com/notify");
        when(configService.value("billing.alipay.product-code", "JSAPI_PAY")).thenReturn("JSAPI_PAY");
        when(configService.value("billing.alipay.op-app-id", "")).thenReturn("");
        when(configService.value("alipay.trade-app-id", "")).thenReturn("2021000000000000");
        AlipayTradeCreateResponse response = new AlipayTradeCreateResponse();
        response.setCode("10000");
        response.setTradeNo("TRADE-1");
        when(alipayClientService.execute(any(AlipayTradeCreateRequest.class))).thenReturn(response);

        String tradeNo = service().createBillPayment(order(), bill());

        assertEquals("TRADE-1", tradeNo);
        ArgumentCaptor<AlipayRequest> requestCaptor = ArgumentCaptor.forClass(AlipayRequest.class);
        verify(alipayClientService).execute(requestCaptor.capture());
        AlipayTradeCreateRequest request = (AlipayTradeCreateRequest) requestCaptor.getValue();
        AlipayTradeCreateModel model = (AlipayTradeCreateModel) request.getBizModel();
        assertEquals("JSAPI_PAY", model.getProductCode());
        assertEquals("2021000000000000", model.getOpAppId());
        verify(billMapper).updateById(any(InstallmentBill.class));
    }

    private AlipayBillingAdapter service() {
        return new AlipayBillingAdapter(alipayClientService, configService, billMapper, paymentSyncService);
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId(31);
        order.setUserUuid("2088123456789012");
        return order;
    }

    private InstallmentBill bill() {
        return new InstallmentBill()
                .setId(31001L)
                .setOrderId(31)
                .setBillNo("BILL31001")
                .setAmount(12_000)
                .setPaidAmount(0)
                .setStatus(CapabilityConstants.BILL_WAIT_PAY);
    }
}
