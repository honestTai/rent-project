package com.fly.rent.web.goods;

import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Result;
import com.fly.rent.mapper.AlipayGoodsSyncLogMapper;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.ClassfyMapper;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.miniapp.catalog.MiniappCatalogService;
import com.fly.rent.web.support.WebRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebGoodsServiceAttrPricingTest {

    @Test
    void createAttrRejectsZeroDailyRent() {
        WebGoodsService service = newService(mock(AttrMapper.class));

        Result result = service.createAttr(WebRequest.of(validPayload("attrAmount", 0)));

        assertEquals(1, result.getCode());
        assertTrue(result.getMsg().contains("SKU租金必须大于0"));
    }

    @Test
    void createAttrRejectsZeroDeposit() {
        WebGoodsService service = newService(mock(AttrMapper.class));

        Result result = service.createAttr(WebRequest.of(validPayload("attrDeposit", 0)));

        assertEquals(1, result.getCode());
        assertTrue(result.getMsg().contains("SKU押金必须大于0"));
    }

    @Test
    void createAttrRejectsZeroBuyoutPriceEvenWhenBuyoutDisabled() {
        Map<String, Object> payload = validPayload("buyoutval", 0);
        payload.put("buyout", 0);
        WebGoodsService service = newService(mock(AttrMapper.class));

        Result result = service.createAttr(WebRequest.of(payload));

        assertEquals(1, result.getCode());
        assertTrue(result.getMsg().contains("买断金必须大于0"));
    }

    @Test
    void updateAttrRejectsExistingZeroBuyoutPrice() {
        AttrMapper attrMapper = mock(AttrMapper.class);
        Attr attr = validAttr();
        attr.setBuyoutval(0);
        when(attrMapper.selectById(4)).thenReturn(attr);
        WebGoodsService service = newService(attrMapper);

        Result result = service.updateAttr(WebRequest.of(mapOf("attrId", 4, "attrTitle", "13寸")));

        assertEquals(1, result.getCode());
        assertTrue(result.getMsg().contains("买断金必须大于0"));
    }

    private WebGoodsService newService(AttrMapper attrMapper) {
        return new WebGoodsService(
                mock(GoodMapper.class),
                attrMapper,
                mock(ClassfyMapper.class),
                mock(AlipayGoodsSyncLogMapper.class),
                mock(AlipayGoodsSyncService.class),
                mock(CatalogCategoryAdminService.class),
                mock(MiniappCatalogService.class)
        );
    }

    private Attr validAttr() {
        Attr attr = new Attr();
        attr.setAttrId(4);
        attr.setGoodId(7);
        attr.setAttrTitle("笔记本");
        attr.setAttrSlid("https://example.com/sku.jpg");
        attr.setAttrRentday("1");
        attr.setAttrAmount(1000);
        attr.setAttrDeposit(10000);
        attr.setBuyout(0);
        attr.setBuyoutval(10000);
        attr.setFree(2);
        return attr;
    }

    private Map<String, Object> validPayload(String overrideKey, Object overrideValue) {
        Map<String, Object> payload = mapOf(
                "goodId", 7,
                "attrTitle", "笔记本",
                "attrSlid", "https://example.com/sku.jpg",
                "attrRentday", "1",
                "attrAmount", 1000,
                "attrDeposit", 10000,
                "buyout", 0,
                "buyoutval", 10000,
                "free", 2
        );
        payload.put(overrideKey, overrideValue);
        return payload;
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
