package com.fly.rent.web.goods;

import com.alipay.api.AlipayApiException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.entity.AlipayGoodsSyncLog;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Classfy;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Result;
import com.fly.rent.mapper.AlipayGoodsSyncLogMapper;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.ClassfyMapper;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.miniapp.catalog.MiniappCatalogService;
import com.fly.rent.support.util.RentInstallmentPlanSupport;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.web.support.WebResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Web 商品管理服务。
 * 负责商品主体、规格、上下架以及支付宝商品同步相关能力。
 */
@Service
@RequiredArgsConstructor
public class WebGoodsService {

    private static final String ITEM_FINENESS_WHOLE_NEW = "wholeNew";
    private static final String ITEM_FINENESS_SECOND_HAND = "secondHand";
    private static final String DEFAULT_ITEM_FINENESS_GRADE = "95new";
    private static final List<String> ITEM_FINENESS_GRADES =
            Collections.unmodifiableList(Arrays.asList("99new", "95new", "90new", "80new", "70new"));
    private static final List<Map<String, Object>> ALIPAY_RENT_CATEGORY_OPTIONS = buildAlipayRentCategoryOptions();
    private static final Set<String> ALLOWED_ALIPAY_RENT_CATEGORY_IDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("RENT_PHONE", "RENT_COMPUTER", "RENT_CAMERA")));

    private final GoodMapper goodMapper;
    private final AttrMapper attrMapper;
    private final ClassfyMapper classfyMapper;
    private final AlipayGoodsSyncLogMapper alipayGoodsSyncLogMapper;
    private final AlipayGoodsSyncService alipayGoodsSyncService;
    private final CatalogCategoryAdminService categoryAdminService;
    private final MiniappCatalogService miniappCatalogService;

    /**
     * 商品分页查询。
     *
     * @param req 请求参数
     * @return 分页结果
     */
    public Result pageGoods(WebRequest req) {
        Page<Good> mpPage = new Page<>(req.page(), req.limit());
        QueryWrapper<Good> wrapper = new QueryWrapper<>();

        if (req.hasText("goodTitle")) {
            wrapper.like("title", req.text("goodTitle"));
        }
        if (req.hasText("title")) {
            wrapper.like("title", req.text("title"));
        }
        if (req.integer("status") != null && req.integer("status") >= 0) {
            wrapper.eq("status", req.integer("status"));
        }
        wrapper.orderByDesc("sort_order");

        goodMapper.selectPage(mpPage, wrapper);
        attachLatestSyncLogs(mpPage.getRecords());
        return WebResponseUtil.page(mpPage.getRecords(), mpPage.getTotal());
    }

    /**
     * 查询全部商品分类。
     *
     * @return 分类列表
     */
    public Result listClassifications() {
        List<Classfy> list = classfyMapper.selectList(new QueryWrapper<>());
        return WebResponseUtil.success(list);
    }

    /**
     * 给商品列表挂载最新一条支付宝商品同步日志。
     *
     * <p>同步日志实际存储在 alipay_goods_sync_log 表，这里只把最新状态和日志内容放到非数据库字段，
     * 便于前端商品列表直接展示。</p>
     *
     * @param goods 商品列表
     */
    private void attachLatestSyncLogs(List<Good> goods) {
        if (goods == null || goods.isEmpty()) {
            return;
        }
        List<Integer> goodIds = new ArrayList<>();
        for (Good good : goods) {
            if (good != null && good.getGoodId() != null) {
                goodIds.add(good.getGoodId());
            }
        }
        if (goodIds.isEmpty()) {
            return;
        }

        List<AlipayGoodsSyncLog> logs = alipayGoodsSyncLogMapper.selectList(
                new QueryWrapper<AlipayGoodsSyncLog>()
                        .in("good_id", goodIds)
                        .orderByDesc("id"));
        Map<Integer, AlipayGoodsSyncLog> latestLogByGoodId = new HashMap<>();
        if (logs != null) {
            for (AlipayGoodsSyncLog log : logs) {
                if (log != null && log.getGoodId() != null && !latestLogByGoodId.containsKey(log.getGoodId())) {
                    latestLogByGoodId.put(log.getGoodId(), log);
                }
            }
        }

        for (Good good : goods) {
            AlipayGoodsSyncLog latestLog = latestLogByGoodId.get(good.getGoodId());
            if (latestLog == null) {
                continue;
            }
            good.setAlipaySyncStatus(latestLog.getSyncStatus());
            good.setAlipaySyncLog(latestLog.getMessage());
            good.setAlipaySyncStartedAt(latestLog.getStartedAt());
            good.setAlipaySyncFinishedAt(latestLog.getFinishedAt());
        }
    }

    /**
     * 已发布商品分页。
     *
     * @param req 请求参数
     * @return 分页结果
     */
    public Result pagePublished(WebRequest req) {
        return pageGoodsByStatus(req, 1);
    }

    /**
     * 未发布商品分页。
     *
     * @param req 请求参数
     * @return 分页结果
     */
    public Result pageUnpublished(WebRequest req) {
        return pageGoodsByStatus(req, 0);
    }

    /**
     * 商品上架。
     * 上架后会自动同步支付宝商品，避免人工遗漏。
     *
     * @param req 请求参数
     * @return 操作结果
     */
    public Result upGoods(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            return WebResponseUtil.error(1, "商品不存在");
        }
        applyGoodsStatus(good, 1);
        goodMapper.updateById(good);
        miniappCatalogService.evictCatalogCaches();
        return buildAutoSyncResult(null, goodId, "商品上架成功，并已自动同步支付宝商品");
    }

    /**
     * 商品下架。
     * 本地状态更新成功后，同步调用支付宝免审更新接口下架远端商品，避免支付宝侧继续展示。
     *
     * @param req 请求参数
     * @return 操作结果
     */
    public Result downGoods(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            return WebResponseUtil.error(1, "商品不存在");
        }
        applyGoodsStatus(good, 0);
        goodMapper.updateById(good);
        miniappCatalogService.evictCatalogCaches();
        return buildRemoteDelistingResult(goodId);
    }

    /**
     * 商品置顶。
     *
     * @param req 请求参数
     * @return 操作结果
     */
    public Result sortTop(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            return WebResponseUtil.error(1, "商品不存在");
        }
        good.setGoodSort(System.currentTimeMillis());
        goodMapper.updateById(good);
        miniappCatalogService.evictCatalogCaches();
        return WebResponseUtil.success();
    }

    /**
     * 删除商品。
     *
     * @param req 请求参数
     * @return 操作结果
     */
    public Result deleteGoods(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId != null) {
            String deleteResult = tryDeleteRemoteBeforeLocalDelete(goodId);
            if (deleteResult.startsWith("删除失败：")) {
                return WebResponseUtil.error(1, "删除支付宝商品失败：" + deleteResult.substring("删除失败：".length()));
            }
            goodMapper.deleteById(goodId);
            miniappCatalogService.evictCatalogCaches();
            return successWithMessage(null, "商品删除成功，" + deleteResult);
        }
        if (req.hasText("goodIds")) {
            String[] ids = req.text("goodIds").split(",");
            int deletedCount = 0;
            String firstSkippedMessage = null;
            for (String id : ids) {
                String trimmedId = id == null ? null : id.trim();
                if (trimmedId == null || trimmedId.isEmpty()) {
                    continue;
                }
                Integer currentGoodId = Integer.valueOf(trimmedId);
                String deleteResult = tryDeleteRemoteBeforeLocalDelete(currentGoodId);
                if (deleteResult.startsWith("删除失败：")) {
                    return WebResponseUtil.error(1, "已删除" + deletedCount + "个商品，支付宝商品删除失败：" + deleteResult.substring("删除失败：".length()));
                }
                if (firstSkippedMessage == null && deleteResult.startsWith("跳过删除：")) {
                    firstSkippedMessage = deleteResult.substring("跳过删除：".length());
                }
                goodMapper.deleteById(currentGoodId);
                deletedCount++;
            }
            if (deletedCount == 0) {
                return WebResponseUtil.error(1, "未找到可删除的商品");
            }
            miniappCatalogService.evictCatalogCaches();
            String msg = "商品删除成功，并已同步删除支付宝商品";
            if (firstSkippedMessage != null) {
                msg = "商品删除成功，部分商品未同步删除支付宝：" + firstSkippedMessage;
            }
            return successWithMessage(null, msg);
        }
        return WebResponseUtil.error(1, "商品ID不能为空");
    }

    /**
     * 批量更新公开状态。
     * 下架仅更新本地状态；如果是更新为上架态，则自动同步支付宝商品。
     *
     * @param req 请求参数
     * @return 操作结果
     */
    public Result updatePublicStatus(WebRequest req) {
        Integer resolvedStatus = normalizeGoodsStatus(req.integer("status"), req.integer("ispub"));
        if (resolvedStatus == null) {
            resolvedStatus = normalizeGoodsStatus(null, req.integer("isPub"));
        }
        if (resolvedStatus == null) {
            return WebResponseUtil.error(1, "状态参数不能为空");
        }

        final Integer targetStatus = resolvedStatus;
        if (req.integer("goodId") != null) {
            Good good = goodMapper.selectById(req.integer("goodId"));
            if (good == null) {
                return WebResponseUtil.error(1, "商品不存在");
            }
            applyGoodsStatus(good, targetStatus);
            goodMapper.updateById(good);
            miniappCatalogService.evictCatalogCaches();
            if (targetStatus == 1) {
                return buildAutoSyncResult(null, good.getGoodId(), "商品状态更新成功，并已自动同步支付宝商品");
            }
            return buildRemoteDelistingResult(good.getGoodId());
        }

        if (req.hasText("goodIds")) {
            return batchUpdatePublicStatus(req.text("goodIds"), targetStatus);
        }
        return WebResponseUtil.error(1, "商品ID不能为空");
    }

    /**
     * 创建商品。
     * 创建后如果商品是上架态且已经有规格，会自动同步支付宝；
     * 如果暂时没有规格，会先提示本地保存成功，等后续新增规格时自动同步。
     *
     * @param req 请求参数
     * @return 创建结果
     */
    public Result createGoods(WebRequest req) {
        CategorySelection categorySelection;
        try {
            categorySelection = resolveCategorySelection(req);
        } catch (IllegalArgumentException e) {
            return WebResponseUtil.error(1, e.getMessage());
        }
        String itemFineness = resolveItemFineness(req.text("itemFineness"));
        if (!StringUtils.hasText(itemFineness)) {
            return WebResponseUtil.error(1, "商品成色仅支持 wholeNew 或 secondHand");
        }
        String itemFinenessGrade = resolveItemFinenessGrade(req.text("itemFinenessGrade"));
        if (ITEM_FINENESS_SECOND_HAND.equals(itemFineness) && !StringUtils.hasText(itemFinenessGrade)) {
            return WebResponseUtil.error(1, "二手商品成色等级仅支持 99new、95new、90new、80new、70new");
        }
        Good good = new Good();
        good.setGoodTitle(req.text("goodTitle"));
        good.setGoodDesc(req.text("goodDesc"));
        good.setGoodCover(req.text("goodCover"));
        good.setGoodSlid(req.text("goodSlid"));
        good.setGoodMinamo(req.integer("goodMinamo"));
        good.setGoodMaxamo(req.integer("goodMaxamo"));
        good.setGoodDeposit(req.integer("goodDeposit"));
        good.setGoodDistr(req.integer("goodDistr"));
        good.setGoodAct(req.text("goodAct"));
        good.setGoodSpepar(req.text("goodSpepar"));
        good.setGoodCon(req.text("goodCon"));
        good.setGoodRec(req.text("goodRec"));
        good.setOfflinePickup(req.integer("offlinePickup"));
        good.setFreight(req.integer("freight"));
        good.setIsBuyOut(req.integer("isBuyOut"));
        good.setAlipayCategoryId(categorySelection.getAlipayCategoryId());
        good.setAlipayRentCategoryId(categorySelection.getAlipayRentCategoryId());
        good.setItemFineness(itemFineness);
        good.setItemFinenessGrade(StringUtils.hasText(itemFinenessGrade) ? itemFinenessGrade : DEFAULT_ITEM_FINENESS_GRADE);
        applyCatalogFields(good, req, true);

        Integer normalizedStatus = normalizeGoodsStatus(req.integer("status"), req.integer("ispub"));
        if (normalizedStatus == null) {
            normalizedStatus = 1;
        }
        applyGoodsStatus(good, normalizedStatus);
        good.setGoodSort(System.currentTimeMillis());

        goodMapper.insert(good);
        miniappCatalogService.evictCatalogCaches();
        return buildAutoSyncResult(good, good.getGoodId(), "商品创建成功，并已自动同步支付宝商品");
    }

    /**
     * 更新商品主体信息。
     * 当前商品若处于上架态，会自动同步支付宝。
     *
     * @param req 请求参数
     * @return 更新结果
     */
    public Result updateGoods(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            return WebResponseUtil.error(1, "商品不存在");
        }

        if (req.hasText("goodTitle")) {
            good.setGoodTitle(req.text("goodTitle"));
        }
        if (req.hasText("goodDesc")) {
            good.setGoodDesc(req.text("goodDesc"));
        }
        if (req.hasText("goodCover")) {
            good.setGoodCover(req.text("goodCover"));
        }
        if (req.hasText("goodSlid")) {
            good.setGoodSlid(req.text("goodSlid"));
        }
        if (req.integer("goodMinamo") != null) {
            good.setGoodMinamo(req.integer("goodMinamo"));
        }
        if (req.integer("goodMaxamo") != null) {
            good.setGoodMaxamo(req.integer("goodMaxamo"));
        }
        if (req.integer("goodDeposit") != null) {
            good.setGoodDeposit(req.integer("goodDeposit"));
        }
        if (req.integer("goodDistr") != null) {
            good.setGoodDistr(req.integer("goodDistr"));
        }
        if (req.hasText("goodAct")) {
            good.setGoodAct(req.text("goodAct"));
        }
        if (req.hasText("goodSpepar")) {
            good.setGoodSpepar(req.text("goodSpepar"));
        }
        if (req.hasText("goodCon")) {
            good.setGoodCon(req.text("goodCon"));
        }
        if (req.hasText("goodRec")) {
            good.setGoodRec(req.text("goodRec"));
        }
        if (req.integer("offlinePickup") != null) {
            good.setOfflinePickup(req.integer("offlinePickup"));
        }
        if (req.integer("freight") != null) {
            good.setFreight(req.integer("freight"));
        }
        if (req.integer("isBuyOut") != null) {
            good.setIsBuyOut(req.integer("isBuyOut"));
        }
        CategorySelection categorySelection;
        try {
            categorySelection = resolveCategorySelection(req);
        } catch (IllegalArgumentException e) {
            return WebResponseUtil.error(1, e.getMessage());
        }
        boolean alipayCategoryChanged = StringUtils.hasText(good.getAlipayGoodsId())
                && StringUtils.hasText(good.getAlipayCategoryId())
                && !Objects.equals(good.getAlipayCategoryId(), categorySelection.getAlipayCategoryId());
        if (alipayCategoryChanged && !Boolean.TRUE.equals(req.bool("confirmAlipayCategoryResubmit"))) {
            return WebResponseUtil.error(1, "该商品已同步支付宝，修改商品开放类目会触发支付宝重新提报审核；请确认后再保存");
        }
        String itemFineness = resolveItemFineness(req.text("itemFineness"));
        if (!StringUtils.hasText(itemFineness)) {
            return WebResponseUtil.error(1, "商品成色仅支持 wholeNew 或 secondHand");
        }
        String itemFinenessGrade = resolveItemFinenessGrade(req.text("itemFinenessGrade"));
        if (ITEM_FINENESS_SECOND_HAND.equals(itemFineness) && !StringUtils.hasText(itemFinenessGrade)) {
            return WebResponseUtil.error(1, "二手商品成色等级仅支持 99new、95new、90new、80new、70new");
        }
        good.setAlipayCategoryId(categorySelection.getAlipayCategoryId());
        good.setAlipayRentCategoryId(categorySelection.getAlipayRentCategoryId());
        good.setItemFineness(itemFineness);
        good.setItemFinenessGrade(StringUtils.hasText(itemFinenessGrade) ? itemFinenessGrade : DEFAULT_ITEM_FINENESS_GRADE);
        applyCatalogFields(good, req, false);

        Integer normalizedStatus = normalizeGoodsStatus(req.integer("status"), req.integer("ispub"));
        if (normalizedStatus != null) {
            applyGoodsStatus(good, normalizedStatus);
        }

        goodMapper.updateById(good);
        miniappCatalogService.evictCatalogCaches();
        return buildAutoSyncResult(good, good.getGoodId(), "商品修改成功，并已自动同步支付宝商品");
    }

    private CategorySelection resolveCategorySelection(WebRequest req) {
        String alipayCategoryId = normalizeCategoryId(req.text("alipayCategoryId"));
        if (!StringUtils.hasText(alipayCategoryId)) {
            throw new IllegalArgumentException("请选择支付宝商品开放类目");
        }
        if (isRentCategoryId(alipayCategoryId)) {
            throw new IllegalArgumentException("支付宝商品开放类目不能选择 RENT_* 租赁组件类目，请查询并选择 C 开头的商品类目");
        }

        String selectedRentCategoryId = normalizeCategoryId(req.text("alipayRentCategoryId"));
        String inferredRentCategoryId = inferRentCategoryId(req.text("alipayCategoryName"), req.text("alipayCategoryPath"));
        String rentCategoryId = StringUtils.hasText(inferredRentCategoryId) ? inferredRentCategoryId : selectedRentCategoryId;
        if (!isAllowedRentCategoryId(rentCategoryId)) {
            throw new IllegalArgumentException("请选择有效的租赁组件类目：手机、电脑/平板或数码摄像");
        }

        boolean mismatch = StringUtils.hasText(selectedRentCategoryId)
                && isAllowedRentCategoryId(selectedRentCategoryId)
                && StringUtils.hasText(inferredRentCategoryId)
                && !Objects.equals(selectedRentCategoryId, inferredRentCategoryId);
        if (mismatch && !Boolean.TRUE.equals(req.bool("confirmRentCategoryMismatch"))) {
            throw new IllegalArgumentException("商品开放类目与租赁组件类目不匹配；如确需手动调整，请确认后再保存");
        }
        return new CategorySelection(alipayCategoryId, rentCategoryId);
    }

    private String inferRentCategoryId(String alipayCategoryName, String alipayCategoryPath) {
        String text = (firstText(alipayCategoryPath, "") + " " + firstText(alipayCategoryName, "")).toLowerCase(Locale.ROOT);
        if (text.contains("手机")) {
            return "RENT_PHONE";
        }
        if (text.contains("电脑") || text.contains("笔记本") || text.contains("平板")) {
            return "RENT_COMPUTER";
        }
        if (text.contains("相机") || text.contains("摄像") || text.contains("无人机") || text.contains("数码")) {
            return "RENT_CAMERA";
        }
        return null;
    }

    private static String normalizeCategoryId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static boolean isRentCategoryId(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith("RENT_");
    }

    private static boolean isAllowedRentCategoryId(String value) {
        return StringUtils.hasText(value) && ALLOWED_ALIPAY_RENT_CATEGORY_IDS.contains(value.trim());
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String resolveItemFineness(String value) {
        if (!StringUtils.hasText(value)) {
            return ITEM_FINENESS_SECOND_HAND;
        }
        String trimmed = value.trim();
        if (ITEM_FINENESS_WHOLE_NEW.equals(trimmed) || ITEM_FINENESS_SECOND_HAND.equals(trimmed)) {
            return trimmed;
        }
        return null;
    }

    private String resolveItemFinenessGrade(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_ITEM_FINENESS_GRADE;
        }
        String trimmed = value.trim();
        return ITEM_FINENESS_GRADES.contains(trimmed) ? trimmed : null;
    }

    /**
     * 查询商品详情及规格列表。
     *
     * @param req 请求参数
     * @return 详情结果
     */
    public Result detailGoods(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            return WebResponseUtil.error(1, "商品不存在");
        }

        List<Attr> attrs = attrMapper.selectList(new QueryWrapper<Attr>().eq("goods_id", goodId));
        Map<String, Object> detail = new HashMap<>();
        detail.put("good", good);
        detail.put("attrs", attrs);
        return WebResponseUtil.success(detail);
    }

    /**
     * 手动同步支付宝商品。
     *
     * @param req 请求参数
     * @return 同步结果
     */
    public Result syncGoods(WebRequest req) {
        return syncGoodsToAlipay(req);
    }

    /**
     * 创建商品规格。
     * 规格创建成功后，如果商品当前是上架态，会自动同步支付宝商品。
     *
     * @param req 请求参数
     * @return 创建结果
     */
    public Result createAttr(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }

        Attr attr = new Attr();
        attr.setGoodId(goodId);
        attr.setAttrTitle(req.text("attrTitle"));
        attr.setAttrDesc(req.text("attrDesc"));
        attr.setAttrSlid(req.text("attrSlid"));
        attr.setAttrAmount(req.integer("attrAmount"));
        attr.setAttrDeposit(req.integer("attrDeposit"));
        attr.setAttrNum(req.integer("attrNum"));
        attr.setAttrRentday(req.text("attrRentday"));
        attr.setFree(normalizeDepositFreeSwitch(req.integer("free")));
        attr.setBuyout(normalizeSwitch(req.integer("buyout")));
        attr.setBuyoutval(req.integer("buyoutval"));
        attr.setMinRent(req.integer("minRent"));
        attr.setMaxRent(req.integer("maxRent"));
        attr.setPenalAmount(req.integer("penalAmount"));
        attr.setAttrTradeType(req.integer("attrTradeType"));
        attr.setInstallmentEnabled(req.integer("installmentEnabled") == null ? 0 : normalizeSwitch(req.integer("installmentEnabled")));
        attr.setInstallmentPeriods(RentInstallmentPlanSupport.normalizePeriodCsv(
                req.text("installmentPeriods"),
                attr.getInstallmentEnabled()
        ));
        attr.setRentToSend(req.integer("rentToSend"));
        applyBuyoutPriceDefault(attr);
        Result pricingCheck = validateAttrPricing(attr);
        if (pricingCheck != null) {
            return pricingCheck;
        }

        attrMapper.insert(attr);
        miniappCatalogService.evictCatalogCaches();
        return buildAutoSyncResult(attr, goodId, "商品规格创建成功，并已自动同步支付宝商品");
    }

    /**
     * 更新商品规格。
     * 规格修改成功后，如果商品当前是上架态，会自动同步支付宝商品。
     *
     * @param req 请求参数
     * @return 更新结果
     */
    public Result updateAttr(WebRequest req) {
        Integer attrId = req.integer("attrId");
        if (attrId == null) {
            return WebResponseUtil.error(1, "规格ID不能为空");
        }
        Attr attr = attrMapper.selectById(attrId);
        if (attr == null) {
            return WebResponseUtil.error(1, "规格不存在");
        }

        if (req.hasText("attrTitle")) {
            attr.setAttrTitle(req.text("attrTitle"));
        }
        if (req.hasText("attrDesc")) {
            attr.setAttrDesc(req.text("attrDesc"));
        }
        if (req.hasText("attrSlid")) {
            attr.setAttrSlid(req.text("attrSlid"));
        }
        if (req.integer("attrAmount") != null) {
            attr.setAttrAmount(req.integer("attrAmount"));
        }
        if (req.integer("attrDeposit") != null) {
            attr.setAttrDeposit(req.integer("attrDeposit"));
        }
        if (req.integer("attrNum") != null) {
            attr.setAttrNum(req.integer("attrNum"));
        }
        if (req.hasText("attrRentday")) {
            attr.setAttrRentday(req.text("attrRentday"));
        }
        if (req.integer("free") != null) {
            attr.setFree(normalizeDepositFreeSwitch(req.integer("free")));
        }
        if (req.integer("buyout") != null) {
            attr.setBuyout(normalizeSwitch(req.integer("buyout")));
        }
        if (req.integer("buyoutval") != null) {
            attr.setBuyoutval(req.integer("buyoutval"));
        }
        if (req.integer("minRent") != null) {
            attr.setMinRent(req.integer("minRent"));
        }
        if (req.integer("maxRent") != null) {
            attr.setMaxRent(req.integer("maxRent"));
        }
        if (req.integer("penalAmount") != null) {
            attr.setPenalAmount(req.integer("penalAmount"));
        }
        if (req.integer("attrTradeType") != null) {
            attr.setAttrTradeType(req.integer("attrTradeType"));
        }
        if (req.integer("installmentEnabled") != null) {
            attr.setInstallmentEnabled(normalizeSwitch(req.integer("installmentEnabled")));
        }
        if (req.hasText("installmentPeriods") || req.integer("installmentEnabled") != null) {
            attr.setInstallmentPeriods(RentInstallmentPlanSupport.normalizePeriodCsv(
                    req.text("installmentPeriods"),
                    attr.getInstallmentEnabled()
            ));
        }
        if (req.integer("rentToSend") != null) {
            attr.setRentToSend(req.integer("rentToSend"));
        }
        if (req.integer("goodId") != null) {
            attr.setGoodId(req.integer("goodId"));
        }
        applyBuyoutPriceDefault(attr);
        Result pricingCheck = validateAttrPricing(attr);
        if (pricingCheck != null) {
            return pricingCheck;
        }

        attrMapper.updateById(attr);
        miniappCatalogService.evictCatalogCaches();
        return buildAutoSyncResult(attr, attr.getGoodId(), "商品规格修改成功，并已自动同步支付宝商品");
    }

    private void applyBuyoutPriceDefault(Attr attr) {
        if (attr == null) {
            return;
        }
        if (attr.getBuyoutval() != null) {
            return;
        }
        if (attr.getAttrDeposit() != null && attr.getAttrDeposit() > 0) {
            attr.setBuyoutval(attr.getAttrDeposit());
        }
    }

    private Result validateAttrPricing(Attr attr) {
        if (!isPositiveAmount(attr == null ? null : attr.getAttrAmount())) {
            return WebResponseUtil.error(1, "SKU租金必须大于0");
        }
        if (!isPositiveAmount(attr.getAttrDeposit())) {
            return WebResponseUtil.error(1, "SKU押金必须大于0");
        }
        if (!isPositiveAmount(attr.getBuyoutval())) {
            return WebResponseUtil.error(1, "买断金必须大于0");
        }
        return null;
    }

    private boolean isPositiveAmount(Integer amount) {
        return amount != null && amount > 0;
    }

    /**
     * 删除商品规格。
     * 保留旧方法供内部兼容使用，不自动同步支付宝。
     *
     * @param req 请求参数
     * @return 删除结果
     */
    public Result deleteAttr(WebRequest req) {
        Integer attrId = req.integer("attrId");
        if (attrId == null) {
            return WebResponseUtil.error(1, "规格ID不能为空");
        }
        attrMapper.deleteById(attrId);
        miniappCatalogService.evictCatalogCaches();
        return WebResponseUtil.success();
    }

    /**
     * 删除商品规格并自动同步支付宝商品。
     * 兼容前端当前传的 ids 字段，也兼容 attrId 字段。
     *
     * @param req 请求参数
     * @return 删除结果
     */
    public Result deleteAttrAndAutoSync(WebRequest req) {
        Integer attrId = req.integer("attrId");
        if (attrId == null) {
            attrId = req.integer("ids");
        }
        if (attrId == null) {
            return WebResponseUtil.error(1, "规格ID不能为空");
        }
        Attr attr = attrMapper.selectById(attrId);
        if (attr == null) {
            return WebResponseUtil.error(1, "规格不存在");
        }
        attrMapper.deleteById(attrId);
        miniappCatalogService.evictCatalogCaches();
        return buildAutoSyncResult(null, attr.getGoodId(), "商品规格删除成功，并已自动同步支付宝商品");
    }

    /**
     * 手动同步支付宝商品。
     *
     * @param req 请求参数
     * @return 同步结果
     */
    public Result syncGoodsToAlipay(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }
        try {
            Map<String, Object> syncResult = alipayGoodsSyncService.syncAsync(goodId, "manual");
            return WebResponseUtil.success(syncResult);
        } catch (IllegalArgumentException e) {
            return WebResponseUtil.error(1, e.getMessage());
        } catch (IllegalStateException e) {
            return WebResponseUtil.error(1, e.getMessage());
        } catch (Exception e) {
            return WebResponseUtil.error(1, "提交支付宝商品同步任务失败：" + e.getMessage());
        }
    }

    /**
     * 查询支付宝普通商品类目，供中台配置页选择同步类目。
     *
     * @param req 请求参数
     * @return 类目列表
     */
    public Result queryAlipayItemCategories(WebRequest req) {
        try {
            return WebResponseUtil.success(alipayGoodsSyncService.queryItemCategories(
                    req.text("keyword"),
                    req.text("itemType"),
                    req.text("catStatus"),
                    req.intValue("limit", 500)));
        } catch (Exception e) {
            return WebResponseUtil.error(1, "查询支付宝商品类目失败：" + e.getMessage());
        }
    }

    /**
     * 查询支付宝芝麻信用预授权租赁类目，供商品创建和配置中心选择。
     *
     * @param req 请求参数
     * @return 类目列表
     */
    public Result queryAlipayRentCategories(WebRequest req) {
        String keyword = req.text("keyword");
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> option : ALIPAY_RENT_CATEGORY_OPTIONS) {
            if (!StringUtils.hasText(normalizedKeyword)
                    || containsKeyword(option.get("categoryId"), normalizedKeyword)
                    || containsKeyword(option.get("categoryName"), normalizedKeyword)
                    || containsKeyword(option.get("scene"), normalizedKeyword)) {
                rows.add(new LinkedHashMap<>(option));
            }
        }
        return WebResponseUtil.success(rows);
    }

    private boolean containsKeyword(Object value, String normalizedKeyword) {
        if (value == null) {
            return false;
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private static List<Map<String, Object>> buildAlipayRentCategoryOptions() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(alipayRentCategory("RENT_PHONE", "手机", "物品租赁-手机"));
        rows.add(alipayRentCategory("RENT_COMPUTER", "电脑/平板", "物品租赁-电脑/平板"));
        rows.add(alipayRentCategory("RENT_CAMERA", "数码摄像", "物品租赁-数码摄像/相机/无人机"));
        return Collections.unmodifiableList(rows);
    }

    private static Map<String, Object> alipayRentCategory(String categoryId, String categoryName, String scene) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("categoryId", categoryId);
        row.put("categoryName", categoryName);
        row.put("scene", scene);
        return row;
    }

    private static class CategorySelection {
        private final String alipayCategoryId;
        private final String alipayRentCategoryId;

        private CategorySelection(String alipayCategoryId, String alipayRentCategoryId) {
            this.alipayCategoryId = alipayCategoryId;
            this.alipayRentCategoryId = alipayRentCategoryId;
        }

        private String getAlipayCategoryId() {
            return alipayCategoryId;
        }

        private String getAlipayRentCategoryId() {
            return alipayRentCategoryId;
        }
    }

    /**
     * 分页查询指定商品的支付宝同步日志。
     *
     * @param req 请求参数，必须包含 goodId
     * @return 同步日志分页结果
     */
    public Result pageGoodsSyncLogs(WebRequest req) {
        Integer goodId = req.integer("goodId");
        if (goodId == null) {
            return WebResponseUtil.error(1, "商品ID不能为空");
        }
        Page<AlipayGoodsSyncLog> mpPage = new Page<>(req.page(), req.limit());
        QueryWrapper<AlipayGoodsSyncLog> wrapper = new QueryWrapper<>();
        wrapper.eq("good_id", goodId);
        wrapper.orderByDesc("id");
        alipayGoodsSyncLogMapper.selectPage(mpPage, wrapper);
        return WebResponseUtil.page(mpPage.getRecords(), mpPage.getTotal());
    }

    /**
     * 双向同步支付宝商品。
     * 支付宝和本地互相补齐缺失商品/规格；双方都存在时只补支付宝标识与状态字段。
     *
     * @return 同步结果
     */
    public Result syncGoodsBidirectional() {
        try {
            return WebResponseUtil.success(alipayGoodsSyncService.syncBidirectional());
        } catch (IllegalArgumentException e) {
            return WebResponseUtil.error(1, e.getMessage());
        } catch (IllegalStateException e) {
            return WebResponseUtil.error(1, e.getMessage());
        } catch (AlipayApiException e) {
            return WebResponseUtil.error(1, "调用支付宝接口失败：" + e.getErrMsg());
        } catch (Exception e) {
            return WebResponseUtil.error(1, "双向同步支付宝商品失败：" + e.getMessage());
        }
    }

    /**
     * 构造本地下架后同步支付宝远端下架的统一返回。
     *
     * @param goodId 商品ID
     * @return 下架结果
     */
    private Result buildRemoteDelistingResult(Integer goodId) {
        String syncResult = trySyncRemoteDelisting(goodId);
        if ("下架成功".equals(syncResult)) {
            return successWithMessage(null, "商品下架成功，并已同步下架支付宝商品");
        }
        if (syncResult.startsWith("跳过下架：")) {
            return successWithMessage(null, "商品下架成功，" + syncResult.substring("跳过下架：".length()));
        }
        return successWithMessage(null, "商品下架成功，但同步下架支付宝商品失败：" + syncResult.substring("下架失败：".length()));
    }

    /**
     * 同步支付宝侧商品下架。
     * 返回中文标记，供单个下架和批量状态更新复用。
     *
     * @param goodId 商品ID
     * @return 下架结果标记
     */
    private String trySyncRemoteDelisting(Integer goodId) {
        try {
            Map<String, Object> syncResult = alipayGoodsSyncService.syncDelisting(goodId);
            if ("skip".equals(syncResult.get("syncMode"))) {
                return "跳过下架：支付宝侧未找到商品，已跳过远端下架";
            }
            return "下架成功";
        } catch (IllegalArgumentException e) {
            return "下架失败：" + e.getMessage();
        } catch (IllegalStateException e) {
            return "下架失败：" + e.getMessage();
        } catch (AlipayApiException e) {
            return "下架失败：调用支付宝下架接口失败：" + e.getErrMsg();
        } catch (Exception e) {
            return "下架失败：" + e.getMessage();
        }
    }

    /**
     * 本地删除前同步删除支付宝商品。
     * 返回中文标记，便于单个和批量删除复用；远端未找到时允许继续本地删除。
     *
     * @param goodId 商品ID
     * @return 删除结果标记
     */
    private String tryDeleteRemoteBeforeLocalDelete(Integer goodId) {
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            return "跳过删除：商品已不存在";
        }
        if (!StringUtils.hasText(good.getAlipayGoodsId())) {
            return "跳过删除：本地商品未同步支付宝";
        }
        try {
            Map<String, Object> deleteResult = alipayGoodsSyncService.deleteRemoteItem(goodId);
            if ("skip".equals(deleteResult.get("syncMode"))) {
                return "跳过删除：支付宝侧未找到商品";
            }
            return "已同步删除支付宝商品";
        } catch (IllegalArgumentException e) {
            return "删除失败：" + e.getMessage();
        } catch (IllegalStateException e) {
            return "删除失败：" + e.getMessage();
        } catch (AlipayApiException e) {
            return "删除失败：调用支付宝删除接口失败：" + e.getErrMsg();
        } catch (Exception e) {
            return "删除失败：" + e.getMessage();
        }
    }

    /**
     * 构造“本地已保存 + 自动同步支付宝”后的统一返回。
     * 这里优先保证本地保存成功；如果自动同步失败，会把失败原因写到提示语里。
     *
     * @param data 本地保存后的数据
     * @param goodId 商品ID
     * @param successMsg 自动同步成功提示
     * @return 统一返回结果
     */
    private Result buildAutoSyncResult(Object data, Integer goodId, String successMsg) {
        if (goodId == null) {
            return successWithMessage(data, successMsg);
        }

        String syncResult = tryAutoSyncAfterLocalSave(goodId);
        if ("同步已提交".equals(syncResult)) {
            return successWithMessage(data, "本地保存成功，已提交支付宝同步任务，请稍后刷新查看同步日志");
        }
        if ("同步成功".equals(syncResult)) {
            return successWithMessage(data, successMsg);
        }
        if (syncResult.startsWith("跳过同步：")) {
            return successWithMessage(data, "本地保存成功，" + syncResult.substring("跳过同步：".length()));
        }
        if (syncResult.startsWith("同步失败：")) {
            return successWithMessage(data, "本地保存成功，但自动同步支付宝失败：" + syncResult.substring("同步失败：".length()));
        }
        return successWithMessage(data, "本地保存成功，但自动同步支付宝失败：" + syncResult);
    }

    /**
     * 在本地保存成功后尝试自动同步支付宝。
     * 这里返回简单中文标记，便于单个和批量场景复用。
     *
     * @param goodId 商品ID
     * @return 同步结果说明
     */
    private String tryAutoSyncAfterLocalSave(Integer goodId) {
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            return "跳过同步：商品已不存在";
        }
        if (good.getStatus() != 1) {
            return "跳过同步：当前商品为下架状态，暂不自动同步支付宝商品";
        }

        List<Attr> attrs = attrMapper.selectList(new QueryWrapper<Attr>().eq("goods_id", goodId));
        if (attrs == null || attrs.isEmpty()) {
            return "跳过同步：当前商品还没有规格，待添加规格后会自动同步支付宝商品";
        }

        try {
            Map<String, Object> submitResult = alipayGoodsSyncService.syncAsync(goodId, "auto");
            if ("SKIPPED".equals(submitResult.get("syncStatus"))) {
                return "跳过同步：" + submitResult.get("message");
            }
            return "同步已提交";
        } catch (IllegalArgumentException e) {
            return "同步失败：" + e.getMessage();
        } catch (IllegalStateException e) {
            return "同步失败：" + e.getMessage();
        } catch (Exception e) {
            return "同步失败：提交后台任务失败：" + e.getMessage();
        }
    }

    /**
     * 返回带中文提示语的成功结果。
     *
     * @param data 返回数据
     * @param msg 中文提示
     * @return 成功结果
     */
    private Result successWithMessage(Object data, String msg) {
        Result result = new Result();
        result.setCode(0);
        result.setMsg(msg);
        result.setData(data);
        result.setTraceId(org.slf4j.MDC.get("traceId"));
        return result;
    }

    /**
     * 按发布状态分页查询商品。
     *
     * @param req 请求参数
     * @param status 状态值
     * @return 分页结果
     */
    private Result pageGoodsByStatus(WebRequest req, int status) {
        Page<Good> mpPage = new Page<>(req.page(), req.limit());
        QueryWrapper<Good> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status);

        if (req.hasText("goodTitle")) {
            wrapper.like("title", req.text("goodTitle"));
        }
        if (req.hasText("title")) {
            wrapper.like("title", req.text("title"));
        }
        wrapper.orderByDesc("sort_order");

        goodMapper.selectPage(mpPage, wrapper);
        attachLatestSyncLogs(mpPage.getRecords());
        return WebResponseUtil.page(mpPage.getRecords(), mpPage.getTotal());
    }

    /**
     * 批量更新商品上下架状态。
     * 当批量更新为上架态时，会逐个尝试自动同步支付宝，并把结果汇总成中文提示。
     *
     * @param goodIdsText 商品ID字符串，逗号分隔
     * @param targetStatus 目标状态
     * @return 汇总结果
     */
    private Result batchUpdatePublicStatus(String goodIdsText, Integer targetStatus) {
        String[] ids = goodIdsText.split(",");
        int updatedCount = 0;
        int syncSuccessCount = 0;
        int syncSkippedCount = 0;
        int syncFailedCount = 0;
        int delistSuccessCount = 0;
        int delistSkippedCount = 0;
        int delistFailedCount = 0;
        String firstFailedMessage = null;

        for (String idText : ids) {
            String trimmedId = idText == null ? null : idText.trim();
            if (trimmedId == null || trimmedId.isEmpty()) {
                continue;
            }

            Good good = goodMapper.selectById(Integer.valueOf(trimmedId));
            if (good == null) {
                continue;
            }

            applyGoodsStatus(good, targetStatus);
            goodMapper.updateById(good);
            updatedCount++;

            if (targetStatus != 1) {
                String delistResult = trySyncRemoteDelisting(good.getGoodId());
                if ("下架成功".equals(delistResult)) {
                    delistSuccessCount++;
                } else if (delistResult.startsWith("下架失败：")) {
                    delistFailedCount++;
                    if (firstFailedMessage == null) {
                        firstFailedMessage = delistResult.substring("下架失败：".length());
                    }
                } else {
                    delistSkippedCount++;
                }
                continue;
            }

            String syncResult = tryAutoSyncAfterLocalSave(good.getGoodId());
            if ("同步成功".equals(syncResult) || "同步已提交".equals(syncResult)) {
                syncSuccessCount++;
            } else if (syncResult.startsWith("同步失败：")) {
                syncFailedCount++;
                if (firstFailedMessage == null) {
                    firstFailedMessage = syncResult.substring("同步失败：".length());
                }
            } else {
                syncSkippedCount++;
            }
        }

        if (updatedCount == 0) {
            return WebResponseUtil.error(1, "未找到可更新的商品");
        }
        miniappCatalogService.evictCatalogCaches();
        if (targetStatus != 1) {
            StringBuilder messageBuilder = new StringBuilder("商品状态更新成功");
            if (delistSuccessCount > 0) {
                messageBuilder.append("，并已同步下架").append(delistSuccessCount).append("个支付宝商品");
            }
            if (delistSkippedCount > 0) {
                messageBuilder.append("，").append(delistSkippedCount).append("个支付宝商品暂未同步下架");
            }
            if (delistFailedCount > 0) {
                messageBuilder.append("，").append(delistFailedCount).append("个支付宝商品下架失败");
                if (firstFailedMessage != null && !firstFailedMessage.isEmpty()) {
                    messageBuilder.append("，首个失败原因：").append(firstFailedMessage);
                }
            }
            return successWithMessage(null, messageBuilder.toString());
        }

        StringBuilder messageBuilder = new StringBuilder("商品状态更新成功");
        if (syncSuccessCount > 0) {
            messageBuilder.append("，并已提交").append(syncSuccessCount).append("个支付宝商品同步任务");
        }
        if (syncSkippedCount > 0) {
            messageBuilder.append("，").append(syncSkippedCount).append("个商品暂未同步");
        }
        if (syncFailedCount > 0) {
            messageBuilder.append("，").append(syncFailedCount).append("个商品同步失败");
            if (firstFailedMessage != null && !firstFailedMessage.isEmpty()) {
                messageBuilder.append("，首个失败原因：").append(firstFailedMessage);
            }
        }
        return successWithMessage(null, messageBuilder.toString());
    }

    /**
     * 统一归一化商品状态。
     *
     * @param status 状态字段
     * @param ispub 发布字段
     * @return 0 或 1
     */
    private Integer normalizeGoodsStatus(Integer status, Integer ispub) {
        if (status != null) {
            return status > 0 ? 1 : 0;
        }
        if (ispub != null) {
            return ispub == 1 ? 1 : 0;
        }
        return null;
    }

    private Integer normalizeSwitch(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private Integer normalizeDepositFreeSwitch(Integer value) {
        return value != null && value == 1 ? 1 : 2;
    }

    /**
     * 同时更新 status 与 ispub，兼容现有表结构和前端判断。
     *
     * @param good 商品
     * @param status 目标状态
     */
    private void applyGoodsStatus(Good good, int status) {
        good.setStatus(status > 0 ? 1 : 0);
        good.setIspub(status > 0 ? 1 : 2);
    }

    /** 将目录字段与现有商品保存收口在同一事务/操作中。 */
    private void applyCatalogFields(Good good, WebRequest req, boolean creating) {
        if (creating || req.raw().containsKey("goodCover")) {
            String cover = req.text("goodCover");
            if (!StringUtils.hasText(cover) || !cover.trim().startsWith("https://")) {
                throw new com.fly.rent.common.support.RentApiException(4001, "goodCover 必须是完整 HTTPS URL");
            }
            good.setGoodCover(cover.trim());
        }
        if (req.hasText("categoryCode")) {
            good.setCategoryCode(categoryAdminService.requireBindableCategory(req.text("categoryCode")).getCode());
        }
        if (req.raw().containsKey("brand")) {
            good.setBrand(trimToNull(req.text("brand")));
        }
        if (req.raw().containsKey("modelName")) {
            good.setModelName(trimToNull(req.text("modelName")));
        }
        if (req.raw().containsKey("deviceType")) {
            good.setDeviceType(trimToNull(req.text("deviceType")));
        }
        if (req.hasText("defaultRentUnit")) {
            String unit = req.text("defaultRentUnit").trim().toUpperCase();
            if (!"DAY".equals(unit) && !"MONTH".equals(unit)) {
                throw new com.fly.rent.common.support.RentApiException(4001,
                        "defaultRentUnit 仅支持 DAY 或 MONTH");
            }
            good.setDefaultRentUnit(unit);
        }
        if (req.integer("featured") != null) {
            int featured = req.integer("featured");
            if (featured != 0 && featured != 1) {
                throw new com.fly.rent.common.support.RentApiException(4001, "featured 仅支持 0 或 1");
            }
            good.setFeatured(featured);
        }
        if (req.longValue("featuredSort") != null) {
            good.setFeaturedSort(req.longValue("featuredSort"));
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
