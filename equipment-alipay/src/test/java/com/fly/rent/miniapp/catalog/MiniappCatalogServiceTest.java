package com.fly.rent.miniapp.catalog;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.order.RentViewAssembler;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.CatalogCategory;
import com.fly.rent.entity.Good;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.CatalogCategoryMapper;
import com.fly.rent.mapper.CategoryGoodsCount;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.mapper.GoodsSalesCount;
import com.fly.rent.mapper.MiniappBannerMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.cache.MiniappCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniappCatalogServiceTest {

    @Mock private GoodMapper goodMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private AttrMapper attrMapper;
    @Mock private MiniappBannerMapper miniappBannerMapper;
    @Mock private CatalogCategoryMapper categoryMapper;
    @Mock private MiniappCacheService cacheService;

    private MiniappCatalogService service;

    @BeforeEach
    void setUp() {
        service = new MiniappCatalogService(
                goodMapper, orderMapper, attrMapper, miniappBannerMapper,
                categoryMapper, new RentViewAssembler(), cacheService);
    }

    @Test
    void categoriesReturnStableEnabledTreeAndDescendantCount() {
        CatalogCategory handheld = category("handheld", null, 20, 1);
        CatalogCategory drone = category("drone", null, 10, 1);
        CatalogCategory portable = category("drone-portable", "drone", 10, 1);
        when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(handheld, portable, drone));
        CategoryGoodsCount count = new CategoryGoodsCount();
        count.setCategoryCode("drone-portable");
        count.setGoodsCount(5L);
        when(goodMapper.countVisibleGoodsByCategoryCodes(any())).thenReturn(Collections.singletonList(count));

        List<RentViews.CatalogCategoryView> result = service.listCategories(true, true);

        assertEquals(Arrays.asList("drone", "handheld"),
                Arrays.asList(result.get(0).getCode(), result.get(1).getCode()));
        assertEquals(5L, result.get(0).getGoodsCount());
        assertEquals("CompassOutline", result.get(0).getIcon());
        assertEquals("drone-portable", result.get(0).getChildren().get(0).getCode());
        assertEquals("TravelOutline", result.get(0).getChildren().get(0).getIcon());
        assertEquals(5L, result.get(0).getChildren().get(0).getGoodsCount());
        assertTrue(result.get(1).getChildren().isEmpty());

        ArgumentCaptor<QueryWrapper<CatalogCategory>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(categoryMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
    }

    @Test
    void categoriesWithoutCountsReturnNullAndAlwaysUseArrays() {
        when(categoryMapper.selectList(any())).thenReturn(Collections.singletonList(
                category("other", null, 90, 1)));

        List<RentViews.CatalogCategoryView> result = service.listCategories(false, false);

        assertNull(result.get(0).getGoodsCount());
        assertNotNull(result.get(0).getChildren());
        assertTrue(result.get(0).getChildren().isEmpty());
    }

    @Test
    void enabledCategoryWithIllegalIconFailsExplicitly() {
        CatalogCategory category = category("drone", null, 10, 1);
        category.setIconName("<script>alert(1)</script>");
        when(categoryMapper.selectList(any())).thenReturn(Collections.singletonList(category));

        RentApiException error = assertThrows(RentApiException.class,
                () -> service.listCategories(true, true));

        assertEquals(4001, error.getCode());
        assertTrue(error.getMessage().contains("白名单"));
    }

    @Test
    void parentCategoryAndKeywordAreAppliedInDatabaseQuery() {
        when(categoryMapper.selectOne(any())).thenReturn(category("drone", null, 10, 1));
        when(categoryMapper.selectList(any())).thenReturn(Collections.singletonList(
                category("drone-portable", "drone", 10, 1)));
        ArgumentCaptor<Wrapper<Good>> wrapperCaptor = emptyGoodsPage();
        RentRequests.GoodsQueryRequest request = new RentRequests.GoodsQueryRequest();
        request.setCategoryCode("drone");
        request.setIncludeDescendants(true);
        request.setKeyword("DJI");
        request.setOnlyAvailable(true);

        RentViews.PageData<RentViews.RentalGoodView> result = service.listGoods(request);

        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("category_code IN"));
        assertTrue(sql.contains("brand LIKE"));
        assertTrue(sql.contains("available_sku.stock > 0"));
        Map<String, Object> params = ((QueryWrapper<Good>) wrapperCaptor.getValue()).getParamNameValuePairs();
        assertTrue(params.values().contains("drone"));
        assertTrue(params.values().contains("drone-portable"));
        assertTrue(params.values().contains("%DJI%"));
    }

    @Test
    void stoppedCategoryReturnsExplicitBusinessError() {
        when(categoryMapper.selectOne(any())).thenReturn(category("drone", null, 10, 0));
        RentRequests.GoodsQueryRequest request = new RentRequests.GoodsQueryRequest();
        request.setCategoryCode("drone");

        RentApiException error = assertThrows(RentApiException.class, () -> service.listGoods(request));

        assertEquals(4004, error.getCode());
        assertTrue(error.getMessage().contains("停用"));
    }

    @Test
    void invalidPagingAndSortingAreRejected() {
        RentRequests.GoodsQueryRequest paging = new RentRequests.GoodsQueryRequest();
        paging.setPage(0);
        assertEquals(4001, assertThrows(RentApiException.class, () -> service.listGoods(paging)).getCode());

        RentRequests.GoodsQueryRequest pageSize = new RentRequests.GoodsQueryRequest();
        pageSize.setPageSize(500);
        assertEquals(4001, assertThrows(RentApiException.class, () -> service.listGoods(pageSize)).getCode());

        RentRequests.GoodsQueryRequest sorting = new RentRequests.GoodsQueryRequest();
        sorting.setSortBy("RANDOM");
        assertEquals(4003, assertThrows(RentApiException.class, () -> service.listGoods(sorting)).getCode());
    }

    @Test
    void defaultRequestUsesStableBusinessOrder() {
        ArgumentCaptor<Wrapper<Good>> wrapperCaptor = emptyGoodsPage();
        RentRequests.GoodsQueryRequest request = new RentRequests.GoodsQueryRequest();
        request.setPage(1);
        request.setPageSize(20);
        request.setKeyword("");
        RentViews.PageData<RentViews.RentalGoodView> result = service.listGoods(request);

        assertEquals(0, result.getTotal());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("goods_id ASC"));
    }

    @Test
    void salesSortUsesTheSamePaidAndFulfilmentStatusesAsReturnedSales() {
        ArgumentCaptor<Wrapper<Good>> wrapperCaptor = emptyGoodsPage();
        RentRequests.GoodsQueryRequest request = new RentRequests.GoodsQueryRequest();
        request.setSortBy("SALES");
        request.setSortOrder("DESC");

        service.listGoods(request);

        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("sales_order.goods_id = goods.goods_id"));
        assertTrue(sql.contains("'PAID','DELIVERED','RECEIVED','RETURN_DELIVERED','RETURN_RECEIVED','FINISHED'"));
        assertTrue(sql.contains("ORDER BY"));
    }

    @Test
    void goodsAmountsUnitsCategoriesAndNullableFieldsHaveStableShape() {
        Good good = new Good();
        good.setGoodId(9);
        good.setGoodTitle("航拍设备");
        good.setGoodDesc("副标题");
        good.setGoodCover("https://cdn.example.invalid/goods/9.png");
        good.setCategoryCode("drone-portable");
        good.setBrand("DJI");
        good.setDeviceType("DRONE");
        good.setDefaultRentUnit("DAY");
        good.setFeatured(1);
        Attr day = attr(91, 9, 1999, 3, 1);
        Attr month = attr(92, 9, 1599, 0, 2);
        CatalogCategory leaf = category("drone-portable", "drone", 10, 1);
        CatalogCategory root = category("drone", null, 10, 1);

        doAnswer(invocation -> {
            Page<Good> page = invocation.getArgument(0);
            page.setRecords(Collections.singletonList(good));
            page.setTotal(1);
            return page;
        }).when(goodMapper).selectPage(any(Page.class), any(Wrapper.class));
        when(attrMapper.selectList(any())).thenReturn(Arrays.asList(day, month));
        GoodsSalesCount sales = new GoodsSalesCount();
        sales.setGoodId(9);
        sales.setSalesCount(6L);
        when(orderMapper.countEffectiveSalesByGoodIds(any(), any())).thenReturn(Collections.singletonList(sales));
        when(categoryMapper.selectList(any()))
                .thenReturn(Collections.singletonList(leaf))
                .thenReturn(Collections.singletonList(root));

        RentViews.RentalGoodView view = service.listGoods(new RentRequests.GoodsQueryRequest()).getList().get(0);

        assertEquals(Integer.valueOf(1599), view.getDailyPrice());
        assertEquals(Integer.valueOf(1599 * 30), view.getMonthlyPrice());
        assertEquals(Long.valueOf(6L), view.getSales());
        assertEquals(Arrays.asList("DAY", "MONTH"), view.getSupportedRentUnits());
        assertEquals("DAY", view.getDefaultRentUnit());
        assertEquals("drone", view.getParentCategoryCode());
        assertEquals("无人机", view.getParentCategoryName());
        assertNull(view.getGoodLabel());
        assertNull(view.getLocation());
        assertTrue(view.getFeatured());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> statuses = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).countEffectiveSalesByGoodIds(any(), statuses.capture());
        assertEquals(Arrays.asList(
                "PAID", "DELIVERED", "RECEIVED", "RETURN_DELIVERED", "RETURN_RECEIVED", "FINISHED"),
                statuses.getValue());
    }

    @Test
    void legacyCoverValueDoesNotBlockTheWholeGoodsQuery() {
        Good good = new Good();
        good.setGoodId(10);
        good.setGoodTitle("历史商品");
        good.setGoodCover("/uploads/goods/legacy.png");
        Attr attr = attr(101, 10, 1000, 1, 1);

        doAnswer(invocation -> {
            Page<Good> page = invocation.getArgument(0);
            page.setRecords(Collections.singletonList(good));
            page.setTotal(1);
            return page;
        }).when(goodMapper).selectPage(any(Page.class), any(Wrapper.class));
        when(orderMapper.countEffectiveSalesByGoodIds(any(), any())).thenReturn(Collections.emptyList());
        when(attrMapper.selectList(any())).thenReturn(Collections.singletonList(attr));

        RentViews.RentalGoodView view = service.listGoods(new RentRequests.GoodsQueryRequest()).getList().get(0);

        assertEquals("/uploads/goods/legacy.png", view.getCover());
    }

    private ArgumentCaptor<Wrapper<Good>> emptyGoodsPage() {
        ArgumentCaptor<Wrapper<Good>> captor = ArgumentCaptor.forClass(Wrapper.class);
        doAnswer(invocation -> {
            Page<Good> page = invocation.getArgument(0);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        }).when(goodMapper).selectPage(any(Page.class), captor.capture());
        return captor;
    }

    private CatalogCategory category(String code, String parent, int sort, int status) {
        CatalogCategory category = new CatalogCategory();
        category.setCode(code);
        category.setParentCode(parent);
        category.setName("drone".equals(code) ? "无人机" : code);
        category.setShortName(category.getName());
        if ("drone".equals(code)) {
            category.setIconName("CompassOutline");
        } else if ("drone-portable".equals(code)) {
            category.setIconName("TravelOutline");
        } else {
            category.setIconName("AppOutline");
        }
        category.setSortOrder(sort);
        category.setStatus(status);
        return category;
    }

    private Attr attr(int id, int goodId, int dailyRent, int stock, int billingCycle) {
        Attr attr = new Attr();
        attr.setAttrId(id);
        attr.setGoodId(goodId);
        attr.setAttrAmount(dailyRent);
        attr.setAttrDeposit(5000);
        attr.setAttrNum(stock);
        attr.setAttrTradeType(billingCycle);
        attr.setMinRent(1);
        return attr;
    }
}
