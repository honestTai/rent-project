package com.fly.rent.legacy.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayCommerceRentOrderQueryRequest;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.legacy.dto.apilyRentUtil.BaseApily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlipayClientServiceImplDemoModeTest {

    private final BaseApily baseApily = mock(BaseApily.class);
    private final AlipayClient realClient = mock(AlipayClient.class);
    private final AlipayPlatformConfigService configService = mock(AlipayPlatformConfigService.class);
    private final AlipayClientServiceImpl service = new AlipayClientServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseApily", baseApily);
        ReflectionTestUtils.setField(service, "configService", configService);
        when(baseApily.getAlipayClient()).thenReturn(realClient);
    }

    @Test
    void blocksNetworkAndReturnsMarkedSuccessWhenDemoModeEnabled() throws Exception {
        when(configService.demoModeEnabled()).thenReturn(true);

        AlipayCommerceRentOrderQueryResponse response =
                service.execute(new AlipayCommerceRentOrderQueryRequest());

        assertTrue(response.isSuccess());
        assertEquals(null, response.getSubCode());
        assertEquals(AlipayDemoResponseFactory.DEMO_MESSAGE, response.getSubMsg());
        assertEquals("true", response.getParams().get("demoMode"));
        verify(baseApily, never()).getAlipayClient();
        verify(realClient, never()).execute(any(AlipayCommerceRentOrderQueryRequest.class));
    }

    @Test
    void delegatesToRealClientWhenDemoModeDisabled() throws Exception {
        when(configService.demoModeEnabled()).thenReturn(false);
        AlipayCommerceRentOrderQueryResponse expected = new AlipayCommerceRentOrderQueryResponse();
        when(realClient.execute(any(AlipayCommerceRentOrderQueryRequest.class))).thenReturn(expected);

        assertEquals(expected, service.execute(new AlipayCommerceRentOrderQueryRequest()));
        verify(realClient).execute(any(AlipayCommerceRentOrderQueryRequest.class));
    }
}
