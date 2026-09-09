package com.fly.rent.web.goods;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.common.catalog.CatalogIconPolicy;
import com.fly.rent.entity.CatalogCategory;
import com.fly.rent.entity.CatalogAdminOperation;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Result;
import com.fly.rent.mapper.CatalogCategoryMapper;
import com.fly.rent.mapper.CategoryGoodsCount;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.miniapp.catalog.MiniappCatalogService;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.web.support.WebResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 后台正式分类管理，受现有 /api/web/** 网关鉴权保护。 */
@Service
@RequiredArgsConstructor
public class CatalogCategoryAdminService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final int MAX_BIND_BATCH_SIZE = 200;

    private final CatalogCategoryMapper categoryMapper;
    private final GoodMapper goodMapper;
    private final MiniappCatalogService catalogService;
    private final CatalogOperationAuditService auditService;

    public Result list() {
        List<CatalogCategory> categories = categoryMapper.selectList(new QueryWrapper<CatalogCategory>()
                .isNotNull("code").ne("code", "")
                .orderByAsc("parent_code").orderByAsc("sort_order").orderByAsc("code"));
        if (categories == null || categories.isEmpty()) {
            return WebResponseUtil.success(new ArrayList<>());
        }
        Set<String> codes = new LinkedHashSet<>();
        for (CatalogCategory category : categories) codes.add(category.getCode());
        Map<String, Long> counts = new LinkedHashMap<>();
        List<CategoryGoodsCount> countRows = goodMapper.countAllGoodsByCategoryCodes(codes);
        if (countRows != null) {
            for (CategoryGoodsCount row : countRows) {
                counts.put(row.getCategoryCode(), row.getGoodsCount());
            }
        }
        Map<String, Map<String, Object>> views = new LinkedHashMap<>();
        for (CatalogCategory category : categories) {
            Map<String, Object> view = categoryView(category, counts.getOrDefault(category.getCode(), 0L));
            views.put(category.getCode(), view);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (CatalogCategory category : categories) {
            Map<String, Object> view = views.get(category.getCode());
            if (!StringUtils.hasText(category.getParentCode()) || !views.containsKey(category.getParentCode())) {
                roots.add(view);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) views
                        .get(category.getParentCode()).get("children");
                children.add(view);
            }
        }
        return WebResponseUtil.success(roots);
    }

    @Transactional
    public Result create(WebRequest request) {
        String code = normalizeCode(request.requiredText("code"));
        if (findByCode(code) != null) {
            throw new RentApiException(4004, "分类编码已存在");
        }
        CatalogCategory category = new CatalogCategory();
        category.setCode(code);
        category.setParentCode(normalizeOptionalCode(request.text("parentCode")));
        category.setName(request.requiredText("name"));
        category.setShortName(StringUtils.hasText(request.text("shortName"))
                ? request.text("shortName").trim() : category.getName());
        category.setDescription(trimToNull(request.text("description")));
        category.setCoverImage(validateOptionalHttps(request.text("coverImage")));
        category.setIconName(CatalogIconPolicy.requireAllowed(request.text("icon")));
        category.setSortOrder(request.integer("sortOrder") == null ? 100 : request.integer("sortOrder"));
        category.setStatus(validateStatus(request.integer("status"), 1));
        validateParent(category.getCode(), category.getParentCode());
        Date now = new Date();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        categoryMapper.insert(category);
        catalogService.evictCatalogCaches();
        auditService.record("CREATE", code, request.operator(), "create category", "success");
        return WebResponseUtil.success(category);
    }

    @Transactional
    public Result update(WebRequest request) {
        String code = normalizeCode(request.requiredText("code"));
        CatalogCategory category = requireCategory(code);
        if (request.raw().containsKey("parentCode")) {
            category.setParentCode(normalizeOptionalCode(request.text("parentCode")));
        }
        if (StringUtils.hasText(request.text("name"))) {
            category.setName(request.text("name").trim());
        }
        if (request.raw().containsKey("shortName")) {
            if (!StringUtils.hasText(request.text("shortName"))) {
                throw new RentApiException(4001, "shortName 不能为空");
            }
            category.setShortName(request.text("shortName").trim());
        }
        if (request.raw().containsKey("description")) {
            category.setDescription(trimToNull(request.text("description")));
        }
        if (request.raw().containsKey("coverImage")) {
            category.setCoverImage(validateOptionalHttps(request.text("coverImage")));
        }
        if (request.raw().containsKey("icon")) {
            category.setIconName(CatalogIconPolicy.validateOptional(request.text("icon")));
        }
        if (request.integer("sortOrder") != null) {
            category.setSortOrder(request.integer("sortOrder"));
        }
        if (request.integer("status") != null) {
            Integer requestedStatus = validateStatus(request.integer("status"), category.getStatus());
            if (!requestedStatus.equals(category.getStatus())) {
                throw new RentApiException(4004, "分类启停必须使用状态接口并确认影响");
            }
        }
        validateParent(code, category.getParentCode());
        validateParentDisable(category);
        validateEnabledIcon(category);
        category.setUpdatedAt(new Date());
        categoryMapper.updateById(category);
        catalogService.evictCatalogCaches();
        auditService.record("UPDATE", code, request.operator(), "update category", "success");
        return WebResponseUtil.success(category);
    }

    public Result statusImpact(WebRequest request) {
        String code = normalizeCode(request.requiredText("code"));
        requireCategory(code);
        return WebResponseUtil.success(statusImpact(code));
    }

    public Result updateStatus(WebRequest request, String idempotencyKey) {
        String code = normalizeCode(request.requiredText("code"));
        CatalogCategory category = requireCategory(code);
        Integer targetStatus = validateStatus(request.integer("status"), null);
        Map<String, Object> impact = statusImpact(code);
        if (Integer.valueOf(0).equals(targetStatus)
                && ((Number) impact.get("publicGoodsCount")).longValue() > 0L) {
            if (!Boolean.TRUE.equals(request.bool("confirmed"))) {
                throw new RentApiException(4004, "停用后将隐藏已绑定的公开商品，请确认影响后重试");
            }
            if (!StringUtils.hasText(idempotencyKey)) {
                throw new RentApiException(4001, "停用有公开商品的分类必须提供 Idempotency-Key");
            }
        }
        category.setStatus(targetStatus);
        validateParentDisable(category);
        validateEnabledIcon(category);
        CatalogAdminOperation operation = auditService.begin(idempotencyKey, "STATUS_UPDATE", code,
                request.operator(), "status=" + targetStatus);
        if (operation != null && Integer.valueOf(1).equals(operation.getSuccess())) {
            return WebResponseUtil.success(statusResult(requireCategory(code), impact, true));
        }
        category.setUpdatedAt(new Date());
        try {
            categoryMapper.updateById(category);
            catalogService.evictCatalogCaches();
            auditService.success(operation, "status=" + targetStatus + ", impact=" + impact);
            if (operation == null) {
                auditService.record("STATUS_UPDATE", code, request.operator(), "status=" + targetStatus, "success");
            }
        } catch (RuntimeException ex) {
            auditService.failure(operation, ex.getMessage());
            throw ex;
        }
        return WebResponseUtil.success(statusResult(category, impact, false));
    }

    @Transactional
    public Result delete(WebRequest request) {
        String code = normalizeCode(request.requiredText("code"));
        CatalogCategory category = requireCategory(code);
        Long childCount = categoryMapper.selectCount(
                new QueryWrapper<CatalogCategory>().eq("parent_code", code));
        if (childCount != null && childCount > 0) {
            throw new RentApiException(4004, "分类仍有子分类，不能删除");
        }
        Long goodsCount = goodMapper.selectCount(new QueryWrapper<Good>().eq("category_code", code));
        if (goodsCount != null && goodsCount > 0) {
            throw new RentApiException(4004, "分类仍被商品引用，请先迁移商品");
        }
        categoryMapper.deleteById(category.getId());
        catalogService.evictCatalogCaches();
        auditService.record("DELETE", code, request.operator(), "delete category", "success");
        return WebResponseUtil.success();
    }

    public Result pageUnclassified(WebRequest request) {
        Page<Good> page = new Page<>(request.page(), request.limit());
        goodMapper.selectPage(page, new QueryWrapper<Good>()
                .and(w -> w.isNull("category_code").or().eq("category_code", ""))
                .orderByDesc("status").orderByAsc("goods_id"));
        return WebResponseUtil.page(page.getRecords(), page.getTotal());
    }

    public Result pageCategoryGoods(WebRequest request) {
        String code = normalizeCode(request.requiredText("categoryCode"));
        requireCategory(code);
        List<String> codes = new ArrayList<>();
        codes.add(code);
        List<CatalogCategory> children = categoryMapper.selectList(
                new QueryWrapper<CatalogCategory>().eq("parent_code", code));
        if (children != null) {
            for (CatalogCategory child : children) codes.add(child.getCode());
        }
        Page<Good> page = new Page<>(request.page(), request.limit());
        goodMapper.selectPage(page, new QueryWrapper<Good>()
                .in("category_code", codes)
                .orderByDesc("status").orderByAsc("goods_id"));
        return WebResponseUtil.page(page.getRecords(), page.getTotal());
    }

    @Transactional
    public Result bindGoods(WebRequest request) {
        CatalogCategory target = requireBindableCategory(request.requiredText("categoryCode"));
        Set<Integer> goodIds = parseGoodIds(request);
        if (goodIds.isEmpty()) {
            throw new RentApiException(4001, "goodIds 不能为空");
        }
        if (goodIds.size() > MAX_BIND_BATCH_SIZE) {
            throw new RentApiException(4001, "单次最多绑定 " + MAX_BIND_BATCH_SIZE + " 个商品");
        }
        List<Good> goods = goodMapper.selectBatchIds(goodIds);
        if (goods == null || goods.size() != goodIds.size()) {
            throw new RentApiException(4004, "部分商品不存在，分类绑定未执行");
        }
        int updated = goodMapper.updateCategoryCodeByIds(goodIds, target.getCode());
        if (updated != goodIds.size()) {
            throw new RentApiException(500, "商品分类绑定数量不一致，操作已回滚");
        }
        catalogService.evictCatalogCaches();
        auditService.record("BIND_GOODS", target.getCode(), request.operator(),
                "goodsCount=" + goodIds.size(), "updated=" + updated);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryCode", target.getCode());
        result.put("updatedCount", updated);
        result.put("goodIds", goodIds);
        return WebResponseUtil.success(result);
    }

    @Transactional
    public Result unbindGoods(WebRequest request) {
        Set<Integer> goodIds = parseGoodIds(request);
        if (goodIds.isEmpty()) throw new RentApiException(4001, "goodIds 不能为空");
        if (goodIds.size() > MAX_BIND_BATCH_SIZE) {
            throw new RentApiException(4001, "单次最多移出 " + MAX_BIND_BATCH_SIZE + " 个商品");
        }
        List<Good> goods = goodMapper.selectBatchIds(goodIds);
        if (goods == null || goods.size() != goodIds.size()) {
            throw new RentApiException(4004, "部分商品不存在，移出操作未执行");
        }
        int updated = goodMapper.clearCategoryCodeByIds(goodIds);
        if (updated != goodIds.size()) throw new RentApiException(500, "商品移出数量不一致，操作已回滚");
        catalogService.evictCatalogCaches();
        auditService.record("UNBIND_GOODS", null, request.operator(),
                "goodsCount=" + goodIds.size(), "updated=" + updated);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedCount", updated);
        result.put("goodIds", goodIds);
        return WebResponseUtil.success(result);
    }

    public CatalogCategory requireBindableCategory(String value) {
        String code = normalizeCode(value);
        CatalogCategory category = requireCategory(code);
        if (!Integer.valueOf(1).equals(category.getStatus())) {
            throw new RentApiException(4004, "停用分类不能绑定商品");
        }
        Long children = categoryMapper.selectCount(
                new QueryWrapper<CatalogCategory>().eq("parent_code", code));
        if (children != null && children > 0) {
            throw new RentApiException(4004, "商品只能绑定到叶子分类");
        }
        return category;
    }

    private Set<Integer> parseGoodIds(WebRequest request) {
        Set<Integer> ids = new LinkedHashSet<>();
        Object value = request.raw().get("goodIds");
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                ids.add(parseGoodId(item));
            }
        } else if (value != null) {
            for (String item : value.toString().split(",")) {
                ids.add(parseGoodId(item));
            }
        } else if (request.integer("goodId") != null) {
            ids.add(request.integer("goodId"));
        }
        return ids;
    }

    private Map<String, Object> categoryView(CatalogCategory category, long goodsCount) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", category.getId());
        view.put("code", category.getCode());
        view.put("parentCode", category.getParentCode());
        view.put("name", category.getName());
        view.put("shortName", category.getShortName());
        view.put("description", category.getDescription());
        view.put("coverImage", category.getCoverImage());
        view.put("icon", category.getIconName());
        view.put("sortOrder", category.getSortOrder());
        view.put("status", category.getStatus());
        view.put("goodsCount", goodsCount);
        view.put("children", new ArrayList<Map<String, Object>>());
        return view;
    }

    private Map<String, Object> statusImpact(String code) {
        Map<String, Object> impact = new LinkedHashMap<>();
        Long goodsCount = goodMapper.selectCount(new QueryWrapper<Good>().eq("category_code", code));
        Long publicGoodsCount = goodMapper.selectCount(new QueryWrapper<Good>()
                .eq("category_code", code).eq("status", 1).eq("is_public", 1)
                .exists("SELECT 1 FROM goods_sku sku WHERE sku.goods_id = goods.goods_id"));
        Long enabledChildCount = categoryMapper.selectCount(new QueryWrapper<CatalogCategory>()
                .eq("parent_code", code).eq("status", 1));
        impact.put("code", code);
        impact.put("goodsCount", goodsCount == null ? 0L : goodsCount);
        impact.put("publicGoodsCount", publicGoodsCount == null ? 0L : publicGoodsCount);
        impact.put("enabledChildCount", enabledChildCount == null ? 0L : enabledChildCount);
        return impact;
    }

    private Map<String, Object> statusResult(CatalogCategory category, Map<String, Object> impact,
                                             boolean idempotentReplay) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("impact", impact);
        result.put("idempotentReplay", idempotentReplay);
        return result;
    }

    private Integer parseGoodId(Object value) {
        try {
            int id = Integer.parseInt(String.valueOf(value).trim());
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new RentApiException(4001, "goodIds 包含非法商品 ID");
        }
    }

    private void validateParent(String code, String parentCode) {
        if (!StringUtils.hasText(parentCode)) return;
        if (code.equals(parentCode)) {
            throw new RentApiException(4004, "分类不能以自身为父分类");
        }
        CatalogCategory parent = requireCategory(parentCode);
        if (StringUtils.hasText(parent.getParentCode())) {
            throw new RentApiException(4004, "分类最多支持两级");
        }
        Long parentGoods = goodMapper.selectCount(new QueryWrapper<Good>().eq("category_code", parentCode));
        if (parentGoods != null && parentGoods > 0) {
            throw new RentApiException(4004, "已绑定商品的叶子分类不能新增子分类");
        }
        Long ownChildren = categoryMapper.selectCount(
                new QueryWrapper<CatalogCategory>().eq("parent_code", code));
        if (ownChildren != null && ownChildren > 0) {
            throw new RentApiException(4004, "含子分类的分类不能移动到第二级");
        }
    }

    private void validateParentDisable(CatalogCategory category) {
        if (!Integer.valueOf(0).equals(category.getStatus())) return;
        Long enabledChildren = categoryMapper.selectCount(new QueryWrapper<CatalogCategory>()
                .eq("parent_code", category.getCode()).eq("status", 1));
        if (enabledChildren != null && enabledChildren > 0) {
            throw new RentApiException(4004, "请先停用全部子分类");
        }
    }

    private void validateEnabledIcon(CatalogCategory category) {
        if (Integer.valueOf(1).equals(category.getStatus())) {
            category.setIconName(CatalogIconPolicy.requireAllowed(category.getIconName()));
        } else {
            category.setIconName(CatalogIconPolicy.validateOptional(category.getIconName()));
        }
    }

    private CatalogCategory requireCategory(String code) {
        CatalogCategory category = findByCode(normalizeCode(code));
        if (category == null) {
            throw new RentApiException(4004, "分类不存在");
        }
        return category;
    }

    private CatalogCategory findByCode(String code) {
        return categoryMapper.selectOne(new QueryWrapper<CatalogCategory>()
                .eq("code", code).last("LIMIT 1"));
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new RentApiException(4001, "分类编码不能为空");
        }
        String code = value.trim().toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new RentApiException(4004, "分类编码格式非法");
        }
        return code;
    }

    private String normalizeOptionalCode(String value) {
        return StringUtils.hasText(value) ? normalizeCode(value) : null;
    }

    private Integer validateStatus(Integer status, Integer defaultValue) {
        if (status == null) {
            if (defaultValue == null) throw new RentApiException(4001, "status 不能为空");
            return defaultValue;
        }
        if (status != 0 && status != 1) throw new RentApiException(4001, "status 仅支持 0 或 1");
        return status;
    }

    private String validateOptionalHttps(String value) {
        String normalized = trimToNull(value);
        if (normalized != null && !normalized.startsWith("https://")) {
            throw new RentApiException(4001, "coverImage 必须是完整 HTTPS URL");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
