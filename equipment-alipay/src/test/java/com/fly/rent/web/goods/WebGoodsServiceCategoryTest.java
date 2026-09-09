package com.fly.rent.web.goods;

import com.fly.rent.entity.Good;
import com.fly.rent.entity.Result;
import com.fly.rent.mapper.AlipayGoodsSyncLogMapper;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.ClassfyMapper;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.miniapp.catalog.MiniappCatalogService;
import com.fly.rent.web.support.WebRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebGoodsServiceCategoryTest {

    @Test
    void queryAlipayItemCategoriesAllowsBlankKeywordAndLoadsAllAuditedRentCategories() throws Exception {
        AlipayGoodsSyncService syncService = mock(AlipayGoodsSyncService.class);
        WebGoodsService service = newService(mock(GoodMapper.class), syncService);
        when(syncService.queryItemCategories(null, "2", "AUDIT_PASSED", 500))
                .thenReturn(Collections.singletonList(Collections.singletonMap("categoryId", "C001686066")));

        Result result = service.queryAlipayItemCategories(WebRequest.of(mapOf(
                "itemType", "2",
                "catStatus", "AUDIT_PASSED",
                "limit", 500
        )));

        assertEquals(0, result.getCode());
        verify(syncService).queryItemCategories(null, "2", "AUDIT_PASSED", 500);
    }

    @Test
    void createGoodsRejectsRentCategoryUsedAsAlipayItemCategory() {
        WebGoodsService service = newService(mock(GoodMapper.class), mock(AlipayGoodsSyncService.class));

        Result result = service.createGoods(WebRequest.of(validCreatePayload(
                "RENT_CAMERA",
                "RENT_CAMERA",
                null,
                null,
                false
        )));

        assertEquals(1, result.getCode());
        assertTrue(result.getMsg().contains("支付宝商品开放类目不能选择 RENT_*"));
    }

    @Test
    void createGoodsInfersComputerRentCategoryWhenRentCategoryIsBlank() {
        GoodMapper goodMapper = mock(GoodMapper.class);
        WebGoodsService service = newService(goodMapper, mock(AlipayGoodsSyncService.class));

        Result result = service.createGoods(WebRequest.of(validCreatePayload(
                "C001686066",
                "",
                "DIY笔记本",
                "3C数码 / 电脑 / DIY电脑 / DIY笔记本",
                false
        )));

        assertEquals(0, result.getCode());
        ArgumentCaptor<Good> captor = ArgumentCaptor.forClass(Good.class);
        verify(goodMapper).insert(captor.capture());
        assertEquals("C001686066", captor.getValue().getAlipayCategoryId());
        assertEquals("RENT_COMPUTER", captor.getValue().getAlipayRentCategoryId());
    }

    @Test
    void updateGoodsRequiresConfirmationWhenSelectedRentCategoryMismatchesRecommendedCategory() {
        GoodMapper goodMapper = mock(GoodMapper.class);
        Good existing = existingGood();
        when(goodMapper.selectById(7)).thenReturn(existing);
        WebGoodsService service = newService(goodMapper, mock(AlipayGoodsSyncService.class));

        Result result = service.updateGoods(WebRequest.of(validUpdatePayload(
                "C001686066",
                "RENT_CAMERA",
                "DIY笔记本",
                "3C数码 / 电脑 / DIY电脑 / DIY笔记本",
                false,
                false
        )));

        assertEquals(1, result.getCode());
        assertTrue(result.getMsg().contains("商品开放类目与租赁组件类目不匹配"));
    }

    @Test
    void updateGoodsRequiresConfirmationWhenSyncedItemCategoryChanges() {
        GoodMapper goodMapper = mock(GoodMapper.class);
        Good existing = existingGood();
        existing.setAlipayGoodsId("2026062322000535489649");
        existing.setAlipayCategoryId("C_OLD_REMOTE");
        when(goodMapper.selectById(7)).thenReturn(existing);
        WebGoodsService service = newService(goodMapper, mock(AlipayGoodsSyncService.class));

        Result result = service.updateGoods(WebRequest.of(validUpdatePayload(
                "C001686066",
                "RENT_COMPUTER",
                "DIY笔记本",
                "3C数码 / 电脑 / DIY电脑 / DIY笔记本",
                false,
                false
        )));

        assertEquals(1, result.getCode());
        assertTrue(result.getMsg().contains("触发支付宝重新提报审核"));
    }

    private WebGoodsService newService(GoodMapper goodMapper, AlipayGoodsSyncService syncService) {
        return new WebGoodsService(
                goodMapper,
                mock(AttrMapper.class),
                mock(ClassfyMapper.class),
                mock(AlipayGoodsSyncLogMapper.class),
                syncService,
                mock(CatalogCategoryAdminService.class),
                mock(MiniappCatalogService.class)
        );
    }

    private Good existingGood() {
        Good good = new Good();
        good.setGoodId(7);
        good.setGoodTitle("办公笔记本2");
        good.setGoodDesc("办公笔记本 方便携带");
        good.setAlipayCategoryId("C001686066");
        good.setAlipayRentCategoryId("RENT_COMPUTER");
        good.setItemFineness("secondHand");
        good.setItemFinenessGrade("95new");
        good.setStatus(1);
        good.setIspub(1);
        return good;
    }

    private Map<String, Object> validCreatePayload(
            String alipayCategoryId,
            String alipayRentCategoryId,
            String alipayCategoryName,
            String alipayCategoryPath,
            boolean confirmMismatch
    ) {
        Map<String, Object> payload = basePayload(alipayCategoryId, alipayRentCategoryId, alipayCategoryName, alipayCategoryPath);
        payload.put("confirmRentCategoryMismatch", confirmMismatch);
        return payload;
    }

    private Map<String, Object> validUpdatePayload(
            String alipayCategoryId,
            String alipayRentCategoryId,
            String alipayCategoryName,
            String alipayCategoryPath,
            boolean confirmMismatch,
            boolean confirmResubmit
    ) {
        Map<String, Object> payload = basePayload(alipayCategoryId, alipayRentCategoryId, alipayCategoryName, alipayCategoryPath);
        payload.put("goodId", 7);
        payload.put("confirmRentCategoryMismatch", confirmMismatch);
        payload.put("confirmAlipayCategoryResubmit", confirmResubmit);
        return payload;
    }

    private Map<String, Object> basePayload(
            String alipayCategoryId,
            String alipayRentCategoryId,
            String alipayCategoryName,
            String alipayCategoryPath
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("goodTitle", "办公笔记本2");
        payload.put("goodDesc", "办公笔记本 方便携带");
        payload.put("goodCover", "https://example.com/notebook.jpg");
        payload.put("alipayCategoryId", alipayCategoryId);
        payload.put("alipayRentCategoryId", alipayRentCategoryId);
        payload.put("alipayCategoryName", alipayCategoryName);
        payload.put("alipayCategoryPath", alipayCategoryPath);
        payload.put("itemFineness", "secondHand");
        payload.put("itemFinenessGrade", "95new");
        payload.put("status", 1);
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
