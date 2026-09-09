package com.fly.rent.web.goods;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.entity.CatalogCategory;
import com.fly.rent.entity.Good;
import com.fly.rent.mapper.CatalogCategoryMapper;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.miniapp.catalog.MiniappCatalogService;
import com.fly.rent.web.support.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogCategoryAdminServiceTest {

    @Mock private CatalogCategoryMapper categoryMapper;
    @Mock private GoodMapper goodMapper;
    @Mock private MiniappCatalogService catalogService;
    @Mock private CatalogOperationAuditService auditService;

    private CatalogCategoryAdminService service;

    @BeforeEach
    void setUp() {
        service = new CatalogCategoryAdminService(categoryMapper, goodMapper, catalogService, auditService);
    }

    @Test
    void parentCycleIsRejected() {
        CatalogCategory category = category("drone", null, 1);
        when(categoryMapper.selectOne(any())).thenReturn(category);

        RentApiException error = assertThrows(RentApiException.class, () -> service.update(
                request("code", "drone", "parentCode", "drone")));

        assertEquals(4004, error.getCode());
        verify(categoryMapper, never()).updateById(any());
    }

    @Test
    void referencedCategoryCannotBeDeleted() {
        CatalogCategory category = category("drone-portable", "drone", 1);
        category.setId(7);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(goodMapper.selectCount(any())).thenReturn(3L);

        RentApiException error = assertThrows(RentApiException.class, () -> service.delete(
                request("code", "drone-portable")));

        assertEquals(4004, error.getCode());
        verify(categoryMapper, never()).deleteById(any(Integer.class));
    }

    @Test
    void bindingUsesExplicitGoodsIdsAndLeafCategoryInOneServiceOperation() {
        CatalogCategory category = category("drone-portable", "drone", 1);
        Good first = new Good(); first.setGoodId(11);
        Good second = new Good(); second.setGoodId(12);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(goodMapper.selectBatchIds(any())).thenReturn(Arrays.asList(first, second));
        when(goodMapper.updateCategoryCodeByIds(any(), any())).thenReturn(2);

        service.bindGoods(request("categoryCode", "drone-portable", "goodIds", Arrays.asList(11, 12)));

        verify(goodMapper).updateCategoryCodeByIds(any(), eq("drone-portable"));
        verify(goodMapper, never()).updateById(any());
        verify(catalogService).evictCatalogCaches();
    }

    @Test
    void unbindingClearsOnlyCategoryForExplicitGoodsIds() {
        Good first = new Good(); first.setGoodId(21);
        Good second = new Good(); second.setGoodId(22);
        when(goodMapper.selectBatchIds(any())).thenReturn(Arrays.asList(first, second));
        when(goodMapper.clearCategoryCodeByIds(any())).thenReturn(2);

        service.unbindGoods(request("goodIds", Arrays.asList(21, 22)));

        verify(goodMapper).clearCategoryCodeByIds(any());
        verify(goodMapper, never()).updateById(any());
        verify(catalogService).evictCatalogCaches();
    }

    @Test
    void stoppedCategoryCannotReceiveGoods() {
        when(categoryMapper.selectOne(any())).thenReturn(category("other", null, 0));

        RentApiException error = assertThrows(RentApiException.class,
                () -> service.requireBindableCategory("other"));

        assertEquals(4004, error.getCode());
        verify(goodMapper, never()).updateById(any());
    }

    @Test
    void genericUpdateCannotBypassStatusImpactConfirmation() {
        CatalogCategory category = category("drone-portable", "drone", 1);
        when(categoryMapper.selectOne(any())).thenReturn(category);

        RentApiException error = assertThrows(RentApiException.class, () -> service.update(
                request("code", "drone-portable", "status", 0)));

        assertEquals(4004, error.getCode());
        verify(categoryMapper, never()).updateById(any());
    }

    @Test
    void disablingVisibleGoodsRequiresExplicitConfirmation() {
        CatalogCategory category = category("drone-portable", "drone", 1);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(goodMapper.selectCount(any())).thenReturn(3L, 2L);
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        RentApiException error = assertThrows(RentApiException.class, () -> service.updateStatus(
                request("code", "drone-portable", "status", 0), "status-key"));

        assertEquals(4004, error.getCode());
        verify(categoryMapper, never()).updateById(any());
    }

    @Test
    void createRejectsIconOutsideWhitelist() {
        when(categoryMapper.selectOne(any())).thenReturn(null);

        RentApiException error = assertThrows(RentApiException.class, () -> service.create(
                request("code", "unsafe", "name", "非法图标", "shortName", "非法",
                        "icon", "https://example.com/icon.svg", "status", 1)));

        assertEquals(4001, error.getCode());
        assertEquals("icon 必须从 antd-mini 分类图标白名单中选择", error.getMessage());
        verify(categoryMapper, never()).insert(any());
    }

    @Test
    void enabledCategoryRejectsBlankIconOnUpdate() {
        CatalogCategory category = category("drone", null, 1);
        when(categoryMapper.selectOne(any())).thenReturn(category);

        RentApiException error = assertThrows(RentApiException.class, () -> service.update(
                request("code", "drone", "icon", "")));

        assertEquals(4001, error.getCode());
        assertEquals("启用分类的 icon 不能为空", error.getMessage());
        verify(categoryMapper, never()).updateById(any());
    }

    @Test
    void updateAcceptsWhitelistedIconName() {
        CatalogCategory category = category("drone", null, 1);
        when(categoryMapper.selectOne(any())).thenReturn(category);

        service.update(request("code", "drone", "icon", "CompassOutline"));

        assertEquals("CompassOutline", category.getIconName());
        verify(categoryMapper).updateById(category);
    }

    private CatalogCategory category(String code, String parent, int status) {
        CatalogCategory category = new CatalogCategory();
        category.setCode(code);
        category.setParentCode(parent);
        category.setStatus(status);
        category.setIconName("AppOutline");
        return category;
    }

    private WebRequest request(Object... values) {
        Map<String, Object> body = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            body.put((String) values[i], values[i + 1]);
        }
        return WebRequest.of(body);
    }
}
