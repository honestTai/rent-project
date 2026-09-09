package com.fly.rent.web.goods;

import com.fly.rent.web.support.AbstractWebController;
import com.fly.rent.entity.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web 商品管理控制器。
 * 提供商品的分页查询、上下架、CRUD 以及规格管理等 16 个接口。
 *
 * @author HonestTat
 * @since 2026-03-12
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebGoodsController extends AbstractWebController {

    private final WebGoodsService webGoodsService;
    private final CatalogCategoryAdminService categoryAdminService;

    @PostMapping("/catalog/categories/list")
    public Result listCatalogCategories(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.list();
    }

    @PostMapping("/catalog/categories/create")
    public Result createCatalogCategory(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.create(request(body));
    }

    @PostMapping("/catalog/categories/update")
    public Result updateCatalogCategory(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.update(request(body));
    }

    @PostMapping("/catalog/categories/status/update")
    public Result updateCatalogCategoryStatus(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return categoryAdminService.updateStatus(request(body), idempotencyKey);
    }

    @PostMapping("/catalog/categories/status-impact")
    public Result catalogCategoryStatusImpact(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.statusImpact(request(body));
    }

    @PostMapping("/catalog/categories/delete")
    public Result deleteCatalogCategory(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.delete(request(body));
    }

    @PostMapping("/catalog/categories/goods/bind")
    public Result bindGoodsCategory(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.bindGoods(request(body));
    }

    @PostMapping("/catalog/categories/goods/unclassified/page")
    public Result pageUnclassifiedGoods(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.pageUnclassified(request(body));
    }

    @PostMapping("/catalog/categories/goods/page")
    public Result pageCategoryGoods(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.pageCategoryGoods(request(body));
    }

    @PostMapping("/catalog/categories/goods/unbind")
    public Result unbindCategoryGoods(@RequestBody(required = false) Map<String, Object> body) {
        return categoryAdminService.unbindGoods(request(body));
    }

    /**
     * 商品分页查询。
     */
    @PostMapping("/goods/page")
    public Result pageGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.pageGoods(request(body));
    }

    /**
     * 查询所有商品分类。
     */
    @PostMapping("/goods/classifications/list")
    public Result listClassifications(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.listClassifications();
    }

    /**
     * 已发布商品分页查询。
     */
    @PostMapping("/goods/published/page")
    public Result pagePublished(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.pagePublished(request(body));
    }

    /**
     * 未发布商品分页查询。
     */
    @PostMapping("/goods/unpublished/page")
    public Result pageUnpublished(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.pageUnpublished(request(body));
    }

    /**
     * 商品上架。
     */
    @PostMapping("/goods/up")
    public Result upGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.upGoods(request(body));
    }

    /**
     * 商品下架。
     */
    @PostMapping("/goods/down")
    public Result downGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.downGoods(request(body));
    }

    /**
     * 商品置顶排序。
     */
    @PostMapping("/goods/sort-top")
    public Result sortTop(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.sortTop(request(body));
    }

    /**
     * 删除商品。
     */
    @PostMapping("/goods/delete")
    public Result deleteGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.deleteGoods(request(body));
    }

    /**
     * 更新商品公开状态。
     */
    @PostMapping("/goods/public-status/update")
    public Result updatePublicStatus(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.updatePublicStatus(request(body));
    }

    /**
     * 创建商品。
     */
    @PostMapping("/goods/create")
    public Result createGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.createGoods(request(body));
    }

    /**
     * 更新商品信息。
     */
    @PostMapping("/goods/update")
    public Result updateGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.updateGoods(request(body));
    }

    /**
     * 查询商品详情。
     */
    @PostMapping("/goods/detail")
    public Result detailGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.detailGoods(request(body));
    }

    /**
     * 同步商品到支付宝。
     */
    @PostMapping("/goods/sync")
    public Result syncGoods(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.syncGoodsToAlipay(request(body));
    }

    /**
     * 查询支付宝普通商品类目。
     */
    @PostMapping("/goods/alipay-categories/query")
    public Result queryAlipayItemCategories(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.queryAlipayItemCategories(request(body));
    }

    /**
     * 查询支付宝芝麻信用预授权租赁类目。
     */
    @PostMapping("/goods/alipay-rent-categories/query")
    public Result queryAlipayRentCategories(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.queryAlipayRentCategories(request(body));
    }

    /**
     * 分页查询商品同步日志。
     */
    @PostMapping("/goods/sync-logs/page")
    public Result pageGoodsSyncLogs(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.pageGoodsSyncLogs(request(body));
    }

    /**
     * 双向同步支付宝商品。
     */
    @PostMapping("/goods/sync-bidirectional")
    public Result syncGoodsBidirectional(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.syncGoodsBidirectional();
    }

    /**
     * 创建商品规格。
     */
    @PostMapping("/goods/attrs/create")
    public Result createAttr(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.createAttr(request(body));
    }

    /**
     * 更新商品规格。
     */
    @PostMapping("/goods/attrs/update")
    public Result updateAttr(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.updateAttr(request(body));
    }

    /**
     * 删除商品规格。
     */
    @PostMapping("/goods/attrs/delete")
    public Result deleteAttr(@RequestBody(required = false) Map<String, Object> body) {
        return webGoodsService.deleteAttrAndAutoSync(request(body));
    }
}
