package com.fly.rent.miniapp.catalog;

import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.entity.Result;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 小程序商品目录入口。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rent/v1/miniapp/catalog")
public class MiniappCatalogController {

    private final MiniappCatalogService catalogService;
    private final AlipayPlatformConfigService alipayPlatformConfigService;

    /**
     * 首页 Banner 列表。
     * @return Banner列表结果
     */
    @PostMapping("/banners/query")
    public Result banners() {
        Map<String, Object> data = new HashMap<>();
        data.put("list", catalogService.listBanners());
        return ResultUtil.success(data);
    }

    /**
     * 获取小程序公开配置（客服电话等），无需登录。
     */
    @GetMapping("/config")
    public Result config() {
        Map<String, Object> data = new HashMap<>();
        data.put("servicePhone", alipayPlatformConfigService.servicePhone());
        data.put("imageStorageType", alipayPlatformConfigService.imageStorageType());
        data.put("imageBaseUrl", alipayPlatformConfigService.publicImageBaseUrl());
        data.put("aliyunImageBaseUrl", alipayPlatformConfigService.aliyunImageBaseUrl());
        data.put("localImageBaseUrl", alipayPlatformConfigService.localImageBaseUrl());
        data.put("demoMode", alipayPlatformConfigService.demoModeEnabled());
        return ResultUtil.success(data);
    }

    /**
     * 商品列表查询。
     * 统一使用 POST，方便后续继续增加复杂筛选参数。
     * @param request 查询请求
     * @return 商品列表结果
     */
    @PostMapping("/goods/query")
    public Result goods(@RequestBody(required = false) RentRequests.GoodsQueryRequest request) {
        RentRequests.GoodsQueryRequest query = request == null ? new RentRequests.GoodsQueryRequest() : request;
        RentViews.PageData<RentViews.RentalGoodView> pageData = catalogService.listGoods(query);
        return ResultUtil.success(pageData);
    }

    /** 查询公开启用分类树。 */
    @PostMapping("/categories/query")
    public Result categories(@RequestBody(required = false) RentRequests.CategoriesQueryRequest request) {
        RentRequests.CategoriesQueryRequest query = request == null
                ? new RentRequests.CategoriesQueryRequest() : request;
        Map<String, Object> data = new HashMap<>();
        data.put("list", catalogService.listCategories(query.getIncludeChildren(), query.getIncludeGoodsCount()));
        data.put("total", ((java.util.List<?>) data.get("list")).size());
        return ResultUtil.success(data);
    }
}
