package com.fly.rent.web.goods;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayResponse;
import com.alipay.api.FileItem;
import com.alipay.api.domain.AlipayMarketingImagedirectoryCreateModel;
import com.alipay.api.domain.AlipayOpenAppItemAllcategoryQueryModel;
import com.alipay.api.domain.AlipayOpenAppItemCalendarstockSaveModel;
import com.alipay.api.domain.AlipayOpenAppItemCreateModel;
import com.alipay.api.domain.AlipayOpenAppItemDeleteModel;
import com.alipay.api.domain.AlipayOpenAppItemDirectModifyModel;
import com.alipay.api.domain.AlipayOpenAppItemListQueryModel;
import com.alipay.api.domain.AlipayOpenAppItemModifyModel;
import com.alipay.api.domain.AlipayOpenAppItemQueryModel;
import com.alipay.api.domain.AppItemCalendarStock;
import com.alipay.api.domain.AppItemAttrVO;
import com.alipay.api.domain.AppItemSkuCalendarStock;
import com.alipay.api.domain.AppxCategoryVO;
import com.alipay.api.domain.CategoryAndParentVO;
import com.alipay.api.domain.GuideInfoVO;
import com.alipay.api.domain.ItemDescInfoVO;
import com.alipay.api.domain.ItemDirectModifySku;
import com.alipay.api.domain.ItemRiskInfo;
import com.alipay.api.domain.ItemSceneRiskInfo;
import com.alipay.api.domain.ItemSkuAttrVO;
import com.alipay.api.domain.ItemSkuCreateVO;
import com.alipay.api.domain.ItemSkuIdPair;
import com.alipay.api.domain.ItemSkuSearchVO;
import com.alipay.api.domain.ItemSkuVO;
import com.alipay.api.domain.ItemSpuVO;
import com.alipay.api.domain.Reasons;
import com.alipay.api.internal.util.json.JSONWriter;
import com.alipay.api.request.AlipayMarketingImageEnhanceUploadRequest;
import com.alipay.api.request.AlipayMarketingImagedirectoryCreateRequest;
import com.alipay.api.request.AlipayOpenAppItemAllcategoryQueryRequest;
import com.alipay.api.request.AlipayOpenAppItemCalendarstockSaveRequest;
import com.alipay.api.request.AlipayOpenAppItemCreateRequest;
import com.alipay.api.request.AlipayOpenAppItemDeleteRequest;
import com.alipay.api.request.AlipayOpenAppItemDirectModifyRequest;
import com.alipay.api.request.AlipayOpenAppItemListQueryRequest;
import com.alipay.api.request.AlipayOpenAppItemModifyRequest;
import com.alipay.api.request.AlipayOpenAppItemQueryRequest;
import com.alipay.api.response.AlipayMarketingImageEnhanceUploadResponse;
import com.alipay.api.response.AlipayMarketingImagedirectoryCreateResponse;
import com.alipay.api.response.AlipayOpenAppItemAllcategoryQueryResponse;
import com.alipay.api.response.AlipayOpenAppItemCalendarstockSaveResponse;
import com.alipay.api.response.AlipayOpenAppItemCreateResponse;
import com.alipay.api.response.AlipayOpenAppItemDeleteResponse;
import com.alipay.api.response.AlipayOpenAppItemDirectModifyResponse;
import com.alipay.api.response.AlipayOpenAppItemListQueryResponse;
import com.alipay.api.response.AlipayOpenAppItemModifyResponse;
import com.alipay.api.response.AlipayOpenAppItemQueryResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.entity.AlipayGoodsSyncLog;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.mapper.AlipayGoodsSyncLogMapper;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.GoodMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 支付宝商品同步服务。
 *
 * <p>这里把商品同步拆成独立服务，统一处理：
 * 1. 本地商品/规格校验
 * 2. 封面图、轮播图、详情图、规格图上传到支付宝
 * 3. 商品创建或修改
 * 4. 支付宝商品 ID / SKU ID 回写到本地
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayGoodsSyncService {

    /** 富文本图片提取规则。 */
    private static final Pattern HTML_IMAGE_PATTERN =
            Pattern.compile("<img[^>]+src\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE);
    /** 富文本标签清理规则。 */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    /** 多空白字符压缩规则。 */
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    /** 下载图片连接超时。 */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    /** 下载图片读取超时。 */
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    /** 支付宝轮播图最多 3 张。 */
    private static final int MAX_ALIPAY_BANNER_COUNT = 3;
    /** 支付宝详情图最多 10 张。 */
    private static final int MAX_ALIPAY_DETAIL_IMAGE_COUNT = 10;
    /** 支付宝 SKU 库存上限。 */
    private static final long MAX_STOCK_NUM = 99_999L;
    /** 支付宝商品分页拉取每页数量。 */
    private static final long REMOTE_LIST_PAGE_SIZE = 50L;
    /** 支付宝普通商品日历库存最大同步天数。 */
    private static final int CALENDAR_STOCK_DAYS = 120;
    /** 支付宝租赁类目要求商品级起租天数不能低于 91 天。 */
    private static final long ALIPAY_MIN_RENT_FROM_NUMBERS_OF_DAY = 91L;
    private static final DateTimeFormatter CALENDAR_STOCK_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 批量同步并发数，避免一次性打爆支付宝 OpenAPI。 */
    private static final int SYNC_THREAD_POOL_SIZE =
            Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
    /** 单商品图片上传并发数，控制图片下载和支付宝图片上传的并行度。 */
    private static final int IMAGE_UPLOAD_THREAD_POOL_SIZE =
            Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors()));
    /** 支付宝商品主图、轮播图和 SKU 图要求 750x750。 */
    private static final int ALIPAY_SQUARE_IMAGE_SIZE = 750;
    /** 支付宝图片上传单文件大小限制 5M。 */
    private static final int ALIPAY_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
    /** 详情图上传前限制长边，避免超大原图触发支付宝多媒体校验。 */
    private static final int ALIPAY_DETAIL_IMAGE_MAX_LONG_SIDE = 3000;
    /** 支付宝图片上传最大重试次数，用于处理偶发 Read timed out。 */
    private static final int IMAGE_UPLOAD_MAX_ATTEMPTS = 3;
    /** 支付宝图片上传重试基础等待时间。 */
    private static final long IMAGE_UPLOAD_RETRY_BACKOFF_MILLIS = 800L;
    /** 商品同步后台任务并发数，避免后台点击后占满 Tomcat 请求线程。 */
    private static final int ASYNC_SYNC_THREAD_POOL_SIZE =
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
    /** 同步日志状态：已提交。 */
    private static final String SYNC_STATUS_QUEUED = "QUEUED";
    /** 同步日志状态：执行中。 */
    private static final String SYNC_STATUS_RUNNING = "RUNNING";
    /** 同步日志状态：成功。 */
    private static final String SYNC_STATUS_SUCCESS = "SUCCESS";
    /** 同步日志状态：失败。 */
    private static final String SYNC_STATUS_FAILED = "FAILED";
    /** 同步日志状态：跳过。 */
    private static final String SYNC_STATUS_SKIPPED = "SKIPPED";
    /** 支付宝商品类型：租赁。 */
    private static final String ITEM_TYPE_RENT = "2";
    /** 支付宝长租模式允许的租期天数。 */
    private static final List<Long> ALIPAY_LONG_RENT_DURATIONS =
            Collections.unmodifiableList(Arrays.asList(120L, 180L, 270L, 360L, 365L, 455L, 545L, 720L, 730L));
    /** 默认只展示商户已通过审核、可以创建商品的支付宝类目。 */
    private static final String DEFAULT_ITEM_CATEGORY_STATUS = "AUDIT_PASSED";
    /** 支付宝 SKU 类型：租赁。 */
    private static final String SKU_TYPE_RENT = "RENT";
    /** 支付宝租赁商品价格单位。 */
    private static final String PRICE_UNIT_PER_DAY = "元/日";
    private static final String PRICE_UNIT_YUAN = "元";
    private static final String DURATION_UNIT_DAY = "日";
    /** 支付宝图片空间根目录。 */
    private static final String ROOT_IMAGE_DIRECTORY_ID = "0";
    /** 商品图片增强上传场景。 */
    private static final String IMAGE_SCENE_HEAD = "ITEM_HEAD_IMG";
    private static final String IMAGE_SCENE_LIST = "ITEM_IMAGE_LIST";
    private static final String IMAGE_SCENE_SKU_THUMB = "ITEM_SKU_THUMB_IMG";
    private static final String IMAGE_SCENE_DESC = "ITEM_DESCINFO_IMG";

    private final GoodMapper goodMapper;
    private final AttrMapper attrMapper;
    private final AlipayGoodsSyncLogMapper alipayGoodsSyncLogMapper;
    private final AlipayClientService alipayClientService;
    private final AlipayPlatformConfigService alipayPlatformConfigService;
    /** 商品同步后台任务线程池。 */
    private final ExecutorService asyncSyncExecutor = Executors.newFixedThreadPool(ASYNC_SYNC_THREAD_POOL_SIZE);
    /** 当前正在执行同步的商品 ID，用于避免同一商品重复提交。 */
    private final Set<Integer> runningSyncGoodIds = ConcurrentHashMap.newKeySet();

    /**
     * 异步提交商品同步任务。
     *
     * @param goodId 商品 ID
     * @param triggerType 触发来源，例如 manual 或 auto
     * @return 任务提交结果，包含日志 ID、任务 ID 和状态
     */
    public Map<String, Object> syncAsync(Integer goodId, String triggerType) {
        requireGood(goodId);
        if (!runningSyncGoodIds.add(goodId)) {
            AlipayGoodsSyncLog skippedLog = createSyncLog(goodId, triggerType, SYNC_STATUS_SKIPPED,
                    "已有同步任务正在执行，本次不重复提交");
            finishSyncLog(skippedLog, SYNC_STATUS_SKIPPED, null, null,
                    "已有同步任务正在执行，本次不重复提交", null, 0L);
            return buildAsyncSubmitResult(skippedLog, "已有同步任务正在执行，请稍后刷新同步日志");
        }

        AlipayGoodsSyncLog syncLog = createSyncLog(goodId, triggerType, SYNC_STATUS_QUEUED, "已提交后台同步任务");
        asyncSyncExecutor.submit(() -> runLoggedSyncTask(goodId, syncLog.getId()));
        return buildAsyncSubmitResult(syncLog, "已提交后台同步任务，请稍后刷新查看同步日志");
    }

    /**
     * 执行带日志记录的商品同步任务。
     *
     * @param goodId 商品 ID
     * @param logId 同步日志 ID
     */
    private void runLoggedSyncTask(Integer goodId, Long logId) {
        long startTime = System.currentTimeMillis();
        startSyncLog(logId);
        try {
            Map<String, Object> syncResult = sync(goodId);
            long durationMs = System.currentTimeMillis() - startTime;
            String syncMode = valueText(syncResult.get("syncMode"));
            String itemId = valueText(syncResult.get("itemId"));
            finishSyncLog(logId, SYNC_STATUS_SUCCESS, syncMode, itemId,
                    buildSuccessLogMessage(syncResult, durationMs), serializeSyncResult(syncResult), durationMs);
        } catch (GoodsSyncTaskException ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            String message = buildFailureLogMessage("同步支付宝商品失败", ex.getCause(), ex.getCauseMessage());
            finishSyncLog(logId, SYNC_STATUS_FAILED, null, null,
                    message, serializeSyncResult(ex.toDetailResult(goodId)), durationMs);
            log.error("异步同步支付宝商品失败，goodId={}", goodId, ex.getCause());
        } catch (AlipayApiException ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            String message = buildFailureLogMessage("调用支付宝接口失败", ex, firstText(ex.getErrMsg(), ex.getMessage()));
            finishSyncLog(logId, SYNC_STATUS_FAILED, null, null, message, null, durationMs);
            log.error("异步同步支付宝商品失败，goodId={}", goodId, ex);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            String message = buildFailureLogMessage("同步支付宝商品失败", ex, ex.getMessage());
            finishSyncLog(logId, SYNC_STATUS_FAILED, null, null, message, null, durationMs);
            log.error("异步同步支付宝商品失败，goodId={}", goodId, ex);
        } finally {
            runningSyncGoodIds.remove(goodId);
        }
    }

    /**
     * 同步服务销毁时关闭后台任务线程池。
     */
    @PreDestroy
    public void destroy() {
        shutdownExecutor(asyncSyncExecutor);
    }

    /**
     * 创建商品同步日志。
     *
     * @param goodId 商品 ID
     * @param triggerType 触发来源
     * @param syncStatus 初始状态
     * @param message 初始日志
     * @return 已入库的同步日志
     */
    private AlipayGoodsSyncLog createSyncLog(Integer goodId, String triggerType, String syncStatus, String message) {
        Date now = new Date();
        AlipayGoodsSyncLog syncLog = new AlipayGoodsSyncLog();
        syncLog.setGoodId(goodId);
        syncLog.setTaskId(UUID.randomUUID().toString());
        syncLog.setTriggerType(StringUtils.hasText(triggerType) ? triggerType : "manual");
        syncLog.setSyncStatus(syncStatus);
        syncLog.setMessage(limitLength(message, 1000));
        syncLog.setCreatedAt(now);
        syncLog.setUpdatedAt(now);
        alipayGoodsSyncLogMapper.insert(syncLog);
        return syncLog;
    }

    /**
     * 标记同步日志开始执行。
     *
     * @param logId 同步日志 ID
     */
    private void startSyncLog(Long logId) {
        Date now = new Date();
        AlipayGoodsSyncLog syncLog = new AlipayGoodsSyncLog();
        syncLog.setId(logId);
        syncLog.setSyncStatus(SYNC_STATUS_RUNNING);
        syncLog.setMessage("同步任务执行中");
        syncLog.setStartedAt(now);
        syncLog.setUpdatedAt(now);
        alipayGoodsSyncLogMapper.updateById(syncLog);
    }

    /**
     * 更新同步日志为最终状态。
     *
     * @param logId 同步日志 ID
     * @param syncStatus 最终状态
     * @param syncMode 商品同步模式
     * @param alipayItemId 支付宝商品 ID
     * @param message 日志内容
     * @param detailJson 同步详情 JSON
     * @param durationMs 耗时毫秒数
     */
    private void finishSyncLog(
            Long logId,
            String syncStatus,
            String syncMode,
            String alipayItemId,
            String message,
            String detailJson,
            Long durationMs
    ) {
        Date now = new Date();
        AlipayGoodsSyncLog syncLog = new AlipayGoodsSyncLog();
        syncLog.setId(logId);
        syncLog.setSyncStatus(syncStatus);
        syncLog.setSyncMode(syncMode);
        syncLog.setAlipayItemId(alipayItemId);
        syncLog.setMessage(message);
        syncLog.setDetailJson(detailJson);
        syncLog.setFinishedAt(now);
        syncLog.setDurationMs(durationMs);
        syncLog.setUpdatedAt(now);
        alipayGoodsSyncLogMapper.updateById(syncLog);
    }

    /**
     * 更新同步日志为最终状态。
     *
     * @param syncLog 同步日志
     * @param syncStatus 最终状态
     * @param syncMode 商品同步模式
     * @param alipayItemId 支付宝商品 ID
     * @param message 日志内容
     * @param detailJson 同步详情 JSON
     * @param durationMs 耗时毫秒数
     */
    private void finishSyncLog(
            AlipayGoodsSyncLog syncLog,
            String syncStatus,
            String syncMode,
            String alipayItemId,
            String message,
            String detailJson,
            Long durationMs
    ) {
        finishSyncLog(syncLog.getId(), syncStatus, syncMode, alipayItemId, message, detailJson, durationMs);
    }

    /**
     * 构造异步同步提交结果。
     *
     * @param syncLog 同步日志
     * @param message 返回提示
     * @return 接口返回结构
     */
    private Map<String, Object> buildAsyncSubmitResult(AlipayGoodsSyncLog syncLog, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodId", syncLog.getGoodId());
        result.put("logId", syncLog.getId());
        result.put("taskId", syncLog.getTaskId());
        result.put("syncStatus", syncLog.getSyncStatus());
        result.put("message", message);
        return result;
    }

    /** 拼接同步失败原因与完整 Java 堆栈，便于后台直接排查真实失败点。 */
    private String buildFailureLogMessage(String prefix, Throwable throwable, String reason) {
        String message = StringUtils.hasText(reason)
                ? reason
                : throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        String stack = throwable == null ? "" : ExceptionUtils.getStackTrace(throwable);
        return prefix + "：" + message + "\n堆栈:\n" + stack;
    }

    /**
     * 构造同步成功日志。
     *
     * @param syncMode 同步模式
     * @param itemId 支付宝商品 ID
     * @param durationMs 耗时毫秒数
     * @return 成功日志内容
     */
    private String buildSuccessLogMessage(String syncMode, String itemId, long durationMs) {
        return "商品同步成功，模式=" + firstText(syncMode, "-")
                + "，支付宝商品ID=" + firstText(itemId, "-")
                + "，耗时=" + durationMs + "ms";
    }

    /**
     * 构造带日历库存状态的同步完成摘要。
     */
    private String buildSuccessLogMessage(Map<String, Object> syncResult, long durationMs) {
        String syncMode = valueText(syncResult.get("syncMode"));
        String itemId = valueText(syncResult.get("itemId"));
        String calendarStatus = valueText(syncResult.get("calendarStockSyncStatus"));
        String calendarError = valueText(syncResult.get("calendarStockError"));
        String message = buildSuccessLogMessage(syncMode, itemId, durationMs);
        if ("FAILED".equals(calendarStatus)) {
            message += "；日历库存同步失败：" + firstText(calendarError, valueText(syncResult.get("calendarStockMessage")));
        } else if ("SUCCESS".equals(calendarStatus)) {
            message += "；日历库存同步成功";
        }
        return message;
    }

    /**
     * 序列化同步结果，便于后台追踪每次任务的返回详情。
     *
     * @param syncResult 同步结果
     * @return JSON 字符串
     */
    private String serializeSyncResult(Map<String, Object> syncResult) {
        try {
            return new JSONWriter().write(syncResult, true);
        } catch (Exception ex) {
            log.warn("序列化支付宝商品同步结果失败", ex);
            return null;
        }
    }

    /**
     * 读取最近一次成功同步沉淀的图片上传映射，避免同一源图在后续普通修改时重复上传。
     */
    private Map<String, String> loadImageUploadCache(Integer goodId) {
        if (goodId == null || alipayGoodsSyncLogMapper == null) {
            return Collections.emptyMap();
        }
        try {
            AlipayGoodsSyncLog syncLog = alipayGoodsSyncLogMapper.selectOne(new QueryWrapper<AlipayGoodsSyncLog>()
                    .eq("good_id", goodId)
                    .eq("sync_status", SYNC_STATUS_SUCCESS)
                    .isNotNull("detail_json")
                    .orderByDesc("finished_at")
                    .last("LIMIT 1"));
            if (syncLog == null || !StringUtils.hasText(syncLog.getDetailJson())) {
                return Collections.emptyMap();
            }
            JSONObject detail = JSON.parseObject(syncLog.getDetailJson());
            JSONObject cacheJson = detail.getJSONObject("imageUploadCache");
            if (cacheJson == null || cacheJson.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, String> cache = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : cacheJson.entrySet()) {
                String key = entry.getKey();
                String value = valueText(entry.getValue());
                if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                    cache.put(key, value);
                }
            }
            return cache;
        } catch (Exception ex) {
            log.warn("读取支付宝商品图片上传缓存失败，goodId={}", goodId, ex);
            return Collections.emptyMap();
        }
    }

    /**
     * 安全转换对象为字符串。
     *
     * @param value 原始值
     * @return 字符串，空值返回 null
     */
    private String valueText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 构造一次商品同步任务的业务生命周期。
     */
    private List<Map<String, Object>> buildGoodsSyncLifecycle() {
        List<Map<String, Object>> lifecycle = new ArrayList<>();
        lifecycle.add(newLifecycleStep("local_validate", "本地数据校验"));
        lifecycle.add(newLifecycleStep("query_item", "查询支付宝商品"));
        lifecycle.add(newLifecycleStep("direct_modify", "免审更新商品"));
        lifecycle.add(newLifecycleStep("modify", "普通修改商品"));
        lifecycle.add(newLifecycleStep("create", "创建商品"));
        lifecycle.add(newLifecycleStep("calendar_stock", "同步日历库存"));
        lifecycle.add(newLifecycleStep("persist_mapping", "回写本地映射"));
        return lifecycle;
    }

    /**
     * 新建一个未开始的生命周期节点。
     */
    private Map<String, Object> newLifecycleStep(String code, String stepName) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("code", code);
        step.put("stepName", stepName);
        step.put("status", "PENDING");
        step.put("message", "未开始");
        return step;
    }

    /**
     * 更新生命周期节点状态。
     */
    private void markLifecycleStep(
            List<Map<String, Object>> lifecycle,
            String code,
            String status,
            String message,
            Long durationMs
    ) {
        for (Map<String, Object> step : lifecycle) {
            if (!Objects.equals(code, step.get("code"))) {
                continue;
            }
            step.put("status", status);
            step.put("message", message);
            if ("RUNNING".equals(status)) {
                step.put("startedAt", new Date());
            } else {
                if (!step.containsKey("startedAt")) {
                    step.put("startedAt", new Date());
                }
                step.put("finishedAt", new Date());
            }
            if (durationMs != null) {
                step.put("durationMs", durationMs);
            }
            return;
        }
    }

    /**
     * 商品资料同步三种模式互斥，未选中的模式展示为跳过。
     */
    private void markItemOperationSteps(List<Map<String, Object>> lifecycle, String selectedCode) {
        for (String code : Arrays.asList("direct_modify", "modify", "create")) {
            if (!Objects.equals(code, selectedCode)) {
                markLifecycleStep(lifecycle, code, "SKIPPED", "本次同步不走该商品资料链路", null);
            }
        }
    }

    /**
     * 将同步模式映射到生命周期节点编码。
     */
    private String lifecycleCodeForSyncMode(String syncMode) {
        if ("create".equals(syncMode)) {
            return "create";
        }
        if ("modify".equals(syncMode)) {
            return "modify";
        }
        if (syncMode != null && syncMode.startsWith("direct_modify")) {
            return "direct_modify";
        }
        return syncMode;
    }

    /**
     * 失败时把当前正在执行的生命周期节点标为失败，后续节点保持未开始。
     */
    private void markRunningLifecycleFailed(List<Map<String, Object>> lifecycle, Exception ex) {
        String message = ex == null ? "unknown" : ex.getMessage();
        for (Map<String, Object> step : lifecycle) {
            if ("RUNNING".equals(step.get("status"))) {
                step.put("status", "FAILED");
                step.put("message", message);
                step.put("finishedAt", new Date());
                return;
            }
        }
    }

    /**
     * 同步一个商品到支付宝。
     *
     * @param goodId 本地商品 ID
     * @return 同步结果摘要
     * @throws AlipayApiException 支付宝 SDK 调用异常
     */
    public Map<String, Object> sync(Integer goodId) throws AlipayApiException {
        Good good = requireGood(goodId);
        List<Attr> attrs = requireAttrs(goodId);
        List<Map<String, Object>> lifecycle = buildGoodsSyncLifecycle();
        markLifecycleStep(lifecycle, "local_validate", "SUCCESS",
                "商品ID=" + good.getGoodId() + "，规格数=" + attrs.size(), null);

        try {
            // 先按稳定的外部商品 ID 查一次远端商品，避免历史漏回写导致重复创建。
            markLifecycleStep(lifecycle, "query_item", "RUNNING", "开始查询支付宝商品", null);
            String outItemId = buildOutItemId(good);
            String remoteItemId = null;
            if (StringUtils.hasText(good.getAlipayGoodsId())) {
                AlipayOpenAppItemQueryResponse mappedResponse = queryRemoteItemByItemIdPreferOnline(good.getAlipayGoodsId().trim());
                if (mappedResponse != null && mappedResponse.isSuccess() && StringUtils.hasText(mappedResponse.getItemId())) {
                    remoteItemId = mappedResponse.getItemId();
                }
            }
            if (!StringUtils.hasText(remoteItemId)) {
                remoteItemId = queryRemoteItemId(outItemId);
            }
            AlipayOpenAppItemQueryResponse remoteDetail = null;
            AlipayOpenAppItemQueryResponse onlineRemoteDetail = null;
            if (StringUtils.hasText(remoteItemId)) {
                onlineRemoteDetail = queryRemoteItemByItemId(remoteItemId, null);
                remoteDetail = onlineRemoteDetail;
                if (remoteDetail == null || !remoteDetail.isSuccess()) {
                    remoteDetail = queryRemoteItemByItemId(remoteItemId, 1L);
                }
                if (remoteDetail != null && remoteDetail.isSuccess()) {
                    if (StringUtils.hasText(remoteDetail.getItemId())) {
                        remoteItemId = remoteDetail.getItemId();
                    }
                    patchLocalGoodRemoteState(good, remoteDetail);
                    if (pullRemoteSkus(good, remoteDetail) > 0) {
                        attrs = requireAttrs(goodId);
                    }
                } else {
                    log.warn("查询支付宝商品详情失败，跳过 SKU ID 补齐，itemId={}, subCode={}, subMsg={}",
                            remoteItemId,
                            remoteDetail == null ? null : remoteDetail.getSubCode(),
                            remoteDetail == null ? null : remoteDetail.getSubMsg());
                }
            }
            markLifecycleStep(lifecycle, "query_item", StringUtils.hasText(remoteItemId) ? "SUCCESS" : "SKIPPED",
                    StringUtils.hasText(remoteItemId)
                            ? "已找到支付宝商品，itemId=" + remoteItemId
                            : "支付宝侧未找到商品，将按新商品创建",
                    null);

            SyncConfig config = loadSyncConfig(good, attrs);
            SyncedImageBundle imageBundle = new SyncedImageBundle();
            Map<String, String> uploadCache = new ConcurrentHashMap<>(loadImageUploadCache(goodId));
            ItemUpsertResult itemResult;
            if (StringUtils.hasText(remoteItemId)
                    && onlineRemoteDetail != null
                    && onlineRemoteDetail.isSuccess()
                    && !hasLocalCategoryChangedFromRemote(good, onlineRemoteDetail)
                    && canDirectModifyMiniappItem(attrs, onlineRemoteDetail)) {
                // 免审更新不需要商品图文，跳过图片上传可以显著减少耗时和图片接口超时。
                markItemOperationSteps(lifecycle, "direct_modify");
                markLifecycleStep(lifecycle, "direct_modify", "RUNNING", "开始免审更新支付宝商品", null);
                itemResult = directModifyMiniappItem(good, attrs, config, remoteItemId, onlineRemoteDetail);
            } else {
                String selectedItemStep = StringUtils.hasText(remoteItemId) ? "modify" : "create";
                markItemOperationSteps(lifecycle, selectedItemStep);
                markLifecycleStep(lifecycle, selectedItemStep, "RUNNING",
                        StringUtils.hasText(remoteItemId) ? "开始准备普通修改支付宝商品" : "开始准备创建支付宝商品", null);
                imageBundle = syncImages(good, attrs, config, uploadCache);
                if (StringUtils.hasText(remoteItemId)) {
                    itemResult = modifyMiniappItem(good, attrs, config, imageBundle, remoteItemId, remoteDetail);
                } else {
                    itemResult = createMiniappItem(good, attrs, config, imageBundle);
                }
            }
            markLifecycleStep(lifecycle, lifecycleCodeForSyncMode(itemResult.getSyncMode()), "SUCCESS",
                    "支付宝商品资料同步成功，itemId=" + firstText(itemResult.getItemId(), "-"),
                    null);
            markLifecycleStep(lifecycle, "persist_mapping", "RUNNING", "开始回写本地映射", null);
            persistItemMapping(good, itemResult);
            persistSkuMappings(attrs, itemResult.getSkuPairs());
            markLifecycleStep(lifecycle, "persist_mapping", "SUCCESS", "已回写支付宝商品/SKU标识", null);
            if (itemResult.getCalendarStockResult() != null) {
                markLifecycleStep(lifecycle, "calendar_stock",
                        itemResult.getCalendarStockResult().getStatus(),
                        itemResult.getCalendarStockResult().getMessage(),
                        itemResult.getCalendarStockResult().getDurationMs());
            } else {
                markLifecycleStep(lifecycle, "calendar_stock", "SKIPPED", "本次商品同步未触发日历库存同步", null);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("goodId", good.getGoodId());
            result.put("outItemId", outItemId);
            result.put("itemId", itemResult.getItemId());
            result.put("syncMode", itemResult.getSyncMode());
            result.put("headImg", imageBundle.getHeadImg());
            result.put("bannerCount", imageBundle.getImageList().size());
            result.put("detailImageCount", imageBundle.getDescImgs().size());
            result.put("imageUploadCache", new LinkedHashMap<>(uploadCache));
            result.put("skuCount", attrs.size());
            result.put("alipayAuditStatus", good.getAlipayAuditStatus());
            result.put("alipaySpuStatus", good.getAlipaySpuStatus());
            result.put("alipayStatusReason", good.getAlipayStatusReason());
            result.put("alipayStatusUpdatedAt", good.getAlipayStatusUpdatedAt());
            if (itemResult.getCalendarStockResult() != null) {
                result.put("calendarStockSyncStatus", itemResult.getCalendarStockResult().getStatus());
                result.put("calendarStockMessage", itemResult.getCalendarStockResult().getMessage());
                result.put("calendarStockError", itemResult.getCalendarStockResult().getErrorMessage());
            }
            result.put("lifecycle", lifecycle);
            return result;
        } catch (Exception ex) {
            markRunningLifecycleFailed(lifecycle, ex);
            throw new GoodsSyncTaskException(ex, lifecycle);
        }
    }

    /**
     * 查询支付宝普通商品开放类目，供后台选择商品同步 categoryId。
     *
     * @param keyword 类目名称或 ID 关键字
     * @param itemType 商品类型，当前租赁实物商品默认 2
     * @param catStatus 类目状态过滤，留空时由支付宝返回全部可见类目
     * @param limit 返回上限
     * @return 扁平化类目列表
     * @throws AlipayApiException 支付宝 SDK 异常
     */
    public List<Map<String, Object>> queryItemCategories(
            String keyword,
            String itemType,
            String catStatus,
            int limit
    ) throws AlipayApiException {
        AlipayOpenAppItemAllcategoryQueryModel model = new AlipayOpenAppItemAllcategoryQueryModel();
        model.setItemType(StringUtils.hasText(itemType) ? itemType.trim() : ITEM_TYPE_RENT);
        String resolvedCatStatus = StringUtils.hasText(catStatus) ? catStatus.trim() : DEFAULT_ITEM_CATEGORY_STATUS;
        if (shouldFilterCategoryStatus(resolvedCatStatus)) {
            model.setCatStatus(resolvedCatStatus);
        }

        AlipayOpenAppItemAllcategoryQueryRequest request = new AlipayOpenAppItemAllcategoryQueryRequest();
        request.setBizModel(model);
        AlipayOpenAppItemAllcategoryQueryResponse response = alipayClientService.execute(request);
        ensureAlipaySuccess(response, "查询支付宝商品类目失败");

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        int maxSize = Math.min(Math.max(limit, 1), 500);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (response == null || CollectionUtils.isEmpty(response.getCats())) {
            return rows;
        }

        for (CategoryAndParentVO categoryPath : response.getCats()) {
            Map<String, Object> row = buildCategoryRow(categoryPath);
            if (!row.isEmpty()
                    && matchesCategoryKeyword(row, normalizedKeyword)
                    && matchesCategoryStatus(row, resolvedCatStatus)) {
                rows.add(row);
            }
        }
        rows.sort((left, right) -> valueText(left.get("fullPathName")).compareTo(valueText(right.get("fullPathName"))));
        if (rows.size() <= maxSize) {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, maxSize));
    }

    /**
     * 将支付宝返回的一条叶子类目路径压平成前端可选项。
     */
    private Map<String, Object> buildCategoryRow(CategoryAndParentVO categoryPath) {
        if (categoryPath == null || CollectionUtils.isEmpty(categoryPath.getCatAndParent())) {
            return Collections.emptyMap();
        }
        List<AppxCategoryVO> path = categoryPath.getCatAndParent().stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> Long.compare(
                        left.getCatLevel() == null ? 0L : left.getCatLevel(),
                        right.getCatLevel() == null ? 0L : right.getCatLevel()))
                .collect(Collectors.toList());
        if (path.isEmpty()) {
            return Collections.emptyMap();
        }
        AppxCategoryVO leaf = path.get(path.size() - 1);
        if (!StringUtils.hasText(leaf.getCatId())) {
            return Collections.emptyMap();
        }
        List<String> names = path.stream()
                .map(AppxCategoryVO::getCatName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("categoryId", leaf.getCatId());
        row.put("categoryName", leaf.getCatName());
        row.put("fullPathName", String.join(" / ", names));
        row.put("categoryLevel", leaf.getCatLevel());
        row.put("parentCategoryId", leaf.getfCatId());
        row.put("catStatus", leaf.getCatStatus());
        row.put("itemTypes", leaf.getItemTypes());
        row.put("rootCategoryName", names.isEmpty() ? "" : names.get(0));
        return row;
    }

    private boolean matchesCategoryKeyword(Map<String, Object> row, String normalizedKeyword) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return true;
        }
        return valueText(row.get("categoryId")).toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || valueText(row.get("categoryName")).toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || valueText(row.get("fullPathName")).toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private boolean matchesCategoryStatus(Map<String, Object> row, String resolvedCatStatus) {
        if (!shouldFilterCategoryStatus(resolvedCatStatus)) {
            return true;
        }
        return resolvedCatStatus.equalsIgnoreCase(valueText(row.get("catStatus")));
    }

    private boolean shouldFilterCategoryStatus(String catStatus) {
        return StringUtils.hasText(catStatus) && !"ALL".equalsIgnoreCase(catStatus.trim());
    }

    /**
     * 双向同步支付宝商品。
     *
     * <p>同步原则：
     * 1. 支付宝已有、本地没有：拉取并新增本地商品及规格，只填本地必须字段和支付宝标识字段；
     * 2. 本地已有、支付宝没有：按现有本地数据创建支付宝商品；
     * 3. 两边都有：不覆盖原有业务字段，只补齐新增的支付宝标识、审核状态和 SPU 状态字段。</p>
     *
     * @return 双向同步结果摘要
     * @throws AlipayApiException 支付宝 SDK 调用异常
     */
    public Map<String, Object> syncBidirectional() throws AlipayApiException {
        List<ItemSpuVO> remoteItems = listRemoteItems();
        int pulledGoodsCount = 0;
        int pulledSkuCount = 0;
        int patchedLocalCount = 0;
        int remoteFailedCount = 0;
        int pushedGoodsCount = 0;
        int skippedPushCount = 0;
        int skippedPulledRemoteCount = 0;
        int pushFailedCount = 0;
        Set<Integer> pulledRemoteGoodIds = new HashSet<>();
        List<String> failures = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(SYNC_THREAD_POOL_SIZE);
        try {
            List<Future<RemotePullResult>> remoteFutures = new ArrayList<>();
            for (ItemSpuVO remoteItem : remoteItems) {
                remoteFutures.add(executor.submit(() -> pullRemoteItem(remoteItem)));
            }
            for (Future<RemotePullResult> future : remoteFutures) {
                try {
                    RemotePullResult pullResult = future.get();
                    if (pullResult.isCreatedGood()) {
                        pulledGoodsCount++;
                        if (pullResult.getGoodId() != null) {
                            pulledRemoteGoodIds.add(pullResult.getGoodId());
                        }
                    }
                    pulledSkuCount += pullResult.getCreatedSkuCount();
                    if (pullResult.isPatchedLocal()) {
                        patchedLocalCount++;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("双向同步被中断", ex);
                } catch (ExecutionException ex) {
                    remoteFailedCount++;
                    failures.add(buildFailureMessage("pull", ex.getCause()));
                    log.warn("拉取支付宝商品失败", ex.getCause());
                }
            }

            List<Good> localGoods = goodMapper.selectList(new QueryWrapper<>());
            List<Future<LocalPushResult>> pushFutures = new ArrayList<>();
            for (Good good : localGoods) {
                pushFutures.add(executor.submit(() -> pushLocalGood(good, pulledRemoteGoodIds)));
            }
            for (Future<LocalPushResult> future : pushFutures) {
                try {
                    LocalPushResult pushResult = future.get();
                    pushedGoodsCount += pushResult.getPushedCount();
                    skippedPushCount += pushResult.getSkippedCount();
                    skippedPulledRemoteCount += pushResult.getSkippedPulledRemoteCount();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("双向同步被中断", ex);
                } catch (ExecutionException ex) {
                    pushFailedCount++;
                    failures.add(buildFailureMessage("push", ex.getCause()));
                    log.warn("推送本地商品失败", ex.getCause());
                }
            }
        } finally {
            shutdownExecutor(executor);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("remoteCount", remoteItems.size());
        result.put("pulledGoodsCount", pulledGoodsCount);
        result.put("pulledSkuCount", pulledSkuCount);
        result.put("patchedLocalCount", patchedLocalCount);
        result.put("remoteFailedCount", remoteFailedCount);
        result.put("pushedGoodsCount", pushedGoodsCount);
        result.put("skippedPushCount", skippedPushCount);
        result.put("skippedPulledRemoteCount", skippedPulledRemoteCount);
        result.put("pushFailedCount", pushFailedCount);
        if (!failures.isEmpty()) {
            result.put("failures", failures);
        }
        return result;
    }

    /**
     * 定时回查本地仍处于审核中的支付宝商品，兜底处理支付宝状态通知未到达的情况。
     *
     * @return 本次回查摘要
     */
    public Map<String, Object> refreshPendingAlipayItemStatuses() {
        List<Good> pendingGoods = goodMapper.selectList(new QueryWrapper<Good>()
                .isNotNull("alipay_goods_id")
                .and(wrapper -> wrapper.eq("alipay_audit_status", "AUDITING")
                        .or()
                        .eq("alipay_spu_status", "AUDITING"))
                .last("LIMIT 50"));
        int checkedCount = 0;
        int updatedCount = 0;
        int failedCount = 0;
        List<String> failures = new ArrayList<>();
        if (pendingGoods != null) {
            for (Good good : pendingGoods) {
                checkedCount++;
                try {
                    if (refreshPendingAlipayItemStatus(good)) {
                        updatedCount++;
                    }
                } catch (Exception ex) {
                    failedCount++;
                    failures.add("goodId=" + good.getGoodId() + ": " + ex.getMessage());
                    log.warn("回查支付宝商品审核状态失败，goodId={}, itemId={}",
                            good.getGoodId(), good.getAlipayGoodsId(), ex);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedCount", checkedCount);
        result.put("updatedCount", updatedCount);
        result.put("failedCount", failedCount);
        if (!failures.isEmpty()) {
            result.put("failures", failures);
        }
        return result;
    }

    /**
     * 回查单个审核中商品状态。
     */
    private boolean refreshPendingAlipayItemStatus(Good good) throws AlipayApiException {
        if (good == null || !StringUtils.hasText(good.getAlipayGoodsId())) {
            return false;
        }
        AlipayOpenAppItemQueryResponse editDetail = queryRemoteItemByItemId(good.getAlipayGoodsId().trim(), 1L);
        AlipayOpenAppItemQueryResponse onlineDetail = queryRemoteItemByItemId(good.getAlipayGoodsId().trim(), null);
        AlipayOpenAppItemQueryResponse selectedDetail = selectStatusDetail(editDetail, onlineDetail);
        if (selectedDetail == null || !selectedDetail.isSuccess()) {
            return false;
        }
        return patchLocalGoodRemoteStatusFromQuery(good, selectedDetail);
    }

    /**
     * 批量同步时推送本地商品：本地已有的都要上推，只有本轮刚从支付宝拉回来的商品跳过，避免用占位字段覆盖远端。
     */
    private LocalPushResult pushLocalGood(Good good, Set<Integer> pulledRemoteGoodIds) throws AlipayApiException {
        LocalPushResult result = new LocalPushResult();
        if (good == null || good.getGoodId() == null) {
            result.setSkippedCount(1);
            return result;
        }
        if (pulledRemoteGoodIds.contains(good.getGoodId())) {
            result.setSkippedPulledRemoteCount(1);
            return result;
        }
        List<Attr> attrs = attrMapper.selectList(new QueryWrapper<Attr>().eq("goods_id", good.getGoodId()));
        if (CollectionUtils.isEmpty(attrs)) {
            result.setSkippedCount(1);
            return result;
        }
        sync(good.getGoodId());
        result.setPushedCount(1);
        return result;
    }

    /**
     * 分页拉取支付宝侧已生效商品摘要。
     *
     * @return 支付宝侧商品摘要列表
     * @throws AlipayApiException 支付宝 SDK 调用异常
     */
    private List<ItemSpuVO> listRemoteItems() throws AlipayApiException {
        List<ItemSpuVO> allItems = new ArrayList<>();
        long pageNum = 1L;
        while (true) {
            AlipayOpenAppItemListQueryModel model = new AlipayOpenAppItemListQueryModel();
            model.setPageNum(pageNum);
            model.setPageSize(REMOTE_LIST_PAGE_SIZE);

            AlipayOpenAppItemListQueryRequest request = new AlipayOpenAppItemListQueryRequest();
            request.setBizModel(model);
            AlipayOpenAppItemListQueryResponse response = alipayClientService.execute(request);
            ensureAlipaySuccess(response, "分页查询支付宝商品失败");

            List<ItemSpuVO> pageItems = response.getItems();
            if (CollectionUtils.isEmpty(pageItems)) {
                break;
            }
            allItems.addAll(pageItems);
            Long total = response.getTotal();
            if (total != null && allItems.size() >= total) {
                break;
            }
            if (pageItems.size() < REMOTE_LIST_PAGE_SIZE) {
                break;
            }
            pageNum++;
        }
        return allItems;
    }

    /**
     * 把支付宝侧商品拉回本地。
     *
     * <p>本地已存在时只补支付宝标识和状态字段；本地不存在时才新增商品和规格。</p>
     */
    private RemotePullResult pullRemoteItem(ItemSpuVO remoteItem) throws AlipayApiException {
        RemotePullResult result = new RemotePullResult();
        if (remoteItem == null || !StringUtils.hasText(remoteItem.getItemId())) {
            return result;
        }

        AlipayOpenAppItemQueryResponse detail = queryRemoteItemByItemId(remoteItem.getItemId(), 1L);
        ensureAlipaySuccess(detail, "查询支付宝商品详情失败");

        Good good = findLocalGoodByRemoteItem(detail);
        if (good == null) {
            good = createLocalGoodFromRemote(detail);
            result.setCreatedGood(true);
        } else if (patchLocalGoodRemoteState(good, detail)) {
            result.setPatchedLocal(true);
        }

        result.setGoodId(good.getGoodId());
        result.setCreatedSkuCount(pullRemoteSkus(good, detail));
        return result;
    }

    /**
     * 根据支付宝平台侧 ID 或商家侧本地商品 ID 匹配本地商品。
     */
    private Good findLocalGoodByRemoteItem(AlipayOpenAppItemQueryResponse detail) {
        if (StringUtils.hasText(detail.getItemId())) {
            List<Good> goods = goodMapper.selectList(new QueryWrapper<Good>().eq("alipay_goods_id", detail.getItemId()));
            if (!CollectionUtils.isEmpty(goods)) {
                return goods.get(0);
            }
        }
        String outItemId = detail.getOutItemId();
        if (StringUtils.hasText(outItemId)) {
            try {
                return goodMapper.selectById(parseLocalId(outItemId));
            } catch (NumberFormatException ex) {
                log.warn("支付宝商品 out_item_id 不是本地商品ID格式: {}", outItemId);
            }
        }
        return null;
    }

    /**
     * 从支付宝商品详情新增本地商品。
     */
    private Good createLocalGoodFromRemote(AlipayOpenAppItemQueryResponse detail) {
        Good good = new Good();
        good.setGoodTitle(limitLength(detail.getTitle(), 255));
        good.setGoodDesc(limitLength(detail.getDesc(), 255));
        good.setGoodCover(firstText(detail.getHeadImg(), firstImage(detail.getImageList())));
        good.setGoodSlid(buildRemoteSlides(detail));
        good.setGoodMinamo(toInteger(detail.getSalePrice()));
        good.setGoodMaxamo(toInteger(detail.getSalePrice()));
        good.setGoodDeposit(0);
        good.setGoodDistr(1);
        good.setStatus(isRemoteAvailable(detail.getSpuStatus()) ? 1 : 0);
        good.setIspub(isRemoteAvailable(detail.getSpuStatus()) ? 1 : 2);
        good.setDeal(0);
        good.setGoodSort(System.currentTimeMillis());
        good.setOfflinePickup(1);
        good.setFreight(0);
        good.setIsBuyOut(0);
        good.setAlipayGoodsId(detail.getItemId());
        good.setAlipayCategoryId(detail.getCategoryId());
        good.setAlipaySpuStatus(detail.getSpuStatus());
        good.setAlipayStatusUpdatedAt(new java.util.Date());
        goodMapper.insert(good);
        return good;
    }

    /**
     * 仅补齐本地商品的支付宝标识和状态字段，不覆盖标题、图片、价格等业务字段。
     */
    private boolean patchLocalGoodRemoteState(Good good, AlipayOpenAppItemQueryResponse detail) {
        boolean changed = false;
        if (StringUtils.hasText(detail.getItemId()) && !Objects.equals(good.getAlipayGoodsId(), detail.getItemId())) {
            good.setAlipayGoodsId(detail.getItemId());
            changed = true;
        }
        if (StringUtils.hasText(detail.getSpuStatus()) && !Objects.equals(good.getAlipaySpuStatus(), detail.getSpuStatus())) {
            good.setAlipaySpuStatus(detail.getSpuStatus());
            changed = true;
        }
        if (StringUtils.hasText(detail.getCategoryId()) && !StringUtils.hasText(good.getAlipayCategoryId())) {
            good.setAlipayCategoryId(detail.getCategoryId());
            changed = true;
        }
        if (changed) {
            good.setAlipayStatusUpdatedAt(new java.util.Date());
            goodMapper.updateById(good);
        }
        return changed;
    }

    private boolean hasLocalCategoryChangedFromRemote(Good good, AlipayOpenAppItemQueryResponse remoteDetail) {
        return good != null
                && remoteDetail != null
                && StringUtils.hasText(good.getAlipayCategoryId())
                && StringUtils.hasText(remoteDetail.getCategoryId())
                && !Objects.equals(good.getAlipayCategoryId(), remoteDetail.getCategoryId());
    }

    /**
     * 用商品详情查询结果刷新本地审核状态和 SPU 状态。
     */
    private boolean patchLocalGoodRemoteStatusFromQuery(Good good, AlipayOpenAppItemQueryResponse detail) {
        boolean changed = patchLocalGoodRemoteState(good, detail);
        String spuStatus = detail.getSpuStatus();
        String auditStatus = resolveAuditStatusFromSpuStatus(spuStatus);
        if (StringUtils.hasText(auditStatus) && !Objects.equals(good.getAlipayAuditStatus(), auditStatus)) {
            good.setAlipayAuditStatus(auditStatus);
            changed = true;
        }
        if ("PASS".equals(auditStatus) && StringUtils.hasText(good.getAlipayStatusReason())) {
            good.setAlipayStatusReason(null);
            changed = true;
        }
        if ("REJECT".equals(auditStatus)) {
            String statusReason = extractStatusReasonFromDetail(detail);
            if (!StringUtils.hasText(statusReason)) {
                statusReason = "支付宝审核驳回，回查接口未返回具体原因，请到支付宝后台查看";
            }
            if (!Objects.equals(good.getAlipayStatusReason(), statusReason)) {
                good.setAlipayStatusReason(statusReason);
                changed = true;
            }
        }
        if (changed) {
            good.setAlipayStatusUpdatedAt(new java.util.Date());
            goodMapper.updateById(good);
        }
        return changed;
    }

    /**
     * 从支付宝回查详情里提取审核驳回原因。
     */
    private String extractStatusReasonFromDetail(AlipayOpenAppItemQueryResponse detail) {
        if (detail == null) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        if (!CollectionUtils.isEmpty(detail.getSceneRiskInfo())) {
            for (ItemSceneRiskInfo sceneRiskInfo : detail.getSceneRiskInfo()) {
                collectSceneRiskReasons(reasons, sceneRiskInfo);
            }
        }
        if (!CollectionUtils.isEmpty(detail.getRiskInfo())) {
            for (ItemRiskInfo riskInfo : detail.getRiskInfo()) {
                if (riskInfo == null) {
                    continue;
                }
                String reason = formatReason(riskInfo.getRiskInfos());
                if (StringUtils.hasText(reason)) {
                    reasons.add(reason);
                }
            }
        }
        if (reasons.isEmpty()) {
            return null;
        }
        return String.join("；", deduplicate(reasons));
    }

    private void collectSceneRiskReasons(List<String> reasons, ItemSceneRiskInfo sceneRiskInfo) {
        if (sceneRiskInfo == null || CollectionUtils.isEmpty(sceneRiskInfo.getRiskInfos())) {
            return;
        }
        String scene = firstText(normalizeText(sceneRiskInfo.getScene()), normalizeText(sceneRiskInfo.getSceneCode()));
        for (Reasons riskReason : sceneRiskInfo.getRiskInfos()) {
            String reason = formatReason(riskReason);
            if (!StringUtils.hasText(reason)) {
                continue;
            }
            if (StringUtils.hasText(scene) && !reason.startsWith(scene)) {
                reason = scene + "：" + reason;
            }
            reasons.add(reason);
        }
    }

    private String formatReason(Reasons reason) {
        if (reason == null) {
            return null;
        }
        String riskName = normalizeText(reason.getRiskName());
        String remark = normalizeText(reason.getRemark());
        if (StringUtils.hasText(riskName) && StringUtils.hasText(remark)
                && !remark.startsWith(riskName)) {
            return riskName + "：" + remark;
        }
        return firstText(remark, riskName);
    }

    /**
     * 从编辑态和线上态详情中选出最能代表审核结果的一份。
     */
    private AlipayOpenAppItemQueryResponse selectStatusDetail(
            AlipayOpenAppItemQueryResponse editDetail,
            AlipayOpenAppItemQueryResponse onlineDetail
    ) {
        if (editDetail != null && editDetail.isSuccess() && isEditAuditStatus(editDetail.getSpuStatus())) {
            return editDetail;
        }
        if (onlineDetail != null && onlineDetail.isSuccess() && isTerminalItemStatus(onlineDetail.getSpuStatus())) {
            return onlineDetail;
        }
        if (editDetail != null && editDetail.isSuccess()) {
            return editDetail;
        }
        return onlineDetail;
    }

    /**
     * 编辑态仍有审核结果时，以编辑态为准，避免线上旧版本状态覆盖本地审核列。
     */
    private boolean isEditAuditStatus(String spuStatus) {
        return "AUDITING".equals(spuStatus)
                || "AUDIT_REJECT".equals(spuStatus);
    }

    /**
     * 审核已结束或商品已进入明确处置状态。
     */
    private boolean isTerminalItemStatus(String spuStatus) {
        return "AVAILABLE".equals(spuStatus)
                || "AUDIT_REJECT".equals(spuStatus)
                || "DELISTING".equals(spuStatus)
                || "FREEZE".equals(spuStatus)
                || "FREZEE".equals(spuStatus);
    }

    /**
     * 将支付宝商品状态映射成列表审核列。
     */
    private String resolveAuditStatusFromSpuStatus(String spuStatus) {
        if ("AVAILABLE".equals(spuStatus) || "ONLINE".equals(spuStatus)) {
            return "PASS";
        }
        if ("AUDIT_REJECT".equals(spuStatus)) {
            return "REJECT";
        }
        if ("AUDITING".equals(spuStatus)) {
            return "AUDITING";
        }
        return null;
    }

    /**
     * 创建/普通修改会进入支付宝审核链路，先把本地列表状态标记为审核中；最终通过/驳回仍以状态通知覆盖。
     */
    private void markLocalAuditPending(Good good) {
        if (good == null || good.getGoodId() == null) {
            return;
        }
        boolean changed = false;
        if (!Objects.equals(good.getAlipayAuditStatus(), "AUDITING")) {
            good.setAlipayAuditStatus("AUDITING");
            changed = true;
        }
        if (StringUtils.hasText(good.getAlipayStatusReason())) {
            good.setAlipayStatusReason(null);
            changed = true;
        }
        if (changed) {
            good.setAlipayStatusUpdatedAt(new java.util.Date());
            goodMapper.updateById(good);
        }
    }

    /**
     * 拉取支付宝商品下的 SKU；本地已有只补 alipay_sku_id，本地没有则新增规格。
     */
    private int pullRemoteSkus(Good good, AlipayOpenAppItemQueryResponse detail) {
        if (good == null || CollectionUtils.isEmpty(detail.getSkus())) {
            return 0;
        }
        int createdCount = 0;
        for (ItemSkuSearchVO remoteSku : detail.getSkus()) {
            Attr attr = findLocalAttrByRemoteSku(remoteSku);
            if (attr == null) {
                attr = createLocalAttrFromRemote(good, remoteSku, detail);
                createdCount++;
                continue;
            }
            if (StringUtils.hasText(remoteSku.getSkuId()) && !Objects.equals(attr.getApilyGoodSkuId(), remoteSku.getSkuId())) {
                attr.setApilyGoodSkuId(remoteSku.getSkuId());
                attrMapper.updateById(attr);
            }
        }
        return createdCount;
    }

    /**
     * 根据支付宝 SKU ID 或商家侧本地规格 ID 匹配本地规格。
     */
    private Attr findLocalAttrByRemoteSku(ItemSkuSearchVO remoteSku) {
        if (remoteSku == null) {
            return null;
        }
        if (StringUtils.hasText(remoteSku.getSkuId())) {
            List<Attr> attrs = attrMapper.selectList(new QueryWrapper<Attr>().eq("alipay_sku_id", remoteSku.getSkuId()));
            if (!CollectionUtils.isEmpty(attrs)) {
                return attrs.get(0);
            }
        }
        String outSkuId = remoteSku.getOutSkuId();
        if (StringUtils.hasText(outSkuId)) {
            try {
                return attrMapper.selectById(parseLocalId(outSkuId));
            } catch (NumberFormatException ex) {
                log.warn("支付宝 SKU out_sku_id 不是本地规格ID格式: {}", outSkuId);
            }
        }
        return null;
    }

    /**
     * 从支付宝 SKU 新增本地规格。
     */
    private Attr createLocalAttrFromRemote(Good good, ItemSkuSearchVO remoteSku, AlipayOpenAppItemQueryResponse detail) {
        Integer salePrice = resolveRemoteSkuPrice(remoteSku);
        String rentDays = resolveRemoteRentDays(remoteSku);
        Attr attr = new Attr();
        attr.setGoodId(good.getGoodId());
        attr.setAttrTitle(limitLength(buildRemoteSkuTitle(remoteSku), 255));
        attr.setAttrDesc(limitLength(remoteSku.getOutSkuId(), 255));
        attr.setAttrSlid(firstText(remoteSku.getThumbImg(), good.getGoodCover()));
        attr.setAttrAmount(salePrice);
        attr.setAttrDeposit(salePrice);
        attr.setAttrNum(toInteger(remoteSku.getStockNum()));
        attr.setAttrRentday(rentDays);
        attr.setFree(1);
        attr.setBuyout(0);
        attr.setBuyoutval(resolveRemoteOriginalPrice(remoteSku, salePrice));
        attr.setMinRent(firstRentDay(rentDays));
        attr.setMaxRent(lastRentDay(rentDays));
        attr.setPenalAmount(0);
        attr.setAttrTradeType("MONTH".equals(detail.getPriceUnit()) ? 2 : 1);
        attr.setApilyGoodSkuId(remoteSku.getSkuId());
        attrMapper.insert(attr);
        return attr;
    }

    /**
     * 用支付宝 SKU 属性拼出本地规格标题。
     */
    private String buildRemoteSkuTitle(ItemSkuSearchVO remoteSku) {
        if (remoteSku == null || CollectionUtils.isEmpty(remoteSku.getSkuAttrs())) {
            return remoteSku != null && StringUtils.hasText(remoteSku.getOutSkuId()) ? remoteSku.getOutSkuId() : "支付宝规格";
        }
        String rentCommodityName = extractRentCommodityName(remoteSku);
        if (StringUtils.hasText(rentCommodityName)) {
            return rentCommodityName;
        }
        String title = remoteSku.getSkuAttrs().stream()
                .map(ItemSkuAttrVO::getAttrValue)
                .filter(StringUtils::hasText)
                .filter(value -> !value.trim().startsWith("{"))
                .collect(Collectors.joining("/"));
        return StringUtils.hasText(title) ? title : firstText(remoteSku.getOutSkuId(), "支付宝规格");
    }

    /**
     * 解析支付宝租赁 SKU 的售价。本地同步规则里押金就是 sale_price，远端缺失时回退租赁套餐单价。
     */
    private Integer resolveRemoteSkuPrice(ItemSkuSearchVO remoteSku) {
        Integer salePrice = toInteger(remoteSku == null ? null : remoteSku.getSalePrice());
        if (salePrice != null && salePrice > 0) {
            return salePrice;
        }
        Long unitSalePrice = firstJsonLong(extractRentCommodityJson(remoteSku), "unitSalePrice");
        Integer parsedPrice = toInteger(unitSalePrice);
        return parsedPrice == null ? 0 : parsedPrice;
    }

    /**
     * 解析远端原价，缺失时用售价兜底，避免本地新增规格价格字段为空。
     */
    private Integer resolveRemoteOriginalPrice(ItemSkuSearchVO remoteSku, Integer salePrice) {
        Integer originalPrice = toInteger(remoteSku == null ? null : remoteSku.getOriginalPrice());
        if (originalPrice != null && originalPrice > 0) {
            return originalPrice;
        }
        return salePrice == null ? 0 : salePrice;
    }

    /**
     * 从支付宝 rent_commodity 中解析租期列表。
     */
    private String resolveRemoteRentDays(ItemSkuSearchVO remoteSku) {
        List<Long> durations = jsonLongValues(extractRentCommodityJson(remoteSku), "duration");
        if (CollectionUtils.isEmpty(durations)) {
            return "1";
        }
        return durations.stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining("/"));
    }

    private Integer firstRentDay(String rentDays) {
        List<Long> days = parseRentDays(rentDays);
        return days.isEmpty() ? 1 : days.get(0).intValue();
    }

    private Integer lastRentDay(String rentDays) {
        List<Long> days = parseRentDays(rentDays);
        return days.isEmpty() ? 1 : days.get(days.size() - 1).intValue();
    }

    private List<Long> parseRentDays(String rentDays) {
        if (!StringUtils.hasText(rentDays)) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>();
        for (String part : rentDays.split("[,，/、\\s]+")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                long day = Long.parseLong(part.trim());
                if (day > 0) {
                    result.add(day);
                }
            } catch (NumberFormatException ignored) {
                // 忽略非法租期。
            }
        }
        return result;
    }

    private String extractRentCommodityName(ItemSkuSearchVO remoteSku) {
        return jsonString(extractRentCommodityJson(remoteSku), "name");
    }

    private String extractRentCommodityJson(ItemSkuSearchVO remoteSku) {
        if (remoteSku == null || CollectionUtils.isEmpty(remoteSku.getSkuAttrs())) {
            return null;
        }
        for (ItemSkuAttrVO skuAttr : remoteSku.getSkuAttrs()) {
            if (skuAttr == null) {
                continue;
            }
            if ("rent_commodity".equals(skuAttr.getAttrKey()) && StringUtils.hasText(skuAttr.getAttrValue())) {
                return skuAttr.getAttrValue();
            }
        }
        return null;
    }

    private String jsonString(String json, String key) {
        if (!StringUtils.hasText(json) || !StringUtils.hasText(key)) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
    }

    private Long firstJsonLong(String json, String key) {
        List<Long> values = jsonLongValues(json, key);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<Long> jsonLongValues(String json, String key) {
        if (!StringUtils.hasText(json) || !StringUtils.hasText(key)) {
            return Collections.emptyList();
        }
        List<Long> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?(\\d+)").matcher(json);
        while (matcher.find()) {
            try {
                values.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // 忽略非法数字。
            }
        }
        return values;
    }

    /**
     * 免审下架支付宝商品。
     *
     * <p>后台本地下架后调用本方法，只同步商品和 SKU 的售卖状态，不重新提报详情图文，
     * 避免普通修改接口在审核中版本存在时被支付宝拒绝。
     *
     * @param goodId 本地商品 ID
     * @return 下架同步结果摘要
     * @throws AlipayApiException 支付宝 SDK 调用异常
     */
    public Map<String, Object> syncDelisting(Integer goodId) throws AlipayApiException {
        Good good = requireGood(goodId);
        List<Attr> attrs = requireAttrs(goodId);
        String outItemId = buildOutItemId(good);
        String remoteItemId = resolveRemoteItemId(good, outItemId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodId", good.getGoodId());
        result.put("outItemId", outItemId);
        if (!StringUtils.hasText(remoteItemId)) {
            result.put("syncMode", "skip");
            result.put("message", "支付宝侧未找到商品，已跳过远端下架");
            return result;
        }

        AlipayOpenAppItemDirectModifyModel model = new AlipayOpenAppItemDirectModifyModel();
        model.setItemId(remoteItemId);
        model.setOutItemId(outItemId);
        model.setSaleStatus("DELISTING");
        model.setSkus(buildDirectModifySkus(attrs));

        AlipayOpenAppItemDirectModifyRequest request = new AlipayOpenAppItemDirectModifyRequest();
        request.setBizModel(model);
        AlipayOpenAppItemDirectModifyResponse response = alipayClientService.execute(request);
        ensureAlipaySuccess(response, "下架支付宝商品失败");

        result.put("itemId", remoteItemId);
        result.put("syncMode", "direct_delisting");
        result.put("skuCount", attrs.size());
        return result;
    }

    /**
     * 删除支付宝侧商品。
     *
     * <p>本地删除商品前调用；如果支付宝侧没有对应商品，则返回跳过，避免阻塞历史未同步商品的本地删除。</p>
     *
     * @param goodId 本地商品 ID
     * @return 删除同步结果摘要
     * @throws AlipayApiException 支付宝 SDK 调用异常
     */
    public Map<String, Object> deleteRemoteItem(Integer goodId) throws AlipayApiException {
        Good good = requireGood(goodId);
        String outItemId = buildOutItemId(good);
        String remoteItemId = resolveRemoteItemId(good, outItemId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodId", good.getGoodId());
        result.put("outItemId", outItemId);
        if (!StringUtils.hasText(remoteItemId)) {
            result.put("syncMode", "skip");
            result.put("message", "支付宝侧未找到商品，已跳过远端删除");
            return result;
        }

        AlipayOpenAppItemDeleteModel model = new AlipayOpenAppItemDeleteModel();
        model.setItemIdList(Collections.singletonList(remoteItemId));

        AlipayOpenAppItemDeleteRequest request = new AlipayOpenAppItemDeleteRequest();
        request.setBizModel(model);
        AlipayOpenAppItemDeleteResponse response = alipayClientService.execute(request);
        ensureAlipaySuccess(response, "删除支付宝商品失败");

        result.put("itemId", remoteItemId);
        result.put("syncMode", "delete");
        return result;
    }

    /**
     * 校验并读取商品主记录。
     */
    private Good requireGood(Integer goodId) {
        if (goodId == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        Good good = goodMapper.selectById(goodId);
        if (good == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        if (!StringUtils.hasText(good.getGoodTitle())) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (!StringUtils.hasText(good.getGoodCover())) {
            throw new IllegalArgumentException("商品封面图不能为空");
        }
        return good;
    }

    /**
     * 校验并读取商品规格列表。
     */
    private List<Attr> requireAttrs(Integer goodId) {
        List<Attr> attrs = attrMapper.selectList(new QueryWrapper<Attr>().eq("goods_id", goodId));
        if (CollectionUtils.isEmpty(attrs)) {
            throw new IllegalArgumentException("请先给商品添加至少一个规格后再同步");
        }
        List<Attr> validAttrs = attrs.stream()
                .filter(Objects::nonNull)
                .filter(attr -> attr.getAttrId() != null)
                .collect(Collectors.toList());
        if (validAttrs.isEmpty()) {
            throw new IllegalArgumentException("未读取到有效的商品规格数据");
        }
        return validAttrs;
    }

    /**
     * 从中台读取支付宝商品同步配置。
     */
    private SyncConfig loadSyncConfig(Good good, List<Attr> attrs) {
        Attr primaryAttr = selectPrimaryAttr(attrs);
        SyncConfig config = new SyncConfig();
        config.setAssetBaseUrl(requireSyncConfigValue(
                alipayPlatformConfigService.goodsSyncAssetBaseUrl(),
                "alipay.goods.sync.asset-base-url", "支付宝商品同步-图片基础地址"));
        config.setCategoryId(requireSyncConfigValue(
                firstText(good.getAlipayCategoryId(), alipayPlatformConfigService.goodsSyncCategoryId()),
                "goods.alipay_category_id 或 alipay.goods.sync.category-id", "支付宝商品同步-类目ID"));
        config.setBusinessModel(requireSyncConfigValue(
                alipayPlatformConfigService.goodsSyncBusinessModel(),
                "alipay.goods.sync.business-model", "支付宝商品同步-业务模式"));
        config.setItemDetailsPageModel(resolveOptionalItemDetailsPageModel(
                alipayPlatformConfigService.goodsSyncItemDetailsPageModel()));
        config.setPathTemplate(trimToNull(alipayPlatformConfigService.goodsSyncPathTemplate()));
        config.setServicePhone(requireSyncConfigValue(
                alipayPlatformConfigService.servicePhone(), "rent.service-phone", "支付宝商品同步-客服电话"));
        config.setPriceUnit(resolvePriceUnit(primaryAttr));
        config.setSaleStatus(resolveGoodsSaleStatus(good));
        config.setOutItemId(buildOutItemId(good));
        return config;
    }

    /**
     * 读取必填同步配置，避免商品同步静默使用代码默认值。
     */
    private String requireSyncConfigValue(String value, String configKey, String configName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(configName + "未配置，请在中台配置 " + configKey);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveOptionalItemDetailsPageModel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if ("0".equals(trimmed) || "1".equals(trimmed)) {
            return trimmed;
        }
        throw new IllegalStateException("支付宝商品同步-详情页模式只能配置 0、1 或留空，请检查 "
                + "alipay.goods.sync.item-details-page-model");
    }

    /**
     * 自定义详情页模式必须配置 path 模板。
     */
    private void validateGoodsPathConfig(SyncConfig config) {
        if ("0".equals(config.getItemDetailsPageModel()) && !StringUtils.hasText(config.getPathTemplate())) {
            throw new IllegalStateException("支付宝商品同步-详情页路径模板未配置，请在中台配置 "
                    + "alipay.goods.sync.path-template");
        }
    }

    /**
     * 同步商品相关图片。
     */
    private SyncedImageBundle syncImages(
            Good good,
            List<Attr> attrs,
            SyncConfig config,
            Map<String, String> uploadCache
    ) throws AlipayApiException {
        SyncedImageBundle bundle = new SyncedImageBundle();
        String imageDirectoryId = ensureImageDirectory(good);

        // 商品介绍、租赁必读、规格参数里都可能有图片，这里统一抽出来补齐给支付宝。
        LinkedHashSet<String> detailSources = new LinkedHashSet<>();
        detailSources.addAll(extractHtmlImageSources(good.getGoodCon()));
        detailSources.addAll(extractHtmlImageSources(good.getGoodRec()));
        detailSources.addAll(extractHtmlImageSources(good.getGoodSpepar()));
        if (detailSources.isEmpty()) {
            detailSources.addAll(splitCsv(good.getGoodSlid()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(IMAGE_UPLOAD_THREAD_POOL_SIZE);
        try {
            Future<String> headFuture = executor.submit(() ->
                    uploadImage(good.getGoodCover(), config, uploadCache, IMAGE_SCENE_HEAD, imageDirectoryId));

            List<String> bannerSources = splitCsv(good.getGoodSlid()).stream()
                    .limit(MAX_ALIPAY_BANNER_COUNT)
                    .collect(Collectors.toList());
            List<Future<String>> bannerFutures =
                    submitImageUploads(executor, bannerSources, config, uploadCache, IMAGE_SCENE_LIST, imageDirectoryId);

            List<String> limitedDetailSources = detailSources.stream()
                    .limit(MAX_ALIPAY_DETAIL_IMAGE_COUNT)
                    .collect(Collectors.toList());
            List<Future<String>> detailFutures =
                    submitImageUploads(executor, limitedDetailSources, config, uploadCache, IMAGE_SCENE_DESC, imageDirectoryId);

            Map<Integer, Future<String>> skuThumbFutures = new LinkedHashMap<>();
            for (Attr attr : attrs) {
                String skuThumbSource = StringUtils.hasText(attr.getAttrSlid()) ? attr.getAttrSlid() : good.getGoodCover();
                skuThumbFutures.put(attr.getAttrId(), executor.submit(() ->
                        uploadImage(skuThumbSource, config, uploadCache, IMAGE_SCENE_SKU_THUMB, imageDirectoryId)));
            }

            bundle.setHeadImg(resolveImageFuture(headFuture));
            bundle.setImageList(resolveImageFutures(bannerFutures));
            bundle.setDescImgs(resolveImageFutures(detailFutures));

            Map<Integer, String> skuThumbs = new HashMap<>();
            for (Map.Entry<Integer, Future<String>> entry : skuThumbFutures.entrySet()) {
                skuThumbs.put(entry.getKey(), resolveImageFuture(entry.getValue()));
            }
            bundle.setSkuThumbs(skuThumbs);
            return bundle;
        } finally {
            shutdownExecutor(executor);
        }
    }

    /**
     * 并发提交同一图片场景下的上传任务。
     */
    private List<Future<String>> submitImageUploads(
            ExecutorService executor,
            List<String> imageSources,
            SyncConfig config,
            Map<String, String> uploadCache,
            String uploadScene,
            String imageDirectoryId
    ) {
        List<Future<String>> futures = new ArrayList<>();
        for (String imageSource : imageSources) {
            futures.add(executor.submit(() -> uploadImage(imageSource, config, uploadCache, uploadScene, imageDirectoryId)));
        }
        return futures;
    }

    /**
     * 按提交顺序收集上传结果，保证轮播图和详情图顺序稳定。
     */
    private List<String> resolveImageFutures(List<Future<String>> futures) throws AlipayApiException {
        List<String> images = new ArrayList<>();
        for (Future<String> future : futures) {
            String image = resolveImageFuture(future);
            if (StringUtils.hasText(image)) {
                images.add(image);
            }
        }
        return images;
    }

    /**
     * 还原并行上传任务里的业务异常。
     */
    private String resolveImageFuture(Future<String> future) throws AlipayApiException {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("同步支付宝商品图片被中断", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof AlipayApiException) {
                throw (AlipayApiException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("同步支付宝商品图片失败", cause);
        }
    }

    /**
     * 根据远端商品和 SKU 绑定状态，自动走创建、免审更新或普通修改逻辑。
     */
    private ItemUpsertResult upsertMiniappItem(
            Good good,
            List<Attr> attrs,
            SyncConfig config,
            SyncedImageBundle imageBundle,
            String remoteItemId,
            AlipayOpenAppItemQueryResponse remoteDetail
    ) throws AlipayApiException {
        if (StringUtils.hasText(remoteItemId)) {
            if (canDirectModifyMiniappItem(attrs, remoteDetail)) {
                return directModifyMiniappItem(good, attrs, config, remoteItemId, remoteDetail);
            }
            return modifyMiniappItem(good, attrs, config, imageBundle, remoteItemId, remoteDetail);
        }
        return createMiniappItem(good, attrs, config, imageBundle);
    }

    /**
     * 判断当前商品是否可以走免审更新。免审接口不能新增 SKU，也不能改租赁套餐租期。
     */
    private boolean canDirectModifyMiniappItem(List<Attr> attrs, AlipayOpenAppItemQueryResponse remoteDetail) {
        if (!hasAllRemoteSkuBindings(attrs, remoteDetail)) {
            return false;
        }
        if (remoteDetail == null || CollectionUtils.isEmpty(remoteDetail.getSkus())) {
            return true;
        }
        for (Attr attr : attrs) {
            ItemSkuSearchVO remoteSku = findRemoteSkuByAttr(attr, remoteDetail);
            if (remoteSku == null) {
                return false;
            }
            List<Long> remoteDurations = jsonLongValues(extractRentCommodityJson(remoteSku), "duration");
            if (!CollectionUtils.isEmpty(remoteDurations) && !sameRentDurations(resolveRentDurations(attr), remoteDurations)) {
                log.info("本地租期与支付宝租期不一致，改走审核更新，goodId={}, attrId={}, localDurations={}, remoteDurations={}",
                        attr.getGoodId(), attr.getAttrId(), resolveRentDurations(attr), remoteDurations);
                return false;
            }
        }
        return true;
    }

    /**
     * 判断当前本地 SKU 是否都已经和支付宝 SKU 建立绑定。
     */
    private boolean hasAllRemoteSkuBindings(List<Attr> attrs, AlipayOpenAppItemQueryResponse remoteDetail) {
        if (CollectionUtils.isEmpty(attrs)) {
            return false;
        }
        if (remoteDetail != null && !CollectionUtils.isEmpty(remoteDetail.getSkus())) {
            Map<String, ItemSkuSearchVO> remoteByOutSkuId = new HashMap<>();
            Map<String, ItemSkuSearchVO> remoteBySkuId = new HashMap<>();
            for (ItemSkuSearchVO remoteSku : remoteDetail.getSkus()) {
                if (remoteSku == null) {
                    continue;
                }
                if (StringUtils.hasText(remoteSku.getOutSkuId())) {
                    remoteByOutSkuId.put(remoteSku.getOutSkuId(), remoteSku);
                }
                if (StringUtils.hasText(remoteSku.getSkuId())) {
                    remoteBySkuId.put(remoteSku.getSkuId(), remoteSku);
                }
            }
            for (Attr attr : attrs) {
                if (attr == null) {
                    continue;
                }
                if (remoteByOutSkuId.containsKey(buildOutSkuId(attr))) {
                    continue;
                }
                if (StringUtils.hasText(attr.getApilyGoodSkuId())
                        && remoteBySkuId.containsKey(attr.getApilyGoodSkuId())) {
                    continue;
                }
                return false;
            }
            return true;
        }
        return attrs.stream()
                .filter(Objects::nonNull)
                .allMatch(attr -> StringUtils.hasText(attr.getApilyGoodSkuId()));
    }

    /**
     * 比较租期集合，顺序不同不视为变化。
     */
    private boolean sameRentDurations(List<Long> left, List<Long> right) {
        return new LinkedHashSet<>(left).equals(new LinkedHashSet<>(right));
    }

    /**
     * 创建支付宝商品。
     */
    private ItemUpsertResult createMiniappItem(
            Good good,
            List<Attr> attrs,
            SyncConfig config,
            SyncedImageBundle imageBundle
    ) throws AlipayApiException {
        AlipayOpenAppItemCreateModel model = new AlipayOpenAppItemCreateModel();
        fillCommonCreateFields(model, good, attrs, config, imageBundle);

        AlipayOpenAppItemCreateRequest request = new AlipayOpenAppItemCreateRequest();
        request.setBizModel(model);
        AlipayOpenAppItemCreateResponse response = alipayClientService.execute(request);
        ensureAlipaySuccess(response, "创建支付宝商品失败");
        CalendarStockSyncResult calendarStockResult =
                saveCalendarStocksSafely(response.getItemId(), config.getOutItemId(), attrs, config);
        markLocalAuditPending(good);
        if (StringUtils.hasText(response.getItemId())) {
            AlipayOpenAppItemQueryResponse latestDetail = queryRemoteItemByItemId(response.getItemId(), 1L);
            if (latestDetail != null && latestDetail.isSuccess()) {
                patchLocalGoodRemoteState(good, latestDetail);
            }
        }

        ItemUpsertResult result = new ItemUpsertResult();
        result.setItemId(response.getItemId());
        result.setSkuPairs(response.getSkus());
        result.setSyncMode("create");
        result.setCalendarStockResult(calendarStockResult);
        return result;
    }

    /**
     * 修改支付宝商品。
     */
    private ItemUpsertResult modifyMiniappItem(
            Good good,
            List<Attr> attrs,
            SyncConfig config,
            SyncedImageBundle imageBundle,
            String remoteItemId,
            AlipayOpenAppItemQueryResponse remoteDetail
    ) throws AlipayApiException {
        AlipayOpenAppItemModifyModel model = new AlipayOpenAppItemModifyModel();
        fillCommonModifyFields(model, good, attrs, config, imageBundle, remoteItemId, remoteDetail);

        AlipayOpenAppItemModifyRequest request = new AlipayOpenAppItemModifyRequest();
        request.setBizModel(model);
        AlipayOpenAppItemModifyResponse response = alipayClientService.execute(request);
        ensureAlipaySuccess(response, "修改支付宝商品失败");
        String itemId = StringUtils.hasText(response.getItemId()) ? response.getItemId() : remoteItemId;
        markLocalAuditPending(good);
        CalendarStockSyncResult calendarStockResult = saveCalendarStocksSafely(itemId, config.getOutItemId(), attrs, config);
        AlipayOpenAppItemQueryResponse latestDetail = queryRemoteItemByItemId(itemId, 1L);
        if (latestDetail != null && latestDetail.isSuccess()) {
            patchLocalGoodRemoteState(good, latestDetail);
            pullRemoteSkus(good, latestDetail);
        } else {
            log.warn("修改商品后查询最新 SKU 失败，库存同步将按本地 out_sku_id 兜底，itemId={}, subCode={}, subMsg={}",
                    itemId,
                    latestDetail == null ? null : latestDetail.getSubCode(),
                    latestDetail == null ? null : latestDetail.getSubMsg());
        }

        ItemUpsertResult result = new ItemUpsertResult();
        result.setItemId(itemId);
        result.setSkuPairs(response.getSkus());
        result.setSyncMode("modify");
        result.setCalendarStockResult(calendarStockResult);
        return result;
    }

    /**
     * 已存在商品按旧链路走免审更新，避免全量修改时重传商品类目。
     */
    private ItemUpsertResult directModifyMiniappItem(
            Good good,
            List<Attr> attrs,
            SyncConfig config,
            String remoteItemId,
            AlipayOpenAppItemQueryResponse remoteDetail
    ) throws AlipayApiException {
        List<Attr> remoteExistingAttrs = filterRemoteExistingAttrs(attrs, remoteDetail);
        if (CollectionUtils.isEmpty(remoteExistingAttrs)) {
            ItemUpsertResult result = new ItemUpsertResult();
            result.setItemId(remoteItemId);
            result.setSyncMode("direct_modify_skip_no_remote_sku");
            return result;
        }

        AlipayOpenAppItemDirectModifyModel model = new AlipayOpenAppItemDirectModifyModel();
        model.setItemId(remoteItemId);
        model.setSaleStatus(config.getSaleStatus());
        model.setSkus(buildDirectModifySkus(good, remoteExistingAttrs, remoteDetail, true));

        AlipayOpenAppItemDirectModifyRequest request = new AlipayOpenAppItemDirectModifyRequest();
        request.setBizModel(model);
        AlipayOpenAppItemDirectModifyResponse response = alipayClientService.execute(request);
        if (response != null && !response.isSuccess() && isRentDurationModifyRejected(response)) {
            log.warn("免审更新不支持租赁套餐属性变更，重试仅更新价格和售卖状态，itemId={}", remoteItemId);
            model.setSkus(buildDirectModifySkus(good, remoteExistingAttrs, remoteDetail, false));
            request.setBizModel(model);
            response = alipayClientService.execute(request);
        }
        ensureAlipaySuccess(response, "免审更新支付宝商品失败");
        CalendarStockSyncResult calendarStockResult =
                saveCalendarStocksSafely(remoteItemId, config.getOutItemId(), remoteExistingAttrs, config);
        AlipayOpenAppItemQueryResponse latestDetail = queryRemoteItemByItemId(remoteItemId, 1L);
        if (latestDetail != null && latestDetail.isSuccess()) {
            patchLocalGoodRemoteState(good, latestDetail);
        }

        ItemUpsertResult result = new ItemUpsertResult();
        result.setItemId(remoteItemId);
        result.setSyncMode("direct_modify");
        result.setCalendarStockResult(calendarStockResult);
        return result;
    }

    /**
     * 填充创建商品时的通用字段。
     */
    private void fillCommonCreateFields(
            AlipayOpenAppItemCreateModel model,
            Good good,
            List<Attr> attrs,
            SyncConfig config,
            SyncedImageBundle imageBundle
    ) {
        Attr primaryAttr = selectPrimaryAttr(attrs);
        model.setOutItemId(config.getOutItemId());
        model.setTitle(limitLength(good.getGoodTitle(), 100));
        model.setDesc(buildGuideDesc(good));
        model.setDescInfo(buildDescInfo(good, imageBundle));
        model.setHeadImg(imageBundle.getHeadImg());
        model.setImageList(imageBundle.getImageList());
        model.setCategoryId(config.getCategoryId());
        model.setItemType(ITEM_TYPE_RENT);
        model.setBusinessModel(config.getBusinessModel());
        if (StringUtils.hasText(config.getItemDetailsPageModel())) {
            model.setItemDetailsPageModel(config.getItemDetailsPageModel());
        }
        model.setSaleStatus(config.getSaleStatus());
        // 创建请求存在 skus 时，支付宝不允许再设置 item 顶层价格；价格只放在 SKU 维度。
        model.setPriceUnit(config.getPriceUnit());
        model.setAttrs(buildItemAttrs(good, primaryAttr));
        model.setGuideInfo(buildGuideInfo(good, primaryAttr, config));
        if (shouldSendPath(config)) {
            model.setPath(buildGoodsPath(good, config));
        }
        model.setSkus(buildCreateSkus(good, attrs, imageBundle, config));
        model.setAutoMarketingDelivery(Boolean.FALSE);
        model.setAutoPremiumPool(Boolean.FALSE);
    }

    /**
     * 填充修改商品时的通用字段。
     */
    private void fillCommonModifyFields(
            AlipayOpenAppItemModifyModel model,
            Good good,
            List<Attr> attrs,
            SyncConfig config,
            SyncedImageBundle imageBundle,
            String remoteItemId,
            AlipayOpenAppItemQueryResponse remoteDetail
    ) {
        Attr primaryAttr = selectPrimaryAttr(attrs);
        model.setItemId(remoteItemId);
        model.setTitle(limitLength(good.getGoodTitle(), 100));
        model.setDesc(buildGuideDesc(good));
        model.setDescInfo(buildDescInfo(good, imageBundle));
        model.setHeadImg(imageBundle.getHeadImg());
        model.setImageList(imageBundle.getImageList());
        model.setCategoryId(resolveModifyCategoryId(config, remoteDetail));
        model.setItemType(resolveModifyItemType(remoteDetail));
        model.setBusinessModel(config.getBusinessModel());
        if (StringUtils.hasText(config.getItemDetailsPageModel())) {
            model.setItemDetailsPageModel(config.getItemDetailsPageModel());
        }
        model.setSaleStatus(config.getSaleStatus());
        model.setPriceUnit(config.getPriceUnit());
        model.setAttrs(buildItemAttrs(good, primaryAttr));
        model.setGuideInfo(buildGuideInfo(good, primaryAttr, config));
        if (shouldSendPath(config)) {
            model.setPath(buildGoodsPath(good, config));
        }
        model.setSkus(buildModifySkus(good, attrs, imageBundle, remoteDetail, config));
    }

    /**
     * 构造商品属性，补齐目前本地系统没有传给支付宝的细节字段。
     */
    private List<AppItemAttrVO> buildItemAttrs(Good good, Attr primaryAttr) {
        List<AppItemAttrVO> attrs = new ArrayList<>();
        attrs.add(buildItemAttr("item_fineness", resolveItemFineness(good)));
        attrs.add(buildItemAttr("item_fineness_grade", resolveItemFinenessGrade(good)));
        attrs.add(buildItemAttr("rent_model", alipayPlatformConfigService.rentModel()));
        attrs.add(buildItemAttr("whether_support_free_deposit", resolveWhetherSupportFreeDeposit(primaryAttr)));
        attrs.add(buildItemAttr("whether_continue_rent", "1"));
        attrs.add(buildItemAttr("whether_buyout", resolveWhetherBuyout(primaryAttr)));
        attrs.add(buildItemAttr("rent_from_numbers_of_day", String.valueOf(resolveAlipayRentFromNumbersOfDay(primaryAttr))));
        attrs.add(buildItemAttr("sales_model", "[\"RENT\"]"));
        return attrs.stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.getAttrKey()) && StringUtils.hasText(item.getAttrValue()))
                .collect(Collectors.toList());
    }

    private String resolveItemFineness(Good good) {
        return alipayPlatformConfigService.itemFineness(good == null ? null : good.getItemFineness());
    }

    private String resolveItemFinenessGrade(Good good) {
        if (!"secondHand".equals(resolveItemFineness(good))) {
            return null;
        }
        return alipayPlatformConfigService.itemFinenessGrade(good == null ? null : good.getItemFinenessGrade());
    }

    /**
     * 创建单个商品属性对象。
     */
    private AppItemAttrVO buildItemAttr(String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return null;
        }
        AppItemAttrVO attr = new AppItemAttrVO();
        attr.setAttrKey(key);
        attr.setAttrValue(limitLength(value, 100));
        return attr;
    }

    /**
     * 构造导购信息。
     */
    private List<GuideInfoVO> buildGuideInfo(Good good, Attr primaryAttr, SyncConfig config) {
        List<GuideInfoVO> guideInfos = new ArrayList<>();
        addGuideInfo(guideInfos, "service_phone", config.getServicePhone());
        addGuideInfo(guideInfos, "freight", formatFen(good.getFreight()));
        if (primaryAttr != null) {
            addGuideInfo(guideInfos, "deposit", formatFen(resolveDepositPriceLong(primaryAttr).intValue()));
            addGuideInfo(guideInfos, "rent_cycle", normalizeSkuRentDay(primaryAttr));
            addGuideInfo(guideInfos, "buyout", resolveGuideBuyout(primaryAttr));
        }
        return guideInfos;
    }

    /**
     * 追加一条导购信息。
     */
    private void addGuideInfo(List<GuideInfoVO> guideInfos, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        GuideInfoVO guideInfo = new GuideInfoVO();
        guideInfo.setKey(key);
        guideInfo.setValue(limitLength(value, 200));
        guideInfos.add(guideInfo);
    }

    private String resolveModifyCategoryId(SyncConfig config, AlipayOpenAppItemQueryResponse remoteDetail) {
        return config.getCategoryId();
    }

    /**
     * 修改商品时优先沿用支付宝侧现有商品类型，缺失时按租赁商品处理。
     */
    private String resolveModifyItemType(AlipayOpenAppItemQueryResponse remoteDetail) {
        if (remoteDetail != null && StringUtils.hasText(remoteDetail.getItemType())) {
            return remoteDetail.getItemType();
        }
        return ITEM_TYPE_RENT;
    }

    /**
     * 构造商品详情信息。
     */
    private ItemDescInfoVO buildDescInfo(Good good, SyncedImageBundle imageBundle) {
        ItemDescInfoVO descInfo = new ItemDescInfoVO();
        descInfo.setDesc(buildDetailText(good));
        descInfo.setImgs(imageBundle.getDescImgs());
        return descInfo;
    }

    /**
     * 构造创建商品时的 SKU 列表。
     */
    private List<ItemSkuCreateVO> buildCreateSkus(
            Good good,
            List<Attr> attrs,
            SyncedImageBundle imageBundle,
            SyncConfig config
    ) {
        List<ItemSkuCreateVO> skuList = new ArrayList<>();
        for (Attr attr : attrs) {
            ItemSkuCreateVO sku = new ItemSkuCreateVO();
            sku.setOutSkuId(buildOutSkuId(attr));
            sku.setSkuType(SKU_TYPE_RENT);
            sku.setPriceUnit(resolvePriceUnit(attr));
            sku.setSalePrice(String.valueOf(resolveSalePriceLong(attr)));
            sku.setOriginalPrice(resolveOriginalPrice(attr));
            sku.setSaleStatus(resolveSkuSaleStatus(good, attr));
            sku.setThumbImg(imageBundle.getSkuThumbs().get(attr.getAttrId()));
            sku.setSkuAttrs(buildSkuAttrs(attr, config));
            skuList.add(sku);
        }
        return skuList;
    }

    /**
     * 构造修改商品时的 SKU 列表。
     */
    private List<ItemSkuVO> buildModifySkus(
            Good good,
            List<Attr> attrs,
            SyncedImageBundle imageBundle,
            AlipayOpenAppItemQueryResponse remoteDetail,
            SyncConfig config
    ) {
        List<ItemSkuVO> skuList = new ArrayList<>();
        for (Attr attr : attrs) {
            ItemSkuVO sku = new ItemSkuVO();
            String remoteSkuId = resolveRemoteSkuId(attr, remoteDetail);
            if (StringUtils.hasText(remoteSkuId)) {
                sku.setSkuId(remoteSkuId);
            } else {
                sku.setSkuType(SKU_TYPE_RENT);
            }
            sku.setOutSkuId(buildOutSkuId(attr));
            sku.setPriceUnit(resolvePriceUnit(attr));
            sku.setSalePrice(resolveSalePriceLong(attr));
            sku.setOriginalPrice(resolveOriginalPrice(attr));
            sku.setSaleStatus(resolveSkuSaleStatus(good, attr));
            sku.setThumbImg(imageBundle.getSkuThumbs().get(attr.getAttrId()));
            sku.setSkuAttrs(buildSkuAttrs(attr, config));
            skuList.add(sku);
        }
        return skuList;
    }

    /**
     * 只使用支付宝查询确认存在的 sku_id，避免本地历史脏 sku_id 导致 ITEM_SKU_NOT_EXIST。
     */
    private String resolveRemoteSkuId(Attr attr, AlipayOpenAppItemQueryResponse remoteDetail) {
        if (attr == null) {
            return null;
        }
        if (remoteDetail != null && !CollectionUtils.isEmpty(remoteDetail.getSkus())) {
            String outSkuId = buildOutSkuId(attr);
            for (ItemSkuSearchVO remoteSku : remoteDetail.getSkus()) {
                if (remoteSku == null) {
                    continue;
                }
                if (StringUtils.hasText(remoteSku.getOutSkuId())
                        && Objects.equals(outSkuId, remoteSku.getOutSkuId())
                        && StringUtils.hasText(remoteSku.getSkuId())) {
                    return remoteSku.getSkuId();
                }
                if (StringUtils.hasText(attr.getApilyGoodSkuId())
                        && Objects.equals(attr.getApilyGoodSkuId(), remoteSku.getSkuId())) {
                    return remoteSku.getSkuId();
                }
            }
            return null;
        }
        return StringUtils.hasText(attr.getApilyGoodSkuId()) ? attr.getApilyGoodSkuId() : null;
    }

    /**
     * 过滤出支付宝侧已经存在的 SKU。
     *
     * <p>direct.modify 只能更新远端已存在 SKU，不能新增 SKU。以远端查询到的 out_sku_id / sku_id
     * 为准过滤本地规格，避免把本地新增但支付宝不存在的 SKU 传过去导致整单失败。</p>
     */
    private List<Attr> filterRemoteExistingAttrs(List<Attr> attrs, AlipayOpenAppItemQueryResponse remoteDetail) {
        if (CollectionUtils.isEmpty(attrs) || remoteDetail == null || CollectionUtils.isEmpty(remoteDetail.getSkus())) {
            return Collections.emptyList();
        }
        Map<String, ItemSkuSearchVO> remoteByOutSkuId = new HashMap<>();
        Map<String, ItemSkuSearchVO> remoteBySkuId = new HashMap<>();
        for (ItemSkuSearchVO remoteSku : remoteDetail.getSkus()) {
            if (remoteSku == null) {
                continue;
            }
            if (StringUtils.hasText(remoteSku.getOutSkuId())) {
                remoteByOutSkuId.put(remoteSku.getOutSkuId(), remoteSku);
            }
            if (StringUtils.hasText(remoteSku.getSkuId())) {
                remoteBySkuId.put(remoteSku.getSkuId(), remoteSku);
            }
        }

        List<Attr> matchedAttrs = new ArrayList<>();
        for (Attr attr : attrs) {
            if (attr == null || attr.getAttrId() == null) {
                continue;
            }
            ItemSkuSearchVO remoteSku = remoteByOutSkuId.get(buildOutSkuId(attr));
            if (remoteSku == null && StringUtils.hasText(attr.getApilyGoodSkuId())) {
                remoteSku = remoteBySkuId.get(attr.getApilyGoodSkuId());
            }
            if (remoteSku == null) {
                log.warn("跳过支付宝侧不存在的本地 SKU，goodId={}, attrId={}, outSkuId={}",
                        attr.getGoodId(), attr.getAttrId(), buildOutSkuId(attr));
                continue;
            }
            if (StringUtils.hasText(remoteSku.getSkuId()) && !Objects.equals(attr.getApilyGoodSkuId(), remoteSku.getSkuId())) {
                attr.setApilyGoodSkuId(remoteSku.getSkuId());
                attrMapper.updateById(attr);
            }
            matchedAttrs.add(attr);
        }
        return matchedAttrs;
    }

    /**
     * 构造免审更新 SKU 列表。
     *
     * <p>免审接口用于已完整绑定支付宝 SKU 的商品；SKU 标识同时传支付宝 sku_id 和商家 out_sku_id，
     * 兼容历史回写缺失时支付宝按商家侧 ID 匹配。</p>
     */
    private List<ItemDirectModifySku> buildDirectModifySkus(List<Attr> attrs) {
        List<ItemDirectModifySku> skuList = new ArrayList<>();
        for (Attr attr : attrs) {
            ItemDirectModifySku sku = new ItemDirectModifySku();
            if (StringUtils.hasText(attr.getApilyGoodSkuId())) {
                sku.setSkuId(attr.getApilyGoodSkuId());
            }
            sku.setOutSkuId(buildOutSkuId(attr));
            sku.setSalePrice(resolveSalePriceLong(attr));
            sku.setSaleStatus("DELISTING");
            skuList.add(sku);
        }
        return skuList;
    }

    /**
     * 构造正常免审更新 SKU 列表。
     */
    private List<ItemDirectModifySku> buildDirectModifySkus(
            Good good,
            List<Attr> attrs,
            AlipayOpenAppItemQueryResponse remoteDetail,
            boolean includeRentSkuAttrs
    ) {
        List<ItemDirectModifySku> skuList = new ArrayList<>();
        for (Attr attr : attrs) {
            ItemDirectModifySku sku = new ItemDirectModifySku();
            String remoteSkuId = resolveRemoteSkuId(attr, remoteDetail);
            if (StringUtils.hasText(remoteSkuId)) {
                sku.setSkuId(remoteSkuId);
            }
            sku.setOutSkuId(buildOutSkuId(attr));
            sku.setSalePrice(resolveSalePriceLong(attr));
            if (includeRentSkuAttrs) {
                List<ItemSkuAttrVO> skuAttrs = buildDirectModifySkuAttrs(attr, findRemoteSkuByAttr(attr, remoteDetail));
                if (!CollectionUtils.isEmpty(skuAttrs)) {
                    sku.setSkuAttrs(skuAttrs);
                }
            }
            sku.setSaleStatus(resolveSkuSaleStatus(good, attr));
            skuList.add(sku);
        }
        return skuList;
    }

    /**
     * 免审更新不能变更租赁套餐租期，只沿用支付宝现有租期并重算后台设定价格。
     */
    private List<ItemSkuAttrVO> buildDirectModifySkuAttrs(Attr attr, ItemSkuSearchVO remoteSku) {
        List<Long> remoteDurations = jsonLongValues(extractRentCommodityJson(remoteSku), "duration").stream()
                .filter(duration -> duration != null && duration > 0)
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(remoteDurations)) {
            return Collections.emptyList();
        }
        List<ItemSkuAttrVO> attrs = new ArrayList<>();
        addSkuAttr(attrs, "rent_commodity", "custom", buildRentCommodityJson(attr, remoteDurations));
        return attrs;
    }

    /**
     * 按当前本地 SKU 绑定关系找到支付宝侧 SKU。
     */
    private ItemSkuSearchVO findRemoteSkuByAttr(Attr attr, AlipayOpenAppItemQueryResponse remoteDetail) {
        if (attr == null || remoteDetail == null || CollectionUtils.isEmpty(remoteDetail.getSkus())) {
            return null;
        }
        String outSkuId = buildOutSkuId(attr);
        for (ItemSkuSearchVO remoteSku : remoteDetail.getSkus()) {
            if (remoteSku == null) {
                continue;
            }
            if (StringUtils.hasText(remoteSku.getOutSkuId()) && Objects.equals(outSkuId, remoteSku.getOutSkuId())) {
                return remoteSku;
            }
            if (StringUtils.hasText(attr.getApilyGoodSkuId()) && Objects.equals(attr.getApilyGoodSkuId(), remoteSku.getSkuId())) {
                return remoteSku;
            }
        }
        return null;
    }

    /**
     * 支付宝免审接口有时会把 rent_commodity 判定为租赁套餐租期变更，此时重试不带 SKU 属性。
     */
    private boolean isRentDurationModifyRejected(AlipayResponse response) {
        return response != null
                && StringUtils.hasText(response.getSubMsg())
                && response.getSubMsg().contains("租赁套餐租期不允许修改");
    }

    /**
     * 保存普通商品日历库存。支付宝商品修改接口不允许直接改 SKU 库存，租赁商品库存走日历库存接口。
     */
    private void saveCalendarStocks(String remoteItemId, String outItemId, List<Attr> attrs) throws AlipayApiException {
        if (!StringUtils.hasText(remoteItemId) || CollectionUtils.isEmpty(attrs)) {
            return;
        }
        AlipayOpenAppItemCalendarstockSaveModel model = new AlipayOpenAppItemCalendarstockSaveModel();
        model.setItemId(remoteItemId);
        if (StringUtils.hasText(outItemId) && outItemId.trim().length() >= 4) {
            model.setOutItemId(outItemId.trim());
        }
        model.setSkuCalendarStocks(buildSkuCalendarStocks(attrs));

        AlipayOpenAppItemCalendarstockSaveRequest request = new AlipayOpenAppItemCalendarstockSaveRequest();
        request.setBizContent(new JSONWriter().write(model, true));
        AlipayOpenAppItemCalendarstockSaveResponse response = alipayClientService.execute(request);
        ensureAlipaySuccess(response, "同步支付宝商品日历库存失败");
    }

    /**
     * 日历库存是商品资料同步后的补充步骤，失败不应反向判定商品同步失败。
     */
    private CalendarStockSyncResult saveCalendarStocksSafely(
            String remoteItemId,
            String outItemId,
            List<Attr> attrs,
            SyncConfig config
    ) {
        long startTime = System.currentTimeMillis();
        CalendarStockSyncResult result = new CalendarStockSyncResult();
        result.setSkuCount(attrs == null ? 0 : attrs.size());
        if (config != null && "1".equals(config.getBusinessModel())) {
            result.setStatus("SKIPPED");
            result.setMessage("长租商品不支持日历库存同步，已跳过");
            result.setDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }
        if (!StringUtils.hasText(remoteItemId) || CollectionUtils.isEmpty(attrs)) {
            result.setStatus("SKIPPED");
            result.setMessage("缺少支付宝商品ID或规格，已跳过日历库存同步");
            result.setDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }
        try {
            saveCalendarStocks(remoteItemId, outItemId, attrs);
            result.setStatus("SUCCESS");
            result.setMessage("日历库存同步成功，SKU数=" + attrs.size());
        } catch (Exception ex) {
            result.setStatus("FAILED");
            result.setMessage("日历库存同步失败，不影响商品资料同步");
            result.setErrorMessage(ex.getMessage());
            log.warn("支付宝商品日历库存同步失败，不影响商品资料同步，itemId={}, outItemId={}",
                    remoteItemId, outItemId, ex);
        }
        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 构造 SKU 维度日历库存。
     */
    private List<AppItemSkuCalendarStock> buildSkuCalendarStocks(List<Attr> attrs) {
        List<AppItemSkuCalendarStock> stocks = new ArrayList<>();
        LocalDate startDate = LocalDate.now();
        for (Attr attr : attrs) {
            AppItemSkuCalendarStock skuStock = new AppItemSkuCalendarStock();
            skuStock.setOutSkuId(buildOutSkuId(attr));
            skuStock.setCalendarStocks(buildCalendarStocks(startDate, resolveSkuStock(attr)));
            stocks.add(skuStock);
        }
        return stocks;
    }

    /**
     * 构造连续 120 天的库存。
     */
    private List<AppItemCalendarStock> buildCalendarStocks(LocalDate startDate, Long stockNum) {
        List<AppItemCalendarStock> stocks = new ArrayList<>();
        for (int day = 0; day < CALENDAR_STOCK_DAYS; day++) {
            AppItemCalendarStock stock = new AppItemCalendarStock();
            stock.setDate(startDate.plusDays(day).format(CALENDAR_STOCK_DATE_FORMATTER));
            stock.setStockNum(stockNum);
            stocks.add(stock);
        }
        return stocks;
    }

    /**
     * 构造 SKU 销售属性，保证支付宝能区分不同规格。
     */
    private List<ItemSkuAttrVO> buildSkuAttrs(Attr attr, SyncConfig config) {
        List<ItemSkuAttrVO> attrs = new ArrayList<>();
        addSkuAttr(attrs, "rent_commodity", "custom", buildRentCommodityJson(attr, resolveAlipayRentDurations(attr, config)));
        return attrs;
    }

    /**
     * 追加一条 SKU 属性。
     */
    private void addSkuAttr(List<ItemSkuAttrVO> attrs, String key, String value) {
        addSkuAttr(attrs, key, "spec", value);
    }

    /**
     * 追加一条 SKU 属性。
     */
    private void addSkuAttr(List<ItemSkuAttrVO> attrs, String key, String attrType, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        ItemSkuAttrVO attr = new ItemSkuAttrVO();
        attr.setAttrKey(key);
        attr.setAttrType(attrType);
        attr.setAttrValue(value);
        attrs.add(attr);
    }

    /**
     * 构造支付宝租赁 SKU 自定义属性，沿用旧同步链路的 rent_commodity 结构。
     */
    private String buildRentCommodityJson(Attr attr) {
        return buildRentCommodityJson(attr, resolveRentDurations(attr));
    }

    /**
     * 构造支付宝租赁 SKU 自定义属性。
     */
    private String buildRentCommodityJson(Attr attr, List<Long> durations) {
        long unitSalePrice = resolveSalePriceLong(attr);
        return "{"
                + "\"name\":\"" + jsonEscape(attr == null ? "" : attr.getAttrTitle()) + "\","
                + "\"priceUnit\":\"" + PRICE_UNIT_YUAN + "\","
                + "\"durationUnit\":\"" + DURATION_UNIT_DAY + "\","
                + "\"durationPriceList\":" + buildDurationPriceListJson(durations, unitSalePrice)
                + "}";
    }

    /**
     * 构造租赁套餐租期价格列表。
     */
    private String buildDurationPriceListJson(Attr attr, long unitSalePrice) {
        return buildDurationPriceListJson(resolveRentDurations(attr), unitSalePrice);
    }

    /**
     * 构造租赁套餐租期价格列表。
     */
    private String buildDurationPriceListJson(List<Long> durations, long unitSalePrice) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < durations.size(); i++) {
            long duration = durations.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append("\"duration\":\"").append(duration).append("\",")
                    .append("\"unitSalePrice\":\"").append(unitSalePrice).append("\",")
                    .append("\"totalSalePrice\":\"").append(unitSalePrice * duration).append("\"")
                    .append('}');
        }
        return builder.append(']').toString();
    }

    /**
     * JSON 字符串转义。
     */
    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 按 outItemId 查询远端商品。
     */
    private String queryRemoteItemId(String outItemId) throws AlipayApiException {
        String onlineItemId = queryRemoteItemIdByOutItemId(outItemId, null);
        if (StringUtils.hasText(onlineItemId)) {
            return onlineItemId;
        }
        // 支付宝商品首次提交后处于审核中时，必须传 need_edit_spu=1 才能查到编辑态版本。
        return queryRemoteItemIdByOutItemId(outItemId, 1L);
    }

    /**
     * 按 outItemId 查询远端商品，可选择查询审核中的编辑态版本。
     */
    private String queryRemoteItemIdByOutItemId(String outItemId, Long needEditSpu) throws AlipayApiException {
        AlipayOpenAppItemQueryModel model = new AlipayOpenAppItemQueryModel();
        model.setOutItemId(outItemId);
        if (needEditSpu != null) {
            model.setNeedEditSpu(needEditSpu);
        }

        AlipayOpenAppItemQueryRequest request = new AlipayOpenAppItemQueryRequest();
        request.setBizModel(model);
        AlipayOpenAppItemQueryResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            return null;
        }
        return response.getItemId();
    }

    /**
     * 优先按本地已保存的支付宝平台商品 ID 查远端商品，查不到时再按稳定外部 ID 查询。
     *
     * <p>这里遵循双向同步原则：如果本地已经保存 alipay_goods_id，说明该商品已经和支付宝侧商品绑定，
     * 后续同步应使用平台侧 item_id；没有绑定时按老链路的裸 goodId 作为 out_item_id 查询。</p>
     */
    private String resolveRemoteItemId(Good good, String outItemId) throws AlipayApiException {
        if (StringUtils.hasText(good.getAlipayGoodsId())) {
            AlipayOpenAppItemQueryResponse mappedResponse = queryRemoteItemByItemIdPreferOnline(good.getAlipayGoodsId().trim());
            if (mappedResponse != null && mappedResponse.isSuccess() && StringUtils.hasText(mappedResponse.getItemId())) {
                return mappedResponse.getItemId();
            }
        }
        String remoteItemId = queryRemoteItemId(outItemId);
        if (StringUtils.hasText(remoteItemId)) {
            return remoteItemId;
        }
        return null;
    }

    /**
     * 按支付宝平台侧商品 ID 查询远端商品。
     *
     * @param itemId 支付宝平台侧商品 ID
     * @param needEditSpu 是否查询审核中的编辑态版本，1 表示查询
     * @return 支付宝查询响应；查询失败时仍返回原始响应，供调用方判断
     * @throws AlipayApiException 支付宝 SDK 调用异常
     */
    private AlipayOpenAppItemQueryResponse queryRemoteItemByItemId(String itemId, Long needEditSpu) throws AlipayApiException {
        AlipayOpenAppItemQueryModel model = new AlipayOpenAppItemQueryModel();
        model.setItemId(itemId);
        if (needEditSpu != null) {
            model.setNeedEditSpu(needEditSpu);
        }

        AlipayOpenAppItemQueryRequest request = new AlipayOpenAppItemQueryRequest();
        request.setBizModel(model);
        return alipayClientService.execute(request);
    }

    /**
     * 按支付宝平台商品 ID 查询，优先取线上版本，线上不存在时才回退编辑版本。
     */
    private AlipayOpenAppItemQueryResponse queryRemoteItemByItemIdPreferOnline(String itemId) throws AlipayApiException {
        AlipayOpenAppItemQueryResponse onlineResponse = queryRemoteItemByItemId(itemId, null);
        if (onlineResponse != null && onlineResponse.isSuccess() && StringUtils.hasText(onlineResponse.getItemId())) {
            return onlineResponse;
        }
        return queryRemoteItemByItemId(itemId, 1L);
    }

    /**
     * 回写商品支付宝 ID。
     */
    private void persistItemMapping(Good good, ItemUpsertResult result) {
        if (!StringUtils.hasText(result.getItemId())) {
            return;
        }
        if (Objects.equals(good.getAlipayGoodsId(), result.getItemId())) {
            return;
        }
        good.setAlipayGoodsId(result.getItemId());
        goodMapper.updateById(good);
    }

    /**
     * 回写规格支付宝 SKU ID。
     */
    private void persistSkuMappings(List<Attr> attrs, List<ItemSkuIdPair> skuPairs) {
        if (CollectionUtils.isEmpty(skuPairs)) {
            return;
        }
        Map<String, Attr> attrByOutSkuId = new HashMap<>();
        for (Attr attr : attrs) {
            attrByOutSkuId.put(buildOutSkuId(attr), attr);
        }
        for (ItemSkuIdPair skuPair : skuPairs) {
            Attr attr = attrByOutSkuId.get(skuPair.getOutSkuId());
            if (attr == null || !StringUtils.hasText(skuPair.getSkuId())) {
                continue;
            }
            if (Objects.equals(attr.getApilyGoodSkuId(), skuPair.getSkuId())) {
                continue;
            }
            attr.setApilyGoodSkuId(skuPair.getSkuId());
            attrMapper.updateById(attr);
        }
    }

    /**
     * 确保商品有支付宝图片空间目录。
     */
    private String ensureImageDirectory(Good good) throws AlipayApiException {
        if (good == null) {
            return ROOT_IMAGE_DIRECTORY_ID;
        }
        if (StringUtils.hasText(good.getImageDirectoryId())) {
            return good.getImageDirectoryId().trim();
        }

        AlipayMarketingImagedirectoryCreateModel model = new AlipayMarketingImagedirectoryCreateModel();
        String title = StringUtils.hasText(good.getGoodTitle()) ? good.getGoodTitle().trim() : "商品";
        model.setImageDirectoryName(limitLength(title + good.getGoodId(), 40));
        model.setParentDirectoryId(ROOT_IMAGE_DIRECTORY_ID);

        AlipayMarketingImagedirectoryCreateRequest request = new AlipayMarketingImagedirectoryCreateRequest();
        request.setBizModel(model);
        AlipayMarketingImagedirectoryCreateResponse response = alipayClientService.execute(request);
        ensureAlipaySuccess(response, "创建支付宝图片目录失败");

        String imageDirectoryId = response.getImageDirectoryId();
        if (!StringUtils.hasText(imageDirectoryId)) {
            return ROOT_IMAGE_DIRECTORY_ID;
        }
        good.setImageDirectoryId(imageDirectoryId);
        goodMapper.updateById(good);
        return imageDirectoryId;
    }

    /**
     * 上传图片到支付宝图片空间，并按商品图片场景启用增强处理。
     */
    private String uploadImage(
            String pathOrUrl,
            SyncConfig config,
            Map<String, String> uploadCache,
            String uploadScene,
            String imageDirectoryId
    ) throws AlipayApiException {
        if (!StringUtils.hasText(pathOrUrl)) {
            return null;
        }
        String rawPathOrUrl = pathOrUrl.trim();
        if (rawPathOrUrl.startsWith("A*")) {
            return rawPathOrUrl;
        }
        String sourceUrl = resolveAssetUrl(pathOrUrl, config.getAssetBaseUrl());
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        if (sourceUrl.contains("alipayobjects.com")) {
            return sourceUrl;
        }
        String cacheKey = uploadScene + "|" + sourceUrl;
        String cachedImageId = uploadCache.get(cacheKey);
        if (StringUtils.hasText(cachedImageId)) {
            return cachedImageId;
        }

        try {
            DownloadedImage downloadedImage = downloadImage(sourceUrl);
            DownloadedImage uploadImage = optimizeImageForUpload(downloadedImage, uploadScene, sourceUrl);
            AlipayMarketingImageEnhanceUploadResponse response =
                    executeImageUploadWithRetry(uploadImage, uploadScene, imageDirectoryId, sourceUrl);
            ensureAlipaySuccess(response, "上传支付宝商品图片失败");
            if (!StringUtils.hasText(response.getImageId())) {
                throw new IllegalStateException("上传支付宝商品图片失败：支付宝未返回 imageId，sourceUrl=" + sourceUrl);
            }
            String existingImageId = uploadCache.putIfAbsent(cacheKey, response.getImageId());
            return StringUtils.hasText(existingImageId) ? existingImageId : response.getImageId();
        } catch (AlipayApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("上传图片到支付宝失败：" + sourceUrl, ex);
        }
    }

    /**
     * 执行支付宝图片上传，并对网络读超时做有限重试。
     *
     * @param downloadedImage 已下载的图片内容
     * @param uploadScene 支付宝上传场景
     * @param imageDirectoryId 支付宝图片目录 ID
     * @param sourceUrl 原始图片地址，用于日志定位
     * @return 支付宝图片上传响应
     * @throws AlipayApiException 支付宝 SDK 调用异常
     */
    private AlipayMarketingImageEnhanceUploadResponse executeImageUploadWithRetry(
            DownloadedImage downloadedImage,
            String uploadScene,
            String imageDirectoryId,
            String sourceUrl
    ) throws AlipayApiException {
        AlipayApiException lastException = null;
        for (int attempt = 1; attempt <= IMAGE_UPLOAD_MAX_ATTEMPTS; attempt++) {
            try {
                AlipayMarketingImageEnhanceUploadRequest request =
                        buildImageUploadRequest(downloadedImage, uploadScene, imageDirectoryId);
                return alipayClientService.execute(request);
            } catch (AlipayApiException ex) {
                lastException = ex;
                if (attempt >= IMAGE_UPLOAD_MAX_ATTEMPTS || !isTransientAlipayException(ex)) {
                    throw ex;
                }
                log.warn("支付宝商品图片上传超时，准备重试，scene={}, attempt={}/{}, sourceUrl={}",
                        uploadScene, attempt, IMAGE_UPLOAD_MAX_ATTEMPTS, sourceUrl);
                sleepBeforeImageUploadRetry(attempt);
            }
        }
        throw lastException;
    }

    /**
     * 构造支付宝图片增强上传请求。
     *
     * @param downloadedImage 已下载的图片内容
     * @param uploadScene 支付宝上传场景
     * @param imageDirectoryId 支付宝图片目录 ID
     * @return 图片增强上传请求
     */
    private AlipayMarketingImageEnhanceUploadRequest buildImageUploadRequest(
            DownloadedImage downloadedImage,
            String uploadScene,
            String imageDirectoryId
    ) {
        AlipayMarketingImageEnhanceUploadRequest request = new AlipayMarketingImageEnhanceUploadRequest();
        request.setImageContent(new FileItem(
                downloadedImage.getFileName(),
                downloadedImage.getContent(),
                downloadedImage.getMimeType()
        ));
        request.setUploadScene(uploadScene);
        request.setNeedEnhance(Boolean.TRUE);
        request.setImageDirectoryId(StringUtils.hasText(imageDirectoryId) ? imageDirectoryId : ROOT_IMAGE_DIRECTORY_ID);
        return request;
    }

    /**
     * 支付宝优化上传会先校验主图类图片尺寸，这里先本地补成 750x750 白底图，再交给支付宝增强。
     */
    private DownloadedImage optimizeImageForUpload(
            DownloadedImage image,
            String uploadScene,
            String sourceUrl
    ) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.getContent()));
        if (source == null) {
            log.warn("支付宝商品图片无法本地优化，保持原图上传，scene={}, sourceUrl={}", uploadScene, sourceUrl);
            return image;
        }
        if (!requiresSquareImage(uploadScene)) {
            return optimizeDetailImageForUpload(image, source, uploadScene, sourceUrl);
        }
        int width = source.getWidth();
        int height = source.getHeight();
        if (width == ALIPAY_SQUARE_IMAGE_SIZE && height == ALIPAY_SQUARE_IMAGE_SIZE) {
            return image;
        }

        BufferedImage canvas = new BufferedImage(
                ALIPAY_SQUARE_IMAGE_SIZE,
                ALIPAY_SQUARE_IMAGE_SIZE,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, ALIPAY_SQUARE_IMAGE_SIZE, ALIPAY_SQUARE_IMAGE_SIZE);

            double scale = Math.min(
                    ALIPAY_SQUARE_IMAGE_SIZE / (double) width,
                    ALIPAY_SQUARE_IMAGE_SIZE / (double) height);
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            int x = (ALIPAY_SQUARE_IMAGE_SIZE - targetWidth) / 2;
            int y = (ALIPAY_SQUARE_IMAGE_SIZE - targetHeight) / 2;
            graphics.drawImage(source, x, y, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (!ImageIO.write(canvas, "jpg", outputStream)) {
            log.warn("支付宝商品图片本地优化失败，保持原图上传，scene={}, sourceUrl={}", uploadScene, sourceUrl);
            return image;
        }

        DownloadedImage optimized = new DownloadedImage();
        optimized.setContent(outputStream.toByteArray());
        optimized.setMimeType("image/jpeg");
        optimized.setFileType("jpg");
        optimized.setFileName(buildUploadFileName(sourceUrl + "|" + uploadScene + "|750", "jpg"));
        log.info("支付宝商品图片已本地优化为750x750，scene={}, sourceUrl={}, original={}x{}",
                uploadScene, sourceUrl, width, height);
        return optimized;
    }

    /**
     * 详情图没有固定方图要求，但支付宝图片上传仍限制单文件 5M；超大原图先转 JPG 并限制长边。
     */
    private DownloadedImage optimizeDetailImageForUpload(
            DownloadedImage image,
            BufferedImage source,
            String uploadScene,
            String sourceUrl
    ) throws Exception {
        int width = source.getWidth();
        int height = source.getHeight();
        int longSide = Math.max(width, height);
        if (image.getContent().length <= ALIPAY_IMAGE_MAX_BYTES && longSide <= ALIPAY_DETAIL_IMAGE_MAX_LONG_SIDE) {
            return image;
        }

        double scale = Math.min(1D, ALIPAY_DETAIL_IMAGE_MAX_LONG_SIDE / (double) longSide);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        byte[] optimizedContent = null;
        for (float quality : new float[]{0.88F, 0.78F, 0.68F, 0.58F, 0.48F}) {
            byte[] candidate = writeJpeg(canvas, quality);
            if (optimizedContent == null || candidate.length < optimizedContent.length) {
                optimizedContent = candidate;
            }
            if (candidate.length <= ALIPAY_IMAGE_MAX_BYTES) {
                optimizedContent = candidate;
                break;
            }
        }
        if (optimizedContent == null || optimizedContent.length > ALIPAY_IMAGE_MAX_BYTES) {
            log.warn("支付宝商品详情图压缩后仍超过5M，保持原图上传，scene={}, sourceUrl={}, originalBytes={}",
                    uploadScene, sourceUrl, image.getContent().length);
            return image;
        }

        DownloadedImage optimized = new DownloadedImage();
        optimized.setContent(optimizedContent);
        optimized.setMimeType("image/jpeg");
        optimized.setFileType("jpg");
        optimized.setFileName(buildUploadFileName(sourceUrl + "|" + uploadScene + "|detail", "jpg"));
        log.info("支付宝商品详情图已压缩，scene={}, sourceUrl={}, original={}x{}/{}bytes, target={}x{}/{}bytes",
                uploadScene, sourceUrl, width, height, image.getContent().length,
                targetWidth, targetHeight, optimizedContent.length);
        return optimized;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前 JDK 不支持 JPG 图片写入");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality);
            }
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private boolean requiresSquareImage(String uploadScene) {
        return IMAGE_SCENE_HEAD.equals(uploadScene)
                || IMAGE_SCENE_LIST.equals(uploadScene)
                || IMAGE_SCENE_SKU_THUMB.equals(uploadScene);
    }

    /**
     * 判断支付宝 SDK 异常是否属于可重试的网络瞬断或读超时。
     *
     * @param ex 支付宝 SDK 异常
     * @return true 表示可以重试
     */
    private boolean isTransientAlipayException(AlipayApiException ex) {
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("SocketTimeoutException")
                    || containsAny(message, "Read timed out", "Connection timed out", "connect timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 图片上传重试前短暂等待，避免立即重试继续打满支付宝图片接口。
     *
     * @param attempt 当前重试序号
     */
    private void sleepBeforeImageUploadRetry(int attempt) {
        try {
            Thread.sleep(Math.min(5000L, attempt * IMAGE_UPLOAD_RETRY_BACKOFF_MILLIS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("支付宝商品图片上传重试等待被中断", ex);
        }
    }

    /**
     * 判断文本是否包含任一关键字。
     *
     * @param value 待检查文本
     * @param keywords 关键字列表
     * @return true 表示命中任一关键字
     */
    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从现有 OSS / 公网地址下载图片内容。
     */
    private DownloadedImage downloadImage(String sourceUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(sourceUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "equipment-management-system-sync");

            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new IllegalStateException("下载图片失败，HTTP状态码：" + status);
            }

            byte[] content;
            try (InputStream inputStream = connection.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
                content = outputStream.toByteArray();
            }

            String mimeType = StringUtils.hasText(connection.getContentType())
                    ? connection.getContentType()
                    : "image/jpeg";
            String fileType = resolveFileType(mimeType, sourceUrl);
            String fileName = buildUploadFileName(sourceUrl, fileType);
            DownloadedImage image = new DownloadedImage();
            image.setContent(content);
            image.setMimeType(mimeType);
            image.setFileType(fileType);
            image.setFileName(fileName);
            return image;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 把本地文件 key 转成公网可访问地址。
     */
    private String resolveAssetUrl(String pathOrUrl, String assetBaseUrl) {
        if (!StringUtils.hasText(pathOrUrl)) {
            return null;
        }
        String source = pathOrUrl.trim();
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return source;
        }
        String baseUrl = assetBaseUrl;
        if (!StringUtils.hasText(baseUrl)) {
            return source;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (source.startsWith("/")) {
            source = source.substring(1);
        }
        return baseUrl + "/" + source;
    }

    /**
     * 从富文本中提取图片地址。
     */
    private List<String> extractHtmlImageSources(String html) {
        if (!StringUtils.hasText(html)) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = HTML_IMAGE_PATTERN.matcher(html);
        while (matcher.find()) {
            String src = matcher.group(1);
            if (StringUtils.hasText(src)) {
                result.add(src.trim());
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 构造商品卡片摘要描述。
     */
    private String buildGuideDesc(Good good) {
        List<String> parts = new ArrayList<>();
        addTextPart(parts, good.getGoodDesc());
        addTextPart(parts, good.getGoodAct());
        String merged = String.join(" | ", parts);
        return limitLength(merged, 200);
    }

    /**
     * 构造商品详情文本。
     */
    private String buildDetailText(Good good) {
        List<String> parts = new ArrayList<>();
        addTextPart(parts, good.getGoodDesc());
        addTextPart(parts, good.getGoodAct());
        addTextPart(parts, stripHtml(good.getGoodRec()));
        addTextPart(parts, stripHtml(good.getGoodSpepar()));
        addTextPart(parts, stripHtml(good.getGoodCon()));
        String merged = String.join("\n", deduplicate(parts));
        return limitLength(merged, 2000);
    }

    /**
     * 追加一段文本片段。
     */
    private void addTextPart(List<String> parts, String value) {
        String normalized = normalizeText(value);
        if (StringUtils.hasText(normalized)) {
            parts.add(normalized);
        }
    }

    /**
     * 去除 HTML 标签，保留纯文本。
     */
    private String stripHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }
        String withoutTags = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        return normalizeText(withoutTags);
    }

    /**
     * 标准化文本内容。
     */
    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = MULTI_SPACE_PATTERN.matcher(value.replace('\u00A0', ' ').trim()).replaceAll(" ");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    /**
     * 返回第一段有内容的文本。
     */
    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : "";
    }

    /**
     * 返回图片列表第一张图片。
     */
    private String firstImage(List<String> images) {
        if (CollectionUtils.isEmpty(images)) {
            return "";
        }
        return images.stream().filter(StringUtils::hasText).findFirst().orElse("");
    }

    /**
     * 把支付宝图片列表转成本地轮播图逗号字符串。
     */
    private String buildRemoteSlides(AlipayOpenAppItemQueryResponse detail) {
        if (!CollectionUtils.isEmpty(detail.getImageList())) {
            return detail.getImageList().stream().filter(StringUtils::hasText).collect(Collectors.joining(","));
        }
        return firstText(detail.getHeadImg(), "");
    }

    /**
     * 判断支付宝商品是否可售。
     */
    private boolean isRemoteAvailable(String spuStatus) {
        return !StringUtils.hasText(spuStatus) || "AVAILABLE".equals(spuStatus) || "ONLINE".equals(spuStatus);
    }

    /**
     * Long 安全转 Integer，用于把支付宝价格和库存落到本地 int 字段。
     */
    private Integer toInteger(Long value) {
        if (value == null) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return value.intValue();
    }

    /**
     * 文本去重并保持原顺序。
     */
    private List<String> deduplicate(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    /**
     * 解析逗号分隔字符串。
     */
    private List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 选出主规格，供商品级价格和属性回填使用。
     */
    private Attr selectPrimaryAttr(List<Attr> attrs) {
        return attrs.stream()
                .filter(Objects::nonNull)
                .min((left, right) -> {
                    int minRentCompare = Integer.compare(resolveMinRentDaysForSort(left), resolveMinRentDaysForSort(right));
                    if (minRentCompare != 0) {
                        return minRentCompare;
                    }
                    return Integer.compare(
                            left.getAttrAmount() == null ? Integer.MAX_VALUE : left.getAttrAmount(),
                            right.getAttrAmount() == null ? Integer.MAX_VALUE : right.getAttrAmount());
                })
                .orElse(null);
    }

    private int resolveMinRentDaysForSort(Attr attr) {
        if (attr != null && attr.getMinRent() != null && attr.getMinRent() > 0) {
            return attr.getMinRent();
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 解析价格单位。
     */
    private String resolvePriceUnit(Attr attr) {
        return PRICE_UNIT_PER_DAY;
    }

    /**
     * 解析商品售卖状态。
     */
    private String resolveGoodsSaleStatus(Good good) {
        if (good.getStatus() == 1) {
            return "AVAILABLE";
        }
        return "DELISTING";
    }

    /**
     * 解析 SKU 售卖状态。
     */
    private String resolveSkuSaleStatus(Good good, Attr attr) {
        if (good.getStatus() != 1 || resolveSkuStock(attr) <= 0) {
            return "DELISTING";
        }
        return "AVAILABLE";
    }

    /**
     * 解析是否支持买断，支付宝租赁商品必填。
     */
    private String resolveWhetherBuyout(Attr attr) {
        return attr != null && attr.getBuyout() != null && attr.getBuyout() == 1 ? "1" : "0";
    }

    private String resolveWhetherSupportFreeDeposit(Attr attr) {
        return attr != null && attr.getFree() != null && attr.getFree() == 1 ? "1" : "0";
    }

    private String resolveGuideBuyout(Attr attr) {
        if (!"1".equals(resolveWhetherBuyout(attr))) {
            return "不支持";
        }
        return formatFen(resolveBuyoutPrice(attr));
    }

    private Integer resolveBuyoutPrice(Attr attr) {
        validateAlipaySkuPricing(attr);
        if (attr == null || attr.getBuyout() == null || attr.getBuyout() != 1) {
            return 0;
        }
        if (attr.getBuyoutval() != null && attr.getBuyoutval() > 0) {
            return attr.getBuyoutval();
        }
        return attr.getAttrDeposit() == null ? 0 : Math.max(attr.getAttrDeposit(), 0);
    }

    /**
     * 解析售价。支付宝商品接口 sale_price/original_price 使用分为单位。
     */
    private Long resolveSalePriceLong(Attr attr) {
        validateAlipaySkuPricing(attr);
        return attr.getAttrAmount().longValue();
    }

    /**
     * 解析押金。押金只能用于押金展示字段，不能写入商品售价。
     */
    private Long resolveDepositPriceLong(Attr attr) {
        validateAlipaySkuPricing(attr);
        return attr.getAttrDeposit().longValue();
    }

    private void validateAlipaySkuPricing(Attr attr) {
        if (attr == null || attr.getAttrAmount() == null || attr.getAttrAmount() <= 0) {
            throw new IllegalStateException(buildSkuPricingError(attr, "SKU租金必须大于0"));
        }
        if (attr.getAttrDeposit() == null || attr.getAttrDeposit() <= 0) {
            throw new IllegalStateException(buildSkuPricingError(attr, "SKU押金必须大于0"));
        }
        if (attr.getBuyoutval() == null || attr.getBuyoutval() <= 0) {
            throw new IllegalStateException(buildSkuPricingError(attr, "买断金必须大于0"));
        }
    }

    private String buildSkuPricingError(Attr attr, String message) {
        if (attr == null || attr.getAttrId() == null) {
            return message;
        }
        String title = StringUtils.hasText(attr.getAttrTitle()) ? attr.getAttrTitle() : String.valueOf(attr.getAttrId());
        return "SKU[" + title + "]" + message;
    }

    /**
     * 解析原价。
     */
    private Long resolveOriginalPrice(Attr attr) {
        return resolveSalePriceLong(attr);
    }

    /**
     * 解析单个 SKU 库存。
     */
    private Long resolveSkuStock(Attr attr) {
        long stock = attr != null && attr.getAttrNum() != null ? attr.getAttrNum() : 0L;
        if (stock < 0) {
            stock = 0;
        }
        return Math.min(stock, MAX_STOCK_NUM);
    }

    /**
     * 解析起租天数。
     */
    private long resolveMinRentDays(Attr attr) {
        if (attr != null && attr.getMinRent() != null && attr.getMinRent() > 0) {
            return attr.getMinRent();
        }
        return resolveRentDurations(attr).get(0);
    }

    private long resolveAlipayRentFromNumbersOfDay(Attr attr) {
        return Math.max(resolveMinRentDays(attr), ALIPAY_MIN_RENT_FROM_NUMBERS_OF_DAY);
    }

    /**
     * 解析租期列表，兼容逗号、斜杠、中文顿号和空白分隔。
     */
    private List<Long> resolveRentDurations(Attr attr) {
        LinkedHashSet<Long> durations = new LinkedHashSet<>();
        if (attr != null && attr.getMinRent() != null && attr.getMinRent() > 0) {
            durations.add(attr.getMinRent().longValue());
        }
        if (attr != null && StringUtils.hasText(attr.getAttrRentday())) {
            String[] parts = attr.getAttrRentday().split("[,，/、\\s]+");
            for (String part : parts) {
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                try {
                    long duration = Long.parseLong(part.trim());
                    if (duration > 0) {
                        durations.add(duration);
                    }
                } catch (NumberFormatException ignored) {
                    // 忽略非法租期片段，后续用默认值兜底。
                }
            }
        }
        if (durations.isEmpty()) {
            durations.add(1L);
        }
        return new ArrayList<>(durations);
    }

    private List<Long> resolveAlipayRentDurations(Attr attr, SyncConfig config) {
        List<Long> durations = resolveRentDurations(attr);
        if (config == null || !"1".equals(config.getBusinessModel())) {
            return durations;
        }
        return durations.stream()
                .map(this::normalizeAlipayLongRentDuration)
                .distinct()
                .collect(Collectors.toList());
    }

    private long normalizeAlipayLongRentDuration(Long duration) {
        long target = duration == null || duration <= 0 ? ALIPAY_LONG_RENT_DURATIONS.get(0) : duration;
        for (Long allowed : ALIPAY_LONG_RENT_DURATIONS) {
            if (target <= allowed) {
                return allowed;
            }
        }
        return ALIPAY_LONG_RENT_DURATIONS.get(ALIPAY_LONG_RENT_DURATIONS.size() - 1);
    }

    /**
     * 汇总商品总库存。
     */
    private Long resolveTotalStock(List<Attr> attrs) {
        long total = attrs.stream()
                .map(this::resolveSkuStock)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        return Math.min(total, MAX_STOCK_NUM);
    }

    /**
     * 标准化租期展示。
     */
    private String normalizeSkuRentDay(Attr attr) {
        if (attr == null || !StringUtils.hasText(attr.getAttrRentday())) {
            return String.valueOf(resolveMinRentDays(attr));
        }
        return limitLength(resolveRentDurations(attr).stream()
                .map(String::valueOf)
                .collect(Collectors.joining("/")), 100);
    }

    /**
     * 构造稳定的外部商品 ID。
     */
    private String buildOutItemId(Good good) {
        return String.valueOf(good.getGoodId());
    }

    /**
     * 构造稳定的外部 SKU ID。
     */
    private String buildOutSkuId(Attr attr) {
        return String.valueOf(attr.getAttrId());
    }

    /**
     * 解析本地 ID。支付宝 out_item_id / out_sku_id 按老链路传裸本地 ID。
     */
    private Integer parseLocalId(String value) {
        return Integer.valueOf(value.trim());
    }

    /**
     * 是否需要传自定义详情页路径。
     */
    private boolean shouldSendPath(SyncConfig config) {
        return "0".equals(config.getItemDetailsPageModel())
                && StringUtils.hasText(config.getPathTemplate());
    }

    /**
     * 构造商品详情页路径。
     */
    private String buildGoodsPath(Good good, SyncConfig config) {
        return config.getPathTemplate().replace("{goodId}", String.valueOf(good.getGoodId()));
    }

    /**
     * 分转元字符串。
     */
    private String formatFen(Integer value) {
        if (value == null) {
            return null;
        }
        long yuan = value / 100;
        long fen = Math.abs(value % 100);
        return yuan + "." + String.format(Locale.ROOT, "%02d", fen);
    }

    /**
     * 推断文件类型。
     */
    private String resolveFileType(String mimeType, String sourceUrl) {
        if (StringUtils.hasText(mimeType)) {
            String lowerMime = mimeType.toLowerCase(Locale.ROOT);
            if (lowerMime.contains("png")) {
                return "png";
            }
            if (lowerMime.contains("gif")) {
                return "gif";
            }
            if (lowerMime.contains("webp")) {
                return "webp";
            }
        }
        String lowerUrl = sourceUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains(".png")) {
            return "png";
        }
        if (lowerUrl.contains(".gif")) {
            return "gif";
        }
        if (lowerUrl.contains(".webp")) {
            return "webp";
        }
        return "jpg";
    }

    /**
     * 构造上传文件名。
     */
    private String buildUploadFileName(String sourceUrl, String fileType) {
        String sanitized = sourceUrl.replaceAll("[^a-zA-Z0-9]+", "_");
        byte[] hashBytes = sourceUrl.getBytes(StandardCharsets.UTF_8);
        String hash = String.format("%08x", Arrays.hashCode(hashBytes));
        String extension = StringUtils.hasText(fileType) ? fileType : "jpg";
        int maxBaseLength = 32 - extension.length() - 1;
        String prefix = "gs_" + hash;
        int maxSuffixLength = maxBaseLength - prefix.length() - 1;
        if (maxSuffixLength <= 0) {
            return prefix.substring(0, Math.min(prefix.length(), maxBaseLength)) + "." + extension;
        }
        if (sanitized.length() > maxSuffixLength) {
            sanitized = sanitized.substring(sanitized.length() - maxSuffixLength);
        }
        return prefix + "_" + sanitized + "." + extension;
    }

    /**
     * 截断字符串长度，避免超过支付宝字段限制。
     */
    private String limitLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 统一处理支付宝接口失败信息。
     */
    private void ensureAlipaySuccess(AlipayResponse response, String prefix) {
        if (response == null) {
            throw new IllegalStateException(prefix + "：支付宝未返回响应");
        }
        if (!response.isSuccess()) {
            StringBuilder message = new StringBuilder(prefix);
            if (StringUtils.hasText(response.getSubCode())) {
                message.append("，错误码：").append(response.getSubCode());
            }
            if (StringUtils.hasText(response.getSubMsg())) {
                message.append("，错误信息：").append(response.getSubMsg());
            }
            throw new IllegalStateException(message.toString());
        }
    }

    /**
     * 关闭批量同步线程池。
     */
    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /**
     * 构造批量同步失败摘要。
     */
    private String buildFailureMessage(String phase, Throwable throwable) {
        String message = throwable == null ? "unknown" : throwable.getMessage();
        return phase + ": " + limitLength(message, 300);
    }

    /**
     * 同步配置快照。
     */
    private static class SyncConfig {
        private String assetBaseUrl;
        private String categoryId;
        private String businessModel;
        private String itemDetailsPageModel;
        private String pathTemplate;
        private String servicePhone;
        private String priceUnit;
        private String saleStatus;
        private String outItemId;

        public String getAssetBaseUrl() {
            return assetBaseUrl;
        }

        public void setAssetBaseUrl(String assetBaseUrl) {
            this.assetBaseUrl = assetBaseUrl;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(String categoryId) {
            this.categoryId = categoryId;
        }

        public String getBusinessModel() {
            return businessModel;
        }

        public void setBusinessModel(String businessModel) {
            this.businessModel = businessModel;
        }

        public String getItemDetailsPageModel() {
            return itemDetailsPageModel;
        }

        public void setItemDetailsPageModel(String itemDetailsPageModel) {
            this.itemDetailsPageModel = itemDetailsPageModel;
        }

        public String getPathTemplate() {
            return pathTemplate;
        }

        public void setPathTemplate(String pathTemplate) {
            this.pathTemplate = pathTemplate;
        }

        public String getServicePhone() {
            return servicePhone;
        }

        public void setServicePhone(String servicePhone) {
            this.servicePhone = servicePhone;
        }

        public String getPriceUnit() {
            return priceUnit;
        }

        public void setPriceUnit(String priceUnit) {
            this.priceUnit = priceUnit;
        }

        public String getSaleStatus() {
            return saleStatus;
        }

        public void setSaleStatus(String saleStatus) {
            this.saleStatus = saleStatus;
        }

        public String getOutItemId() {
            return outItemId;
        }

        public void setOutItemId(String outItemId) {
            this.outItemId = outItemId;
        }
    }

    /**
     * 同步后的图片集合。
     */
    private static class SyncedImageBundle {
        private String headImg;
        private List<String> imageList = new ArrayList<>();
        private List<String> descImgs = new ArrayList<>();
        private Map<Integer, String> skuThumbs = new HashMap<>();

        public String getHeadImg() {
            return headImg;
        }

        public void setHeadImg(String headImg) {
            this.headImg = headImg;
        }

        public List<String> getImageList() {
            return imageList;
        }

        public void setImageList(List<String> imageList) {
            this.imageList = imageList;
        }

        public List<String> getDescImgs() {
            return descImgs;
        }

        public void setDescImgs(List<String> descImgs) {
            this.descImgs = descImgs;
        }

        public Map<Integer, String> getSkuThumbs() {
            return skuThumbs;
        }

        public void setSkuThumbs(Map<Integer, String> skuThumbs) {
            this.skuThumbs = skuThumbs;
        }
    }

    /**
     * 商品创建/修改结果。
     */
    private static class ItemUpsertResult {
        private String itemId;
        private List<ItemSkuIdPair> skuPairs;
        private String syncMode;
        private CalendarStockSyncResult calendarStockResult;

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public List<ItemSkuIdPair> getSkuPairs() {
            return skuPairs;
        }

        public void setSkuPairs(List<ItemSkuIdPair> skuPairs) {
            this.skuPairs = skuPairs;
        }

        public String getSyncMode() {
            return syncMode;
        }

        public void setSyncMode(String syncMode) {
            this.syncMode = syncMode;
        }

        public CalendarStockSyncResult getCalendarStockResult() {
            return calendarStockResult;
        }

        public void setCalendarStockResult(CalendarStockSyncResult calendarStockResult) {
            this.calendarStockResult = calendarStockResult;
        }
    }

    /**
     * 日历库存同步结果。
     */
    private static class CalendarStockSyncResult {
        private String status;
        private String message;
        private String errorMessage;
        private Long durationMs;
        private int skuCount;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(Long durationMs) {
            this.durationMs = durationMs;
        }

        public int getSkuCount() {
            return skuCount;
        }

        public void setSkuCount(int skuCount) {
            this.skuCount = skuCount;
        }
    }

    /**
     * 带生命周期详情的商品同步异常。
     */
    private static class GoodsSyncTaskException extends RuntimeException {
        private final List<Map<String, Object>> lifecycle;
        private final String causeMessage;

        GoodsSyncTaskException(Exception cause, List<Map<String, Object>> lifecycle) {
            super(cause == null ? null : cause.getMessage(), cause);
            this.lifecycle = lifecycle;
            this.causeMessage = cause == null ? null : cause.getMessage();
        }

        public String getCauseMessage() {
            return causeMessage;
        }

        public Map<String, Object> toDetailResult(Integer goodId) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("goodId", goodId);
            detail.put("lifecycle", lifecycle);
            detail.put("errorMessage", causeMessage);
            return detail;
        }
    }

    /**
     * 支付宝商品拉回本地的结果。
     */
    private static class RemotePullResult {
        private Integer goodId;
        private boolean createdGood;
        private boolean patchedLocal;
        private int createdSkuCount;

        public Integer getGoodId() {
            return goodId;
        }

        public void setGoodId(Integer goodId) {
            this.goodId = goodId;
        }

        public boolean isCreatedGood() {
            return createdGood;
        }

        public void setCreatedGood(boolean createdGood) {
            this.createdGood = createdGood;
        }

        public boolean isPatchedLocal() {
            return patchedLocal;
        }

        public void setPatchedLocal(boolean patchedLocal) {
            this.patchedLocal = patchedLocal;
        }

        public int getCreatedSkuCount() {
            return createdSkuCount;
        }

        public void setCreatedSkuCount(int createdSkuCount) {
            this.createdSkuCount = createdSkuCount;
        }
    }

    /**
     * 本地商品推送结果。
     */
    private static class LocalPushResult {
        private int pushedCount;
        private int skippedCount;
        private int skippedPulledRemoteCount;

        public int getPushedCount() {
            return pushedCount;
        }

        public void setPushedCount(int pushedCount) {
            this.pushedCount = pushedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public void setSkippedCount(int skippedCount) {
            this.skippedCount = skippedCount;
        }

        public int getSkippedPulledRemoteCount() {
            return skippedPulledRemoteCount;
        }

        public void setSkippedPulledRemoteCount(int skippedPulledRemoteCount) {
            this.skippedPulledRemoteCount = skippedPulledRemoteCount;
        }
    }

    /**
     * 下载后的图片内容。
     */
    private static class DownloadedImage {
        private byte[] content;
        private String mimeType;
        private String fileType;
        private String fileName;

        public byte[] getContent() {
            return content;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
    }
}
