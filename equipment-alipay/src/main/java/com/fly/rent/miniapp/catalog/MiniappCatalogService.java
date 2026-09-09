package com.fly.rent.miniapp.catalog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.catalog.CatalogIconPolicy;
import com.fly.rent.common.order.RentOrderStatus;
import com.fly.rent.common.order.RentViewAssembler;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.CatalogCategory;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.MiniappBanner;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.CatalogCategoryMapper;
import com.fly.rent.mapper.CategoryGoodsCount;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.mapper.GoodsSalesCount;
import com.fly.rent.mapper.MiniappBannerMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.cache.MiniappCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 小程序商品与分类目录服务。 */
@RequiredArgsConstructor
@Service
public class MiniappCatalogService {

    private static final String CACHE_KEY_BANNERS = "catalog:banners";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Pattern CATEGORY_CODE = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Set<String> SORT_FIELDS = new LinkedHashSet<>();

    static {
        Collections.addAll(SORT_FIELDS, "DEFAULT", "PRICE", "SALES", "NEWEST");
    }

    private final GoodMapper goodMapper;
    private final OrderMapper orderMapper;
    private final AttrMapper attrMapper;
    private final MiniappBannerMapper miniappBannerMapper;
    private final CatalogCategoryMapper categoryMapper;
    private final RentViewAssembler viewAssembler;
    private final MiniappCacheService miniappCache;

    /** 首页轮播，仍沿用现有 Redis 热点缓存。 */
    public List<RentViews.BannerView> listBanners() {
        return miniappCache.getOrLoad(CACHE_KEY_BANNERS, this::listBannersFromDb, false);
    }

    private List<RentViews.BannerView> listBannersFromDb() {
        List<RentViews.BannerView> configuredBanners = listConfiguredBanners();
        if (!configuredBanners.isEmpty()) {
            return configuredBanners;
        }
        return listGoodsBannersFromDb();
    }

    private List<RentViews.BannerView> listConfiguredBanners() {
        List<MiniappBanner> records = miniappBannerMapper.selectList(new QueryWrapper<MiniappBanner>()
                .eq("status", 1)
                .orderByDesc("sort_order")
                .orderByDesc("banner_id"));
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<RentViews.BannerView> banners = new ArrayList<>();
        int sort = 1;
        for (MiniappBanner record : records) {
            RentViews.BannerView banner = new RentViews.BannerView();
            banner.setBannerId(String.valueOf(record.getBannerId()));
            banner.setTitle(record.getTitle());
            banner.setDesc(record.getDescription());
            banner.setBadge(record.getBadge());
            banner.setImage(record.getImage());
            banner.setLinkType(record.getLinkType());
            banner.setLinkValue(record.getLinkValue());
            banner.setSort(record.getSortOrder() == null ? sort : record.getSortOrder());
            banner.setStatus(record.getStatus());
            banners.add(banner);
            sort++;
        }
        return banners;
    }

    private List<RentViews.BannerView> listGoodsBannersFromDb() {
        List<Good> goods = loadActiveGoods();
        List<RentViews.BannerView> banners = new ArrayList<>();
        int sort = 1;
        for (Good good : goods.stream().limit(3).collect(Collectors.toList())) {
            RentViews.BannerView banner = new RentViews.BannerView();
            banner.setBannerId("banner-" + good.getGoodId());
            banner.setTitle(good.getGoodTitle());
            banner.setDesc(StringUtils.hasText(good.getGoodAct()) ? good.getGoodAct() : good.getGoodDesc());
            banner.setBadge("热租");
            banner.setImage(good.getGoodCover());
            banner.setSort(sort++);
            banner.setStatus(1);
            banners.add(banner);
        }
        return banners;
    }

    /** 定时刷新 Banner 缓存。 */
    public void refreshBannersCache() {
        miniappCache.put(CACHE_KEY_BANNERS, listBannersFromDb());
    }

    public void evictBannersCache() {
        miniappCache.evict(CACHE_KEY_BANNERS);
    }

    /** 商品或分类变更后使 Banner 立即失效。商品分页不缓存，避免动态筛选产生陈旧键。 */
    public void evictCatalogCaches() {
        miniappCache.evict(CACHE_KEY_BANNERS);
    }

    public RentViews.PageData<RentViews.RentalGoodView> listGoods(RentRequests.GoodsQueryRequest request) {
        ValidatedGoodsQuery query = validateGoodsQuery(request == null
                ? new RentRequests.GoodsQueryRequest() : request);
        List<String> categoryCodes = resolveCategoryCodes(query.categoryCode, query.includeDescendants);

        QueryWrapper<Good> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).eq("is_public", 1);
        wrapper.exists("SELECT 1 FROM goods_sku sku WHERE sku.goods_id = goods.goods_id");
        wrapper.and(w -> w.isNull("category_code").or().eq("category_code", "").or().exists(
                "SELECT 1 FROM category c WHERE c.code = goods.category_code AND c.status = 1"));
        if (!categoryCodes.isEmpty()) {
            wrapper.in("category_code", categoryCodes);
        }
        if (StringUtils.hasText(query.keyword)) {
            wrapper.and(w -> w.like("title", query.keyword)
                    .or().like("activity_text", query.keyword)
                    .or().like("description", query.keyword)
                    .or().like("brand", query.keyword)
                    .or().like("model_name", query.keyword));
        }
        if (query.onlyAvailable) {
            wrapper.exists("SELECT 1 FROM goods_sku available_sku "
                    + "WHERE available_sku.goods_id = goods.goods_id AND available_sku.stock > 0");
        }
        applySort(wrapper, query.sortBy, query.sortOrder);

        Page<Good> mpPage = new Page<>(query.page, query.pageSize);
        goodMapper.selectPage(mpPage, wrapper);
        List<Good> records = mpPage.getRecords() == null ? Collections.<Good>emptyList() : mpPage.getRecords();

        RentViews.PageData<RentViews.RentalGoodView> data = new RentViews.PageData<>();
        data.setTotal(mpPage.getTotal());
        if (records.isEmpty()) {
            data.setList(new ArrayList<>());
            return data;
        }

        List<Integer> goodIds = records.stream().map(Good::getGoodId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Integer, Long> salesByGoodId = loadSalesCounts(goodIds);
        List<Attr> attrs = attrMapper.selectList(new QueryWrapper<Attr>().in("goods_id", goodIds));
        Map<Integer, List<Attr>> attrsByGood = (attrs == null ? Collections.<Attr>emptyList() : attrs).stream()
                .filter(a -> a.getGoodId() != null)
                .collect(Collectors.groupingBy(Attr::getGoodId));

        Map<String, CatalogCategory> categoryByCode = loadCategoryContext(records);
        List<RentViews.RentalGoodView> views = new ArrayList<>();
        for (Good good : records) {
            List<Attr> goodAttrs = attrsByGood.getOrDefault(good.getGoodId(), Collections.<Attr>emptyList());
            Attr primary = selectPrimaryAttr(goodAttrs, query.onlyAvailable);
            if (primary == null) {
                throw new RentApiException(5001, "商品规格数据与目录查询结果不一致");
            }
            RentViews.RentalGoodView view = viewAssembler.toRentalGoodView(good, primary, goodAttrs);
            view.setSales(salesByGoodId.getOrDefault(good.getGoodId(), 0L));
            if (!view.getSupportedRentUnits().contains(view.getDefaultRentUnit())) {
                throw new RentApiException(5001, "商品默认租期单位不在其支持范围内");
            }
            attachCategory(view, good, categoryByCode);
            views.add(view);
        }
        data.setList(views);
        return data;
    }

    private Map<Integer, Long> loadSalesCounts(List<Integer> goodIds) {
        if (goodIds == null || goodIds.isEmpty()) return Collections.emptyMap();
        List<GoodsSalesCount> rows = orderMapper.countEffectiveSalesByGoodIds(
                goodIds, RentOrderStatus.codesForGoodsSales());
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();
        return rows.stream()
                .filter(row -> row != null && row.getGoodId() != null)
                .collect(Collectors.toMap(
                        GoodsSalesCount::getGoodId,
                        row -> row.getSalesCount() == null ? 0L : row.getSalesCount(),
                        Long::sum
                ));
    }

    public List<RentViews.CatalogCategoryView> listCategories(
            Boolean includeChildrenValue, Boolean includeGoodsCountValue) {
        boolean includeChildren = includeChildrenValue == null || includeChildrenValue;
        boolean includeGoodsCount = includeGoodsCountValue == null || includeGoodsCountValue;
        List<CatalogCategory> categories = categoryMapper.selectList(new QueryWrapper<CatalogCategory>()
                .eq("status", 1).isNotNull("code").ne("code", "")
                .orderByAsc("sort_order").orderByAsc("code"));
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>();
        }
        for (CatalogCategory category : categories) {
            CatalogIconPolicy.requireAllowed(category.getIconName());
        }

        Map<String, CatalogCategory> byCode = categories.stream()
                .collect(Collectors.toMap(CatalogCategory::getCode, c -> c, (left, right) -> left, LinkedHashMap::new));
        validateEnabledCategoryTree(categories, byCode);
        Map<String, Long> directCounts = includeGoodsCount ? loadDirectCounts(byCode.keySet()) : Collections.emptyMap();
        Map<String, List<CatalogCategory>> childrenByParent = categories.stream()
                .filter(c -> StringUtils.hasText(c.getParentCode()))
                .collect(Collectors.groupingBy(CatalogCategory::getParentCode));

        List<CatalogCategory> roots = categories.stream()
                .filter(c -> !StringUtils.hasText(c.getParentCode()))
                .sorted(categoryComparator()).collect(Collectors.toList());
        List<RentViews.CatalogCategoryView> result = new ArrayList<>();
        for (CatalogCategory root : roots) {
            RentViews.CatalogCategoryView rootView = toCategoryView(root, includeGoodsCount
                    ? aggregateCount(root.getCode(), directCounts, childrenByParent) : null);
            if (includeChildren) {
                List<CatalogCategory> children = new ArrayList<>(
                        childrenByParent.getOrDefault(root.getCode(), Collections.<CatalogCategory>emptyList()));
                children.sort(categoryComparator());
                for (CatalogCategory child : children) {
                    rootView.getChildren().add(toCategoryView(child,
                            includeGoodsCount ? directCounts.getOrDefault(child.getCode(), 0L) : null));
                }
            }
            result.add(rootView);
        }
        return result;
    }

    public ResolvedSku requireResolvedSku(String goodId, String skuId) {
        Good good = resolveGood(goodId);
        Attr attr = resolveAttr(skuId, good);
        if (good == null || attr == null) {
            throw new RentApiException(404, "商品或规格不存在");
        }
        ResolvedSku resolvedSku = new ResolvedSku();
        resolvedSku.setGood(good);
        resolvedSku.setAttr(attr);
        return resolvedSku;
    }

    private ValidatedGoodsQuery validateGoodsQuery(RentRequests.GoodsQueryRequest request) {
        ValidatedGoodsQuery query = new ValidatedGoodsQuery();
        if (request.getPage() != null && request.getPage() < 1) {
            throw new RentApiException(4001, "page 必须从 1 开始");
        }
        if (request.getPageSize() != null
                && (request.getPageSize() < 1 || request.getPageSize() > MAX_PAGE_SIZE)) {
            throw new RentApiException(4001, "pageSize 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        query.page = request.getPage() == null ? 1 : request.getPage();
        query.pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        query.keyword = StringUtils.hasText(request.getKeyword()) ? request.getKeyword().trim() : null;
        query.categoryCode = normalizeCategoryCode(request.getCategoryCode());
        query.includeDescendants = Boolean.TRUE.equals(request.getIncludeDescendants());
        query.onlyAvailable = Boolean.TRUE.equals(request.getOnlyAvailable());
        query.sortBy = StringUtils.hasText(request.getSortBy())
                ? request.getSortBy().trim().toUpperCase(Locale.ROOT) : "DEFAULT";
        query.sortOrder = StringUtils.hasText(request.getSortOrder())
                ? request.getSortOrder().trim().toUpperCase(Locale.ROOT) : "DESC";
        if (!SORT_FIELDS.contains(query.sortBy)) {
            throw new RentApiException(4003, "sortBy 仅支持 DEFAULT、PRICE、SALES、NEWEST");
        }
        if (!"ASC".equals(query.sortOrder) && !"DESC".equals(query.sortOrder)) {
            throw new RentApiException(4003, "sortOrder 仅支持 ASC 或 DESC");
        }
        return query;
    }

    private String normalizeCategoryCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!CATEGORY_CODE.matcher(normalized).matches()) {
            throw new RentApiException(4004, "categoryCode 格式非法");
        }
        return normalized;
    }

    private List<String> resolveCategoryCodes(String categoryCode, boolean includeDescendants) {
        if (!StringUtils.hasText(categoryCode)) {
            return Collections.emptyList();
        }
        CatalogCategory category = categoryMapper.selectOne(new QueryWrapper<CatalogCategory>()
                .eq("code", categoryCode).last("LIMIT 1"));
        if (category == null) {
            throw new RentApiException(4004, "分类不存在");
        }
        if (!Integer.valueOf(1).equals(category.getStatus())) {
            throw new RentApiException(4004, "分类已停用");
        }
        List<String> codes = new ArrayList<>();
        codes.add(categoryCode);
        if (includeDescendants) {
            List<CatalogCategory> children = categoryMapper.selectList(new QueryWrapper<CatalogCategory>()
                    .eq("parent_code", categoryCode).eq("status", 1)
                    .orderByAsc("sort_order").orderByAsc("code"));
            if (children != null) {
                codes.addAll(children.stream().map(CatalogCategory::getCode).collect(Collectors.toList()));
            }
        }
        return codes;
    }

    private void applySort(QueryWrapper<Good> wrapper, String sortBy, String sortOrder) {
        boolean asc = "ASC".equals(sortOrder);
        String column;
        switch (sortBy) {
            case "PRICE":
                column = "(SELECT MIN(sort_sku.daily_rent) FROM goods_sku sort_sku "
                        + "WHERE sort_sku.goods_id = goods.goods_id)";
                break;
            case "SALES":
                column = "(SELECT COALESCE(SUM(CASE WHEN sales_order.quantity IS NULL "
                        + "OR sales_order.quantity < 1 THEN 1 ELSE sales_order.quantity END), 0) "
                        + "FROM rent_order sales_order WHERE sales_order.goods_id = goods.goods_id "
                        + "AND UPPER(TRIM(sales_order.alipay_status)) IN "
                        + "('PAID','DELIVERED','RECEIVED','RETURN_DELIVERED','RETURN_RECEIVED','FINISHED'))";
                break;
            case "NEWEST":
                column = "goods_id";
                break;
            default:
                column = "sort_order";
        }
        wrapper.orderBy(true, asc, column);
        if (!"goods_id".equals(column)) {
            wrapper.orderByAsc("goods_id");
        }
    }

    private Attr selectPrimaryAttr(List<Attr> attrs, boolean onlyAvailable) {
        return attrs.stream().filter(Objects::nonNull)
                .filter(a -> !onlyAvailable || (a.getAttrNum() != null && a.getAttrNum() > 0))
                .min(Comparator.comparing((Attr a) -> a.getAttrAmount() == null ? Integer.MAX_VALUE : a.getAttrAmount())
                        .thenComparing(a -> a.getAttrId() == null ? Integer.MAX_VALUE : a.getAttrId()))
                .orElse(null);
    }

    private Map<String, CatalogCategory> loadCategoryContext(List<Good> goods) {
        Set<String> codes = goods.stream().map(Good::getCategoryCode).filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CatalogCategory> selected = categoryMapper.selectList(
                new QueryWrapper<CatalogCategory>().in("code", codes).eq("status", 1));
        List<CatalogCategory> direct = new ArrayList<>(
                selected == null ? Collections.<CatalogCategory>emptyList() : selected);
        Set<String> parentCodes = direct.stream().map(CatalogCategory::getParentCode)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        if (!parentCodes.isEmpty()) {
            direct.addAll(categoryMapper.selectList(
                    new QueryWrapper<CatalogCategory>().in("code", parentCodes).eq("status", 1)));
        }
        return direct.stream().collect(Collectors.toMap(CatalogCategory::getCode, c -> c, (a, b) -> a));
    }

    private void attachCategory(
            RentViews.RentalGoodView view, Good good, Map<String, CatalogCategory> categoryByCode) {
        if (!StringUtils.hasText(good.getCategoryCode())) {
            return;
        }
        CatalogCategory category = categoryByCode.get(good.getCategoryCode());
        if (category == null) {
            throw new RentApiException(5001, "商品分类数据无效");
        }
        view.setCategoryName(category.getName());
        view.setParentCategoryCode(category.getParentCode());
        if (StringUtils.hasText(category.getParentCode())) {
            CatalogCategory parent = categoryByCode.get(category.getParentCode());
            if (parent == null) {
                throw new RentApiException(5001, "商品父分类数据无效");
            }
            view.setParentCategoryName(parent.getName());
        }
    }

    private void validateEnabledCategoryTree(
            List<CatalogCategory> categories, Map<String, CatalogCategory> byCode) {
        for (CatalogCategory category : categories) {
            if (!CATEGORY_CODE.matcher(category.getCode() == null ? "" : category.getCode()).matches()) {
                throw new RentApiException(5001, "分类编码配置非法");
            }
            requireHttpsUrl(category.getCoverImage(), "分类封面", true);
            if (StringUtils.hasText(category.getParentCode())) {
                CatalogCategory parent = byCode.get(category.getParentCode());
                if (parent == null || StringUtils.hasText(parent.getParentCode())) {
                    throw new RentApiException(5001, "分类层级配置非法");
                }
            }
        }
    }

    private Map<String, Long> loadDirectCounts(Set<String> categoryCodes) {
        List<CategoryGoodsCount> rows = goodMapper.countVisibleGoodsByCategoryCodes(categoryCodes);
        Map<String, Long> counts = new HashMap<>();
        if (rows != null) {
            for (CategoryGoodsCount row : rows) {
                counts.put(row.getCategoryCode(), row.getGoodsCount() == null ? 0L : row.getGoodsCount());
            }
        }
        return counts;
    }

    private long aggregateCount(
            String code,
            Map<String, Long> directCounts,
            Map<String, List<CatalogCategory>> childrenByParent) {
        long count = directCounts.getOrDefault(code, 0L);
        for (CatalogCategory child : childrenByParent.getOrDefault(code, Collections.<CatalogCategory>emptyList())) {
            count += directCounts.getOrDefault(child.getCode(), 0L);
        }
        return count;
    }

    private Comparator<CatalogCategory> categoryComparator() {
        return Comparator.comparing((CatalogCategory c) -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder())
                .thenComparing(CatalogCategory::getCode);
    }

    private RentViews.CatalogCategoryView toCategoryView(CatalogCategory category, Long goodsCount) {
        RentViews.CatalogCategoryView view = new RentViews.CatalogCategoryView();
        view.setCode(category.getCode());
        view.setParentCode(category.getParentCode());
        view.setName(category.getName());
        view.setShortName(category.getShortName());
        view.setDescription(category.getDescription());
        view.setCoverImage(requireHttpsUrl(category.getCoverImage(), "分类封面", true));
        view.setIcon(CatalogIconPolicy.requireAllowed(category.getIconName()));
        view.setSortOrder(category.getSortOrder());
        view.setGoodsCount(goodsCount);
        view.setCreatedAt(formatDate(category.getCreatedAt()));
        view.setUpdatedAt(formatDate(category.getUpdatedAt()));
        return view;
    }

    private String formatDate(Date date) {
        return date == null ? null : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private String requireHttpsUrl(String value, String field) {
        return requireHttpsUrl(value, field, false);
    }

    private String requireHttpsUrl(String value, String field, boolean optional) {
        if (!StringUtils.hasText(value)) {
            if (optional) {
                return null;
            }
            throw new RentApiException(5001, field + "不能为空");
        }
        String normalized = value.trim();
        if (!normalized.startsWith("https://")) {
            throw new RentApiException(5001, field + "必须是完整 HTTPS URL");
        }
        return normalized;
    }

    private Good resolveGood(String goodId) {
        if (!StringUtils.hasText(goodId)) {
            throw new RentApiException(400, "goodId 不能为空");
        }
        if (goodId.matches("\\d+") && goodId.length() <= 9) {
            try {
                Good byId = goodMapper.selectById(Integer.valueOf(goodId));
                if (byId != null) {
                    return byId;
                }
            } catch (NumberFormatException ignored) {
                // 继续按支付宝商品 ID 查询。
            }
        }
        List<Good> goods = goodMapper.selectList(
                new QueryWrapper<Good>().eq("alipay_goods_id", goodId.trim()));
        return goods == null || goods.isEmpty() ? null : goods.get(0);
    }

    private Attr resolveAttr(String skuId, Good good) {
        if (!StringUtils.hasText(skuId)) {
            throw new RentApiException(400, "skuId 不能为空");
        }
        Attr attr = null;
        if (skuId.matches("\\d+") && skuId.length() <= 9) {
            try {
                attr = attrMapper.selectById(Integer.valueOf(skuId));
            } catch (NumberFormatException ignored) {
                // 继续按支付宝 SKU ID 查询。
            }
        }
        if (attr == null) {
            List<Attr> attrs = attrMapper.selectList(
                    new QueryWrapper<Attr>().eq("alipay_sku_id", skuId.trim()));
            attr = attrs == null || attrs.isEmpty() ? null : attrs.get(0);
        }
        if (attr != null && good != null && !good.getGoodId().equals(attr.getGoodId())) {
            throw new RentApiException(400, "商品与规格不匹配");
        }
        return attr;
    }

    private List<Good> loadActiveGoods() {
        return goodMapper.selectList(new QueryWrapper<Good>()
                .eq("status", 1).eq("is_public", 1)
                .exists("SELECT 1 FROM goods_sku sku WHERE sku.goods_id = goods.goods_id")
                .and(w -> w.isNull("category_code").or().exists(
                        "SELECT 1 FROM category c WHERE c.code = goods.category_code AND c.status = 1"))
                .orderByDesc("sort_order").orderByAsc("goods_id"));
    }

    private static class ValidatedGoodsQuery {
        private int page;
        private int pageSize;
        private String keyword;
        private String categoryCode;
        private boolean includeDescendants;
        private boolean onlyAvailable;
        private String sortBy;
        private String sortOrder;
    }

    public static class ResolvedSku {
        private Good good;
        private Attr attr;

        public Good getGood() { return good; }
        public void setGood(Good good) { this.good = good; }
        public Attr getAttr() { return attr; }
        public void setAttr(Attr attr) { this.attr = attr; }
    }
}
