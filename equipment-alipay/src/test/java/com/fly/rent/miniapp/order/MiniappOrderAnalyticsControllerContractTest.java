package com.fly.rent.miniapp.order;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MiniappOrderAnalyticsControllerContractTest {
    @Test void yearlyRouteIsGetAndAcceptsOnlyOptionalYearParameter() throws Exception {
        RequestMapping base = MiniappOrderController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/rent/v1/miniapp/orders"}, base.value());
        Method method = MiniappOrderController.class.getMethod("yearlyAnalytics", String.class);
        assertArrayEquals(new String[]{"/analytics/yearly"}, method.getAnnotation(GetMapping.class).value());
        RequestParam year = method.getParameters()[0].getAnnotation(RequestParam.class);
        assertEquals("year", year.value());
        assertFalse(year.required());
        assertEquals(1, method.getParameterCount());
    }
}
