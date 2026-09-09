package com.fly.rent.aftersale.service.impl;

import com.alipay.api.response.AlipayCommerceRentOrderAftersaleConfirmResponse;
import com.alipay.api.response.AlipayCommerceRentOrderAftersaleCreateResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.aftersale.model.RentAftersaleNotifyContext;
import com.fly.rent.aftersale.service.RentDepositDeductService;
import com.fly.rent.aftersale.service.RentAftersaleSyncService;
import com.fly.rent.common.order.RentOrderReletRelationService;
import com.fly.rent.common.support.RentOrderLocator;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.RedisClient;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentAftersaleNotifyRecord;
import com.fly.rent.entity.RentAftersaleSyncSnapshot;
import com.fly.rent.entity.RentDepositDeductRecord;
import com.fly.rent.legacy.service.RentAftersaleService;
import com.fly.rent.legacy.service.RentOrderPayService;
import com.fly.rent.legacy.service.exception.RentAftersaleException;
import com.fly.rent.legacy.service.exception.RentOrderPayException;
import com.fly.rent.mapper.RentAftersaleNotifyRecordMapper;
import com.fly.rent.mapper.RentDepositDeductRecordMapper;
import com.fly.rent.support.util.OrderUtil;
import com.fly.rent.web.support.OperLogContext;
import com.fly.rent.web.support.WebRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 押金扣减领域服务实现。
 *
 * 设计说明：
 * 1. 该类把“后台扣减”和“支付宝异步通知落账”统一为同一个领域服务；
 * 2. Web 层只负责权限、入参和操作日志切面，真正的业务编排都在这里；
 * 3. 通过独立通知流水表 + 业务台账表的双层设计，分别解决“通知幂等”和“业务幂等”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentDepositDeductServiceImpl implements RentDepositDeductService {

    private final RentOrderLocator orderLocator;
    private final RentAftersaleService rentAftersaleService;
    private final RentOrderPayService rentOrderPayService;
    private final RedisClient redisClient;
    private final RentDepositDeductRecordMapper rentDepositDeductRecordMapper;
    private final RentAftersaleNotifyRecordMapper rentAftersaleNotifyRecordMapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final RentOrderReletRelationService reletRelationService;
    private final RentAftersaleSyncService rentAftersaleSyncService;

    /**
     * 后台发起一笔押金赔付/违约金扣减。
     *
     * 流程分成两段：
     * 1. 先本地落一条 PROCESSING 台账，确保后续任何回调/重试都能找到业务主键；
     * 2. 再调用支付宝创建赔付售后单，把返回的 aftersale_id 回写到本地。
     *
     * 文档约束：
     * 1. 后台通过 create 接口创建的赔付售后，属于私域场景；
     * 2. 私域场景的支付必须在售后完结前发起，否则支付宝会拒绝；
     * 3. 因此前端下一步应先触发本地 PAY_COMPENSATION，支付成功后再自动完结售后。
     */
    @Override
    @SneakyThrows
    public Map<String, Object> deductDeposit(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        DeductOrderContext orderContext = resolveDeductOrderContext(order);
        Order depositOrder = orderContext.getDepositOrder();

        Integer deductAmountCent = parseDeductAmountCent(req.requiredText("deductAmount"));
        String feeType = req.requiredText("feeType");
        String reasonCode = req.requiredText("reasonCode");
        String remark = normalizeText(req.text("remark"));
        String operatorId = normalizeText(req.text("operatorId"));
        String operatorName = resolveOperatorName(req.text("operatorName"));

        validateDeductable(order, depositOrder, deductAmountCent, feeType, reasonCode);

        RentDepositDeductRecord unfinishedAftersale = findUnfinishedAftersaleRecord(order, depositOrder);
        if (unfinishedAftersale != null) {
            throw new IllegalArgumentException("支付宝仍有未完结售后，请先补完结售后单: "
                    + displayAftersaleIdentity(unfinishedAftersale));
        }

        // 先做“同订单 + 同金额 + 同原因”的幂等回看，避免后台误点导致重复发起售后。
        RentDepositDeductRecord existingRecord = findLatestRetryableRecord(order.getOrderId(), feeType, reasonCode, deductAmountCent);
        if (existingRecord != null) {
            if (isStaleOrphanAftersaleRecord(existingRecord)) {
                RentDepositDeductRecord recovered = rentAftersaleSyncService.recoverDuplicateCreate(depositOrder, existingRecord);
                if (recovered != null && hasText(recovered.getAftersaleNo())) {
                    return toDeductConfirmResult(order, recovered);
                }
                markDeductRecordFailed(existingRecord, new IllegalArgumentException("未找到支付宝售后单号，已关闭本地处理中记录，请重新发起扣减"));
            } else {
                return toDeductConfirmResult(order, existingRecord);
            }
        }

        if (existingRecord != null && RentDepositDeductRecord.STATUS_PROCESSING.equals(existingRecord.getStatus())) {
            return toDeductConfirmResult(order, existingRecord);
        }

        String lockKey = "deposit_deduct_lock:" + order.getOrderId();
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisClient.tryLock(lockKey, lockValue, 2, TimeUnit.MINUTES);
        if (!locked) {
            throw new IllegalArgumentException("该订单正在扣减押金，请勿重复提交");
        }

        int beforeRemainingDeposit = currentRemainingDeposit(depositOrder);
        String outRequestNo = buildDeductRequestNo(order, "DD");
        String outAftersaleId = buildDeductRequestNo(order, "AS");
        String confirmRequestNo = buildDeductRequestNo(order, "CF");
        String outTradeNo = buildDeductRequestNo(order, "TP");

        RentDepositDeductRecord record = buildDeductRecord(order, depositOrder, deductAmountCent, beforeRemainingDeposit,
                feeType, reasonCode, remark, operatorId, operatorName, outRequestNo, outAftersaleId, confirmRequestNo, outTradeNo);
        rentDepositDeductRecordMapper.insert(record);

        try {
            AlipayCommerceRentOrderAftersaleCreateResponse createResponse = rentAftersaleService.createCompensationAftersale(
                    depositOrder, feeType, reasonCode, deductAmountCent, outAftersaleId, remark);
            markAftersaleCreated(record, createResponse);

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("orderId", order.getOrderId());
            result.put("orderNo", order.getOrderNo());
            result.put("depositOrderId", depositOrder.getOrderId());
            result.put("depositOrderNo", depositOrder.getOrderNo());
            result.put("deductSource", orderContext.getDeductSource());
            result.put("deductRecordId", record.getId());
            result.put("deductAmount", deductAmountCent);
            result.put("feeType", feeType);
            result.put("reasonCode", reasonCode);
            result.put("remainingDeposit", depositOrder.getOrderRestDeposit());
            result.put("authNo", depositOrder.getOrderAuthNo());
            result.put("tradeNo", record.getTradeNo());
            result.put("aftersaleNo", record.getAftersaleNo());
            result.put("outAftersaleId", record.getOutAftersaleId());
            result.put("outTradeNo", record.getOutTradeNo());
            result.put("status", record.getStatus());
            // 私域赔付售后先发起押金转支付，支付成功后再自动完结售后。
            result.put("nextAction", AlipayRentConstants.AFTERSALE_OPERATION_PAY_COMPENSATION);
            OperLogContext.setResultBody(result);
            return result;
        } catch (Exception ex) {
            if (isDuplicateAftersaleCreate(ex)) {
                RentDepositDeductRecord recovered = rentAftersaleSyncService.recoverDuplicateCreate(depositOrder, record);
                if (recovered != null) {
                    Map<String, Object> result = toDeductConfirmResult(order, recovered);
                    result.put("recovered", true);
                    result.put("message", "已找到支付宝售后单，请继续处理");
                    OperLogContext.setResultBody(result);
                    return result;
                }
                IllegalArgumentException duplicateTip = new IllegalArgumentException(
                        "支付宝已存在未完结售后，请先同步售后并点击“补完结售后”，不要重新发起扣款");
                markDeductRecordFailed(record, duplicateTip);
                throw duplicateTip;
            }
            markDeductRecordFailed(record, ex);
            throw ex;
        } finally {
            redisClient.releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 查询某个订单下的所有扣减记录。
     * 该方法只做视图转换，不做业务推断，保证后台看到的是原始账务轨迹。
     */
    @Override
    public List<Map<String, Object>> deductRecords(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            orderId = req.raw().get("orderNo");
        }
        Order order = locateOrder(orderId);
        DeductOrderContext orderContext = resolveDeductOrderContext(order);
        List<RentDepositDeductRecord> records = rentDepositDeductRecordMapper.selectList(buildDeductRecordQuery(order, orderContext.getDepositOrder()));
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (RentDepositDeductRecord record : records) {
            result.add(toDeductRecordView(record));
        }
        return result;
    }

    /**
     * 分页查询订单下的扣减记录。
     * Web 后台列表默认只取当前页，避免订单详情抽屉一次性加载过长台账。
     */
    @Override
    public Map<String, Object> deductRecordsPage(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            orderId = req.raw().get("orderNo");
        }
        Order order = locateOrder(orderId);
        DeductOrderContext orderContext = resolveDeductOrderContext(order);
        Page<RentDepositDeductRecord> page = new Page<RentDepositDeductRecord>(req.page(), req.limit());
        rentDepositDeductRecordMapper.selectPage(page, buildDeductRecordQuery(order, orderContext.getDepositOrder()));

        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        for (RentDepositDeductRecord record : page.getRecords()) {
            records.add(toDeductRecordView(record));
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("list", records);
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        return result;
    }

    /**
     * 后台继续推进一条已经创建的售后扣减单。
     *
     * 支持的动作：
     * 1. 私域（MERCHANT）：`PAY_COMPENSATION` / `AFTERSALE_FINISH` / `USER_CANCEL_APPLY`；
     * 2. 公域（ZHIMA_RENT）：`MERCHANT_APPROVE` / `MERCHANT_REJECT` / `APPROVE_WITH_USER_PAY`。
     *
     * 其中私域 `PAY_COMPENSATION` 是本地动作：
     * 1. 先调用 order.pay 发起押金转支付；
     * 2. 等支付成功回调后再自动执行 AFTERSALE_FINISH。
     */
    @Override
    @SneakyThrows
    public Map<String, Object> confirmDeductRecord(WebRequest req) {
        Integer recordId = req.integer("recordId");
        if (recordId == null) {
            throw new IllegalArgumentException("recordId 不能为空");
        }
        String operationType = req.requiredText("operationType");
        String overrideReasonCode = normalizeText(req.text("reasonCode"));
        String overrideRemark = normalizeText(req.text("remark"));

        RentDepositDeductRecord record = rentDepositDeductRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("扣减记录不存在");
        }
        Order order = locateOrder(record.getOrderId());
        return confirmExistingDeductRecord(order, record, operationType, overrideReasonCode, overrideRemark);
    }

    /**
     * 处理支付宝售后通知。
     *
     * 核心原则：
     * 1. notify_id 先落库，再做业务处理，先拿到通知幂等；
     * 2. 业务处理只定位并更新“赔付扣减台账”，不直接操作其他不相关模块；
     * 3. 售后通知只负责同步官方售后快照，不再单独认定“资金已到账”。
     */
    @Override
    public void handleAftersaleNotify(RentAftersaleNotifyContext context) {
        if (context == null || !hasText(context.getNotifyId())) {
            throw new IllegalArgumentException("售后通知缺少 notifyId");
        }

        RentAftersaleNotifyRecord notifyRecord = acquireNotifyRecord(context);
        if (RentAftersaleNotifyRecord.PROCESS_STATUS_SUCCESS.equals(notifyRecord.getProcessStatus())) {
            return;
        }

        try {
            RentDepositDeductRecord record = locateDeductRecordByAftersale(context);
            if (record == null) {
                throw new IllegalArgumentException("未找到匹配的售后扣减记录");
            }

            syncAftersaleSnapshot(record, context);

            if (AlipayRentConstants.AFTERSALE_OPERATION_USER_CANCEL_APPLY.equals(context.getOperationType())) {
                record.setStatus(RentDepositDeductRecord.STATUS_CANCELLED);
                record.setAfterRemainingDeposit(record.getBeforeRemainingDeposit());
                record.setUpdateTime(new Date());
                rentDepositDeductRecordMapper.updateById(record);
            } else if (AlipayRentConstants.AFTERSALE_STATUS_FAIL.equals(context.getAftersaleStatus())) {
                record.setStatus(RentDepositDeductRecord.STATUS_FAILED);
                record.setUpdateTime(new Date());
                rentDepositDeductRecordMapper.updateById(record);
            } else {
                // 售后 SUCCESS 只代表售后流程到达官方终态，不等于赔付资金已经到账；
                // 本地押金扣账必须以赔付支付成功通知为准，这里只同步快照。
                record.setUpdateTime(new Date());
                rentDepositDeductRecordMapper.updateById(record);
            }

            markNotifyRecordSuccess(notifyRecord, null);
        } catch (Exception ex) {
            markNotifyRecordFailed(notifyRecord, ex);
            throw ex;
        }
    }

    /**
     * 处理赔付支付成功通知。
     *
     * 返回值的设计是为了让支付通知路由器知道：
     * - `true`：这条通知已经被识别为“赔付支付”，普通租金支付逻辑就不要再处理；
     * - `false`：这不是赔付支付，应该继续走租金支付逻辑。
     */
    @Override
    public boolean handleCompensationPaySuccess(String outTradeNo,
                                                String outOrderId,
                                                String tradeNo,
                                                Integer payAmountCent,
                                                String payloadJson) {
        RentDepositDeductRecord record = findDeductRecordByOutTradeNo(outTradeNo);
        if (record == null) {
            // 赔付支付只允许按预先生成的 out_trade_no 精确认领，避免把同订单同金额的普通租金支付误认成赔付。
            log.warn("未匹配到赔付支付记录，忽略赔付落账: outTradeNo={}, outOrderId={}, payAmountCent={}",
                    outTradeNo, outOrderId, payAmountCent);
            return false;
        }

        // 这里不依赖售后通知先后顺序，支付成功后即可把业务台账推进到最终成功。
        persistDeductSuccessIfNeeded(record.getId(), tradeNo, record.getAftersaleStatus(),
                record.getLastOperationType(), record.getLastNotifyId());

        if (hasText(payloadJson)) {
            log.info("赔付支付通知已落账: recordId={}, outTradeNo={}", record.getId(), record.getOutTradeNo());
        }
        finalizeMerchantAftersaleAfterPay(record.getId());
        return true;
    }

    private RentAftersaleNotifyRecord acquireNotifyRecord(RentAftersaleNotifyContext context) {
        RentAftersaleNotifyRecord existing = rentAftersaleNotifyRecordMapper.selectOne(
                new QueryWrapper<RentAftersaleNotifyRecord>().eq("notify_id", context.getNotifyId()).last("LIMIT 1")
        );
        if (existing != null) {
            return existing;
        }

        RentAftersaleNotifyRecord created = new RentAftersaleNotifyRecord();
        created.setNotifyId(context.getNotifyId());
        created.setMsgMethod(context.getMsgMethod());
        created.setOrderId(context.getOrderId());
        created.setOutOrderId(context.getOutOrderId());
        created.setAftersaleId(context.getAftersaleId());
        created.setOutAftersaleId(context.getOutAftersaleId());
        created.setAftersaleStatus(context.getAftersaleStatus());
        created.setOperationType(context.getOperationType());
        created.setPayloadJson(context.getPayloadJson());
        created.setProcessStatus(RentAftersaleNotifyRecord.PROCESS_STATUS_PROCESSING);
        created.setCreateTime(System.currentTimeMillis());
        created.setUpdateTime(new Date());
        rentAftersaleNotifyRecordMapper.insert(created);
        return created;
    }

    private void markNotifyRecordSuccess(RentAftersaleNotifyRecord notifyRecord, String failReason) {
        notifyRecord.setProcessStatus(RentAftersaleNotifyRecord.PROCESS_STATUS_SUCCESS);
        notifyRecord.setFailReason(failReason);
        notifyRecord.setUpdateTime(new Date());
        rentAftersaleNotifyRecordMapper.updateById(notifyRecord);
    }

    private void markNotifyRecordFailed(RentAftersaleNotifyRecord notifyRecord, Exception ex) {
        notifyRecord.setProcessStatus(RentAftersaleNotifyRecord.PROCESS_STATUS_FAILED);
        notifyRecord.setFailReason(ex.getMessage() != null ? ex.getMessage() : "售后通知处理失败");
        notifyRecord.setUpdateTime(new Date());
        rentAftersaleNotifyRecordMapper.updateById(notifyRecord);
    }

    private RentDepositDeductRecord locateDeductRecordByAftersale(RentAftersaleNotifyContext context) {
        if (hasText(context.getOutAftersaleId())) {
            RentDepositDeductRecord byOutAftersale = rentDepositDeductRecordMapper.selectOne(
                    new QueryWrapper<RentDepositDeductRecord>()
                            .eq("out_aftersale_id", context.getOutAftersaleId())
                            .last("LIMIT 1")
            );
            if (byOutAftersale != null) {
                return byOutAftersale;
            }
        }
        if (hasText(context.getAftersaleId())) {
            return rentDepositDeductRecordMapper.selectOne(
                    new QueryWrapper<RentDepositDeductRecord>()
                            .eq("aftersale_no", context.getAftersaleId())
                            .last("LIMIT 1")
            );
        }
        return null;
    }

    private void syncAftersaleSnapshot(RentDepositDeductRecord record, RentAftersaleNotifyContext context) {
        if (hasText(context.getAftersaleId())) {
            record.setAftersaleNo(context.getAftersaleId());
        }
        if (hasText(context.getOutAftersaleId())) {
            record.setOutAftersaleId(context.getOutAftersaleId());
        }
        record.setAftersaleStatus(context.getAftersaleStatus());
        record.setLastOperationType(context.getOperationType());
        record.setSourceType(context.getSourceType());
        record.setNeedOperation(context.getNeedOperation());
        record.setLastNotifyId(context.getNotifyId());
        record.setUpdateTime(new Date());
        rentDepositDeductRecordMapper.updateById(record);
    }

    private RentDepositDeductRecord findDeductRecordByOutTradeNo(String outTradeNo) {
        if (!hasText(outTradeNo)) {
            return null;
        }
        return rentDepositDeductRecordMapper.selectOne(
                new QueryWrapper<RentDepositDeductRecord>()
                        .eq("out_trade_no", outTradeNo)
                        .last("LIMIT 1")
        );
    }

    private Map<String, Object> confirmExistingDeductRecord(Order order,
                                                            RentDepositDeductRecord record,
                                                            String operationType,
                                                            String overrideReasonCode,
                                                            String overrideRemark) throws Exception {
        Order alipayAftersaleOrder = resolveDepositAccountOrder(order);
        if (RentDepositDeductRecord.STATUS_SUCCESS.equals(record.getStatus())
                && !shouldAllowSuccessRecordFinish(record, operationType)) {
            return toDeductConfirmResult(order, record);
        }
        if (!hasText(record.getAftersaleNo()) && hasText(record.getOutAftersaleId())) {
            RentDepositDeductRecord recovered = rentAftersaleSyncService.recoverDuplicateCreate(alipayAftersaleOrder, record);
            if (recovered != null) {
                record = recovered;
            }
        }
        if (!hasText(record.getAftersaleNo())) {
            throw new IllegalArgumentException("当前记录尚未创建售后单，无法继续确认");
        }

        // 官方文档要求私域和公域的可操作动作不同，这里在服务层统一兜底校验。
        validateConfirmOperationType(record, operationType);
        String confirmReasonCode = resolveConfirmReasonCode(operationType, overrideReasonCode, record.getReasonCode());
        String confirmRemark = hasText(overrideRemark) ? overrideRemark : record.getRemark();

        String lockKey = "deposit_deduct_lock:" + alipayAftersaleOrder.getOrderId();
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisClient.tryLock(lockKey, lockValue, 2, TimeUnit.MINUTES);
        if (!locked) {
            throw new IllegalArgumentException("该订单正在处理扣减确认，请稍后再试");
        }
        try {
            if (AlipayRentConstants.AFTERSALE_OPERATION_PAY_COMPENSATION.equals(operationType)) {
                record = prepareCompensationPayAttempt(alipayAftersaleOrder, record, operationType);

                Order latestAftersaleOrder = orderLocator.requireByIdentifier(String.valueOf(alipayAftersaleOrder.getOrderId()));
                String payTradeNo = rentOrderPayService.payCompensation(latestAftersaleOrder, record);
                persistCompensationPayLaunched(latestAftersaleOrder, record, payTradeNo, operationType);
                Map<String, Object> result = toDeductConfirmResult(order, record);
                OperLogContext.setResultBody(result);
                return result;
            }

            boolean merchantFinishRetry = shouldSkipMerchantAftersaleConfirm(record, operationType);
            AlipayCommerceRentOrderAftersaleConfirmResponse confirmResponse = null;
            if (!merchantFinishRetry) {
                confirmResponse = rentAftersaleService.confirmCompensationAftersale(
                        alipayAftersaleOrder, operationType, record.getFeeType(), confirmReasonCode, record.getDeductAmount(),
                        record.getAftersaleNo(), record.getOutAftersaleId(), record.getOutTradeNo(), confirmRemark);
            }

            RentAftersaleSyncSnapshot refreshedAftersale = refreshAftersaleAfterFinish(alipayAftersaleOrder, record, operationType);
            persistDeductConfirmResult(order, record, operationType, confirmResponse, confirmReasonCode, refreshedAftersale);
            Map<String, Object> result = toDeductConfirmResult(order, record);
            appendAftersaleFinishResult(result, operationType, refreshedAftersale);
            OperLogContext.setResultBody(result);
            return result;
        } catch (Exception ex) {
            if (shouldAllowSuccessRecordFinish(record, operationType)) {
                markAftersaleFinishFailed(record, ex);
            } else {
                markDeductRecordFailed(record, ex);
            }
            throw ex;
        } finally {
            redisClient.releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 准备私域赔付支付发起记录。
     *
     * 支付宝的外部交易号一旦被 order.pay 接口消费，即使接口返回“没有可用的支付工具”等失败结果，
     * 后续再次用同一个 out_trade_no 也可能被判定为“外部交易号已存在”。这里仅在上一轮明确失败且没有
     * 支付宝 trade_no 的场景下刷新外部支付单号，避免误改仍可能到账的支付流水。
     *
     * @param order 本地订单，用于生成新的赔付支付外部单号。
     * @param record 当前扣减台账。
     * @param operationType 本次后台动作，固定为 PAY_COMPENSATION。
     * @return 已持久化并同步最新外部支付单号的扣减台账。
     */
    private RentDepositDeductRecord prepareCompensationPayAttempt(Order order,
                                                                  RentDepositDeductRecord record,
                                                                  String operationType) {
        return transactionTemplate.execute(status -> {
            RentDepositDeductRecord latestRecord = rentDepositDeductRecordMapper.selectById(record.getId());
            if (latestRecord == null) {
                throw new IllegalArgumentException("扣减记录不存在");
            }

            if (shouldRenewCompensationOutTradeNo(latestRecord)) {
                String oldOutTradeNo = latestRecord.getOutTradeNo();
                String newOutTradeNo = buildDeductRequestNo(order, "TP");
                latestRecord.setOutTradeNo(newOutTradeNo);
                log.info("赔付记录{}重试扣款，刷新外部支付单号: {} -> {}",
                        latestRecord.getId(), oldOutTradeNo, newOutTradeNo);
            }

            latestRecord.setLastOperationType(operationType);
            latestRecord.setUpdateTime(new Date());
            rentDepositDeductRecordMapper.updateById(latestRecord);
            return latestRecord;
        });
    }

    /**
     * 判断本次赔付支付重试是否需要刷新 out_trade_no。
     *
     * 只处理支付宝已消耗外部交易号但未产生 trade_no 的失败重试；如果已有 trade_no，说明支付流水已创建，
     * 必须继续等待或补偿同步，不能换号导致回调无法认领。
     *
     * @param record 当前扣减台账。
     * @return true 表示重试前需要生成新的赔付支付外部单号。
     */
    private boolean shouldRenewCompensationOutTradeNo(RentDepositDeductRecord record) {
        if (record == null
                || !RentDepositDeductRecord.STATUS_FAILED.equals(record.getStatus())
                || hasText(record.getTradeNo())
                || !AlipayRentConstants.AFTERSALE_OPERATION_PAY_COMPENSATION.equals(record.getLastOperationType())) {
            return false;
        }

        // 没有产生支付宝 trade_no 的失败重试，一律换新的 out_trade_no：
        // 1. 这类失败（PAYMENT_FAIL 扣款失败、外部交易号已存在、没有可用支付工具等）支付宝侧没有成功的支付流水，
        //    复用旧 out_trade_no 反而容易撞「外部交易号已存在」；
        // 2. 既然没有 trade_no，就不存在「换号导致回调认领不到」的风险，可以安全换号重试。
        String subCode = record.getAlipaySubCode();
        if (hasText(subCode) && "PAYMENT_FAIL".equalsIgnoreCase(subCode.trim())) {
            return true;
        }
        String subMsg = record.getAlipaySubMsg();
        if (!hasText(subMsg)) {
            // 失败但没有任何 subMsg 时，保守起见也换号重试，避免卡在旧外部交易号上。
            return true;
        }
        String lowerSubMsg = subMsg.toLowerCase(Locale.ROOT);
        return subMsg.contains("外部交易号已存在")
                || subMsg.contains("没有可用的支付工具")
                || subMsg.contains("重复提交")
                || subMsg.contains("扣款失败")
                || lowerSubMsg.contains("payment_fail")
                || lowerSubMsg.contains("out_trade_no");
    }

    private Order persistDeductConfirmResult(Order order,
                                              RentDepositDeductRecord record,
                                              String operationType,
                                              AlipayCommerceRentOrderAftersaleConfirmResponse confirmResponse,
                                              String confirmReasonCode,
                                              RentAftersaleSyncSnapshot refreshedAftersale) {
        return transactionTemplate.execute(status -> {
            Order latestOrder = orderLocator.requireByIdentifier(String.valueOf(order.getOrderId()));
            record.setLastOperationType(operationType);
            record.setAftersaleStatus(AlipayRentConstants.AFTERSALE_STATUS_APPROVING);
            record.setReasonCode(confirmReasonCode);
            if (confirmResponse != null && hasText(confirmResponse.getTradeNo())) {
                record.setTradeNo(confirmResponse.getTradeNo());
            }
            if (confirmResponse != null) {
                record.setAlipaySubCode(confirmResponse.getSubCode());
                record.setAlipaySubMsg(confirmResponse.getSubMsg());
            }
            record.setUpdateTime(new Date());

            // 对不同动作分别更新本地状态，但只有赔付支付成功回调才能真正扣本地押金。
            if (AlipayRentConstants.AFTERSALE_OPERATION_APPROVE_WITH_USER_PAY.equals(operationType)
                    || AlipayRentConstants.AFTERSALE_OPERATION_MERCHANT_APPROVE.equals(operationType)) {
                record.setStatus(RentDepositDeductRecord.STATUS_PROCESSING);
            } else if (AlipayRentConstants.AFTERSALE_OPERATION_MERCHANT_REJECT.equals(operationType)) {
                record.setStatus(RentDepositDeductRecord.STATUS_FAILED);
            } else if (AlipayRentConstants.AFTERSALE_OPERATION_USER_CANCEL_APPLY.equals(operationType)) {
                record.setStatus(RentDepositDeductRecord.STATUS_CANCELLED);
                record.setAfterRemainingDeposit(record.getBeforeRemainingDeposit());
            } else if (AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)) {
                // AFTERSALE_FINISH 现在只用于人工补同步或支付成功后的自动收口，不承担扣款动作。
                applyRefreshedAftersaleStatus(record, refreshedAftersale);
                if (!RentDepositDeductRecord.STATUS_SUCCESS.equals(record.getStatus())) {
                    record.setStatus(RentDepositDeductRecord.STATUS_PROCESSING);
                }
            }

            rentDepositDeductRecordMapper.updateById(record);
            return latestOrder;
        });
    }

    private RentAftersaleSyncSnapshot refreshAftersaleAfterFinish(Order alipayAftersaleOrder,
                                                                  RentDepositDeductRecord record,
                                                                  String operationType) {
        if (!AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)) {
            return null;
        }
        try {
            return rentAftersaleSyncService.refreshSingleAftersale(
                    alipayAftersaleOrder,
                    record.getAftersaleNo(),
                    record.getOutAftersaleId()
            );
        } catch (Exception ex) {
            log.warn("补完结售后后刷新支付宝快照失败: recordId={}, aftersaleNo={}, outAftersaleId={}, msg={}",
                    record.getId(), record.getAftersaleNo(), record.getOutAftersaleId(), ex.getMessage());
            return null;
        }
    }

    private void applyRefreshedAftersaleStatus(RentDepositDeductRecord record,
                                               RentAftersaleSyncSnapshot refreshedAftersale) {
        if (refreshedAftersale == null) {
            record.setAftersaleStatus(AlipayRentConstants.AFTERSALE_STATUS_APPROVING);
            record.setAlipaySubMsg("支付宝已受理售后完结请求，远端状态待同步确认");
            return;
        }
        record.setAftersaleStatus(refreshedAftersale.getAftersaleStatus());
        record.setSourceType(refreshedAftersale.getSourceType());
        record.setNeedOperation(refreshedAftersale.getNeedOperation());
        if (isRemoteAftersaleFinished(refreshedAftersale)) {
            record.setAlipaySubMsg("支付宝售后已完结");
        } else {
            record.setAlipaySubMsg("支付宝已受理售后完结请求，但当前查询仍为"
                    + displayRemoteAftersaleStatus(refreshedAftersale)
                    + "，请稍后再次同步或重试补完结");
        }
    }

    private void appendAftersaleFinishResult(Map<String, Object> result,
                                             String operationType,
                                             RentAftersaleSyncSnapshot refreshedAftersale) {
        if (!AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)) {
            return;
        }
        result.put("remoteAftersaleStatus", refreshedAftersale == null ? null : refreshedAftersale.getAftersaleStatus());
        result.put("remoteAftersaleFinished", refreshedAftersale == null ? null : refreshedAftersale.getFinished());
        boolean finished = isRemoteAftersaleFinished(refreshedAftersale);
        result.put("aftersaleFinishConfirmed", finished);
        result.put("message", finished
                ? "支付宝售后已完结"
                : "完结请求已提交，但支付宝查询仍未完结，请稍后同步或再次补完结");
    }

    /**
     * 私域售后在 AFTERSALE_FINISH 成功后，还要显式调用一次 order.pay 才会真正触发押金转支付。
     *
     * 这里单独落库支付发起结果，避免把“售后成功”和“支付已成功”混成同一个状态。
     */
    private Order persistCompensationPayLaunched(Order order,
                                                 RentDepositDeductRecord record,
                                                 String payTradeNo,
                                                 String operationType) {
        return transactionTemplate.execute(status -> {
            Order latestOrder = orderLocator.requireByIdentifier(String.valueOf(order.getOrderId()));
            RentDepositDeductRecord latestRecord = rentDepositDeductRecordMapper.selectById(record.getId());
            if (latestRecord == null) {
                throw new IllegalArgumentException("扣减记录不存在");
            }

            if (hasText(payTradeNo)) {
                latestRecord.setTradeNo(payTradeNo);
            }
            latestRecord.setStatus(RentDepositDeductRecord.STATUS_PROCESSING);
            latestRecord.setLastOperationType(operationType);
            latestRecord.setAlipaySubCode(null);
            latestRecord.setAlipaySubMsg(null);
            latestRecord.setUpdateTime(new Date());
            rentDepositDeductRecordMapper.updateById(latestRecord);

            record.setTradeNo(latestRecord.getTradeNo());
            record.setStatus(latestRecord.getStatus());
            record.setLastOperationType(latestRecord.getLastOperationType());
            record.setUpdateTime(latestRecord.getUpdateTime());
            return latestOrder;
        });
    }

    private void persistDeductSuccessIfNeeded(Integer recordId,
                                              String tradeNo,
                                              String aftersaleStatus,
                                              String operationType,
                                              String notifyId) {
        transactionTemplate.execute(status -> {
            RentDepositDeductRecord latestRecord = rentDepositDeductRecordMapper.selectById(recordId);
            if (latestRecord == null) {
                throw new IllegalArgumentException("扣减记录不存在");
            }
            Order latestOrder = orderLocator.requireByIdentifier(String.valueOf(latestRecord.getOrderId()));
            Order depositOrder = resolveDepositAccountOrder(latestOrder);

            if (RentDepositDeductRecord.STATUS_SUCCESS.equals(latestRecord.getStatus())) {
                // 如果台账已经成功，仅补充交易号 / 通知号 / 售后状态，不重复扣账。
                if (hasText(tradeNo) && !hasText(latestRecord.getTradeNo())) {
                    latestRecord.setTradeNo(tradeNo);
                }
                if (hasText(aftersaleStatus)) {
                    latestRecord.setAftersaleStatus(aftersaleStatus);
                }
                if (hasText(operationType)) {
                    latestRecord.setLastOperationType(operationType);
                }
                if (hasText(notifyId)) {
                    latestRecord.setLastNotifyId(notifyId);
                }
                latestRecord.setUpdateTime(new Date());
                rentDepositDeductRecordMapper.updateById(latestRecord);
                return null;
            }

            if (hasText(aftersaleStatus)) {
                latestRecord.setAftersaleStatus(aftersaleStatus);
            }
            if (hasText(operationType)) {
                latestRecord.setLastOperationType(operationType);
            }
            if (hasText(notifyId)) {
                latestRecord.setLastNotifyId(notifyId);
            }
            persistDeductSuccessInsideTransaction(depositOrder, latestRecord, tradeNo);
            rentDepositDeductRecordMapper.updateById(latestRecord);
            return null;
        });
    }

    private void persistDeductSuccessInsideTransaction(Order latestOrder,
                                                       RentDepositDeductRecord latestRecord,
                                                       String tradeNo) {
        int beforeRemaining = currentRemainingDeposit(latestOrder);
        int afterRemaining = beforeRemaining - latestRecord.getDeductAmount();
        if (afterRemaining < 0) {
            throw new IllegalArgumentException("扣减后剩余押金不能为负数");
        }

        latestOrder.setOrderRestDeposit(afterRemaining);
        latestOrder.setUpdateTime(new Date());
        latestOrder.setRestAmount(OrderUtil.convertCentToYuan(afterRemaining).toString());
        latestOrder.setRestFundAmount(OrderUtil.convertCentToYuan(afterRemaining).toString());
        latestOrder.setTotalPayAmount(addYuanAmount(latestOrder.getTotalPayAmount(), latestRecord.getDeductAmount()));
        latestOrder.setTotalPayFundAmount(addYuanAmount(latestOrder.getTotalPayFundAmount(), latestRecord.getDeductAmount()));
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_INDEMNITY.equals(latestRecord.getFeeType())) {
            latestOrder.setOrderDamage((latestOrder.getOrderDamage() == null ? 0 : latestOrder.getOrderDamage()) + latestRecord.getDeductAmount());
        } else if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(latestRecord.getFeeType())) {
            latestOrder.setOrderBeamo((latestOrder.getOrderBeamo() == null ? 0 : latestOrder.getOrderBeamo()) + latestRecord.getDeductAmount());
        }
        latestOrder.updateById();

        latestRecord.setAfterRemainingDeposit(afterRemaining);
        latestRecord.setStatus(RentDepositDeductRecord.STATUS_SUCCESS);
        latestRecord.setAftersaleStatus(AlipayRentConstants.AFTERSALE_STATUS_SUCCESS);
        if (hasText(tradeNo)) {
            latestRecord.setTradeNo(tradeNo);
        }
        latestRecord.setUpdateTime(new Date());
    }

    private void validateConfirmOperationType(RentDepositDeductRecord record, String operationType) {
        if (isMerchantAftersale(record)) {
            // 私域售后由商家自行创建；后台按钮先走本地 PAY_COMPENSATION，再由支付成功后自动完结售后。
            if (AlipayRentConstants.AFTERSALE_OPERATION_PAY_COMPENSATION.equals(operationType)
                    || AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)
                    || AlipayRentConstants.AFTERSALE_OPERATION_USER_CANCEL_APPLY.equals(operationType)) {
                return;
            }
            throw new IllegalArgumentException("私域赔付售后仅支持 PAY_COMPENSATION、AFTERSALE_FINISH 或 USER_CANCEL_APPLY");
        }

        // 公域售后来自芝麻租赁阵地，需要商家按通知做审核动作。
        if (AlipayRentConstants.AFTERSALE_OPERATION_APPROVE_WITH_USER_PAY.equals(operationType)
                || AlipayRentConstants.AFTERSALE_OPERATION_MERCHANT_APPROVE.equals(operationType)
                || AlipayRentConstants.AFTERSALE_OPERATION_MERCHANT_REJECT.equals(operationType)) {
            return;
        }
        throw new IllegalArgumentException("公域售后仅支持 MERCHANT_APPROVE、MERCHANT_REJECT、APPROVE_WITH_USER_PAY");
    }

    private String resolveConfirmReasonCode(String operationType, String overrideReasonCode, String recordReasonCode) {
        if (AlipayRentConstants.AFTERSALE_OPERATION_APPROVE_WITH_USER_PAY.equals(operationType)) {
            return recordReasonCode;
        }
        if (AlipayRentConstants.AFTERSALE_OPERATION_MERCHANT_REJECT.equals(operationType)) {
            if (!hasText(overrideReasonCode)) {
                throw new IllegalArgumentException("MERCHANT_REJECT 必须传 reasonCode");
            }
            validateRejectReasonCode(overrideReasonCode);
            return overrideReasonCode;
        }
        return overrideReasonCode;
    }

    private void validateRejectReasonCode(String reasonCode) {
        if (AlipayRentConstants.AFTERSALE_REASON_GOODS_DELIVERED.equals(reasonCode)
                || AlipayRentConstants.AFTERSALE_REASON_BUYER_AGREED.equals(reasonCode)
                || AlipayRentConstants.AFTERSALE_REASON_OTHER.equals(reasonCode)) {
            return;
        }
        throw new IllegalArgumentException("MERCHANT_REJECT 的 reasonCode 不合法: " + reasonCode);
    }

    private Integer parseDeductAmountCent(String amountText) {
        BigDecimal amount = new BigDecimal(amountText.trim());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("扣减金额必须大于0");
        }
        return OrderUtil.convertYuanToCent(amount.stripTrailingZeros().toPlainString());
    }

    private void validateDeductable(Order order,
                                    Order depositOrder,
                                    Integer deductAmountCent,
                                    String feeType,
                                    String reasonCode) {
        if (!hasText(order.getRentOrderId())) {
            throw new IllegalArgumentException("订单缺少交易组件订单号，无法发起赔付售后");
        }
        if (!hasText(order.getUserUuid())) {
            throw new IllegalArgumentException("订单缺少买家支付宝用户ID，无法发起赔付售后");
        }
        if (AlipayRentConstants.STATUS_FINISHED.equals(order.getAlipayStatus())
                || AlipayRentConstants.STATUS_CLOSED.equals(order.getAlipayStatus())) {
            throw new IllegalArgumentException("当前订单状态不允许扣减押金: " + order.getAlipayStatus());
        }
        validateFeeTypeAndReasonCode(feeType, reasonCode);
        int remaining = currentRemainingDeposit(depositOrder);
        if (remaining <= 0) {
            throw new IllegalArgumentException("该订单已无可扣减押金");
        }
        if (deductAmountCent > remaining) {
            throw new IllegalArgumentException("扣减金额不能超过剩余押金");
        }
    }

    private int currentRemainingDeposit(Order order) {
        if (order.getOrderRestDeposit() != null) {
            return order.getOrderRestDeposit().intValue();
        }
        return order.getOrderDeposit() != null ? order.getOrderDeposit().intValue() : 0;
    }

    /**
     * 解析本次扣押金涉及的业务订单和押金记账订单。
     * 续租子单本身不重新冻结押金，因此后台从续租单入口扣款时，支付宝售后与押金转支付都按最初冻结押金的根单发起，
     * 本地响应保留 RELET 来源，避免把“操作入口是续租单”和“押金授权归属是原单”混在一起。
     *
     * @param order 后台当前操作的订单
     * @return 扣押金订单上下文
     */
    private DeductOrderContext resolveDeductOrderContext(Order order) {
        Order depositOrder = resolveDepositAccountOrder(order);
        String deductSource = order != null && depositOrder != null
                && !order.getOrderId().equals(depositOrder.getOrderId()) ? "RELET" : "ORIGIN";
        return new DeductOrderContext(order, depositOrder, deductSource);
    }

    /**
     * 解析承载押金余额的订单。
     * 如果当前订单是续租子单，则优先用续租关系表里的 originRentOrderId 找到根单；
     * 根单缺失时回退当前订单，避免异常关系数据影响历史普通订单继续扣押金。
     *
     * @param order 当前业务订单
     * @return 承载本地押金余额的订单
     */
    private Order resolveDepositAccountOrder(Order order) {
        if (order == null || !hasText(order.getOrderNo())) {
            return order;
        }
        com.fly.rent.entity.RentOrderReletRelation relation = reletRelationService.findByChildOrderNo(order.getOrderNo());
        if (relation == null) {
            return order;
        }
        Order originOrder = null;
        if (hasText(relation.getOriginRentOrderId())) {
            originOrder = orderLocator.findByIdentifier(relation.getOriginRentOrderId());
        }
        if (originOrder == null && hasText(relation.getParentOrderNo())) {
            originOrder = orderLocator.findByIdentifier(relation.getParentOrderNo());
        }
        if (originOrder == null) {
            return order;
        }
        return originOrder;
    }

    /**
     * 查询押金扣减记录。
     * 续租链共用同一笔押金授权，因此后台查看原单或续租单时，都会返回同一授权号下的扣减记录，
     * 方便在任一订单详情里核对完整扣款轨迹。
     *
     * @param order 当前查看的订单
     * @param depositOrder 承载押金余额的订单
     * @return 扣减记录查询条件
     */
    private QueryWrapper<RentDepositDeductRecord> buildDeductRecordQuery(Order order, Order depositOrder) {
        QueryWrapper<RentDepositDeductRecord> wrapper = new QueryWrapper<RentDepositDeductRecord>();
        wrapper.eq("order_id", order.getOrderId());
        if (depositOrder != null && hasText(depositOrder.getOrderAuthNo())) {
            wrapper.or().eq("auth_no", depositOrder.getOrderAuthNo());
        }
        wrapper.orderByDesc("id");
        return wrapper;
    }

    private RentDepositDeductRecord findUnfinishedAftersaleRecord(Order order, Order depositOrder) {
        List<RentDepositDeductRecord> records = rentDepositDeductRecordMapper.selectList(buildDeductRecordQuery(order, depositOrder));
        for (RentDepositDeductRecord record : records) {
            if (isUnfinishedAftersaleRecord(record)) {
                return record;
            }
        }
        return null;
    }

    private boolean isUnfinishedAftersaleRecord(RentDepositDeductRecord record) {
        if (record == null
                || RentDepositDeductRecord.STATUS_CANCELLED.equals(record.getStatus())
                || RentDepositDeductRecord.STATUS_FAILED.equals(record.getStatus())
                || AlipayRentConstants.AFTERSALE_STATUS_FAIL.equals(record.getAftersaleStatus())) {
            return false;
        }
        if (AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(record.getLastOperationType())
                && AlipayRentConstants.AFTERSALE_STATUS_SUCCESS.equals(record.getAftersaleStatus())) {
            return false;
        }
        return hasText(record.getAftersaleNo()) || hasText(record.getOutAftersaleId());
    }

    private String displayAftersaleIdentity(RentDepositDeductRecord record) {
        if (record == null) {
            return "-";
        }
        if (hasText(record.getAftersaleNo())) {
            return record.getAftersaleNo();
        }
        if (hasText(record.getOutAftersaleId())) {
            return record.getOutAftersaleId();
        }
        return String.valueOf(record.getId());
    }

    private void validateFeeTypeAndReasonCode(String feeType, String reasonCode) {
        Set<String> indemnityReasons = new HashSet<String>();
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_DAMAGED);
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_REPAIR);
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_LOST);
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_DEPRECIATION);

        Set<String> lateFeeReasons = new HashSet<String>();
        lateFeeReasons.add(AlipayRentConstants.AFTERSALE_REASON_RETURN_EARLY);
        lateFeeReasons.add(AlipayRentConstants.AFTERSALE_REASON_RETURN_OVERDUE);

        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_INDEMNITY.equals(feeType)) {
            if (!indemnityReasons.contains(reasonCode)) {
                throw new IllegalArgumentException("赔付金原因码不合法: " + reasonCode);
            }
            return;
        }
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(feeType)) {
            if (!lateFeeReasons.contains(reasonCode)) {
                throw new IllegalArgumentException("违约金原因码不合法: " + reasonCode);
            }
            return;
        }
        throw new IllegalArgumentException("费用类型不合法: " + feeType);
    }

    private String buildDeductRequestNo(Order order, String suffix) {
        return order.getOrderNo() + suffix + System.currentTimeMillis();
    }

    private String resolveOperatorName(String operatorName) {
        String normalized = normalizeText(operatorName);
        return normalized != null ? normalized : "ADMIN";
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RentDepositDeductRecord buildDeductRecord(Order order,
                                                      Order depositOrder,
                                                      Integer deductAmountCent,
                                                      int beforeRemainingDeposit,
                                                      String feeType,
                                                      String reasonCode,
                                                      String remark,
                                                      String operatorId,
                                                      String operatorName,
                                                      String outRequestNo,
                                                      String outAftersaleId,
                                                      String confirmRequestNo,
                                                      String outTradeNo) {
        RentDepositDeductRecord record = new RentDepositDeductRecord();
        record.setOrderId(order.getOrderId());
        record.setOrderNo(order.getOrderNo());
        record.setAuthNo(depositOrder.getOrderAuthNo());
        record.setOutAftersaleId(outAftersaleId);
        record.setConfirmRequestNo(confirmRequestNo);
        record.setOutRequestNo(outRequestNo);
        record.setOutTradeNo(outTradeNo);
        record.setFeeType(feeType);
        record.setReasonCode(reasonCode);
        record.setDeductAmount(deductAmountCent);
        record.setBeforeRemainingDeposit(beforeRemainingDeposit);
        record.setReason(resolveReasonText(feeType));
        record.setRemark(remark);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        // 后台主动创建的赔付售后属于私域场景，后续动作必须按私域规则限制。
        record.setSourceType(AlipayRentConstants.AFTERSALE_SOURCE_MERCHANT);
        record.setStatus(RentDepositDeductRecord.STATUS_PROCESSING);
        record.setAftersaleStatus(AlipayRentConstants.AFTERSALE_STATUS_APPROVING);
        record.setCreateTime(System.currentTimeMillis());
        record.setUpdateTime(new Date());
        return record;
    }

    private String resolveReasonText(String feeType) {
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_INDEMNITY.equals(feeType)) {
            return "赔付金";
        }
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(feeType)) {
            return "违约金";
        }
        return "扣减押金";
    }

    private void markAftersaleCreated(RentDepositDeductRecord record, AlipayCommerceRentOrderAftersaleCreateResponse createResponse) {
        record.setAftersaleNo(createResponse.getAftersaleId());
        record.setOutAftersaleId(createResponse.getOutAftersaleId());
        record.setAftersaleStatus(AlipayRentConstants.AFTERSALE_STATUS_APPROVING);
        record.setUpdateTime(new Date());
        rentDepositDeductRecordMapper.updateById(record);
    }

    private RentDepositDeductRecord findLatestRetryableRecord(Integer orderId,
                                                              String feeType,
                                                              String reasonCode,
                                                              Integer deductAmountCent) {
        List<RentDepositDeductRecord> records = rentDepositDeductRecordMapper.selectList(
                new QueryWrapper<RentDepositDeductRecord>()
                        .eq("order_id", orderId)
                        .eq("fee_type", feeType)
                        .eq("reason_code", reasonCode)
                        .eq("deduct_amount", deductAmountCent)
                        .in("status", RentDepositDeductRecord.STATUS_PROCESSING, RentDepositDeductRecord.STATUS_FAILED)
                        .orderByDesc("id")
                        .last("LIMIT 5")
        );
        for (RentDepositDeductRecord record : records) {
            if (RentDepositDeductRecord.STATUS_PROCESSING.equals(record.getStatus())
                    && (hasText(record.getAftersaleNo()) || hasText(record.getOutAftersaleId()))) {
                return record;
            }
            if (RentDepositDeductRecord.STATUS_FAILED.equals(record.getStatus()) && hasText(record.getAftersaleNo())) {
                return record;
            }
        }
        return null;
    }

    private boolean isStaleOrphanAftersaleRecord(RentDepositDeductRecord record) {
        if (record == null
                || !RentDepositDeductRecord.STATUS_PROCESSING.equals(record.getStatus())
                || hasText(record.getAftersaleNo())
                || !hasText(record.getOutAftersaleId())
                || record.getCreateTime() == null) {
            return false;
        }
        return System.currentTimeMillis() - record.getCreateTime() > TimeUnit.MINUTES.toMillis(2);
    }

    private void markDeductRecordFailed(RentDepositDeductRecord record, Exception ex) {
        RentDepositDeductRecord latestRecord = record;
        if (record != null && record.getId() != null) {
            RentDepositDeductRecord selected = rentDepositDeductRecordMapper.selectById(record.getId());
            if (selected != null) {
                latestRecord = selected;
            }
        }
        latestRecord.setStatus(RentDepositDeductRecord.STATUS_FAILED);
        if (ex instanceof RentOrderPayException) {
            RentOrderPayException payEx = (RentOrderPayException) ex;
            latestRecord.setAlipaySubCode(payEx.getSubCode());
            latestRecord.setAlipaySubMsg(payEx.getOperatorMessage());
        } else if (ex instanceof RentAftersaleException) {
            RentAftersaleException depositEx = (RentAftersaleException) ex;
            latestRecord.setAlipaySubCode(depositEx.getSubCode());
            latestRecord.setAlipaySubMsg(depositEx.getSubMsg());
        } else {
            latestRecord.setAlipaySubCode(null);
            latestRecord.setAlipaySubMsg(ex.getMessage() != null ? ex.getMessage() : "扣减失败");
        }
        if (!hasText(latestRecord.getAftersaleNo())) {
            latestRecord.setAftersaleStatus(AlipayRentConstants.AFTERSALE_STATUS_FAIL);
        }
        latestRecord.setUpdateTime(new Date());
        rentDepositDeductRecordMapper.updateById(latestRecord);
        record.setStatus(latestRecord.getStatus());
        record.setAftersaleStatus(latestRecord.getAftersaleStatus());
        record.setAlipaySubCode(latestRecord.getAlipaySubCode());
        record.setAlipaySubMsg(latestRecord.getAlipaySubMsg());
        record.setUpdateTime(latestRecord.getUpdateTime());
    }

    private void markAftersaleFinishFailed(RentDepositDeductRecord record, Exception ex) {
        if (record == null || record.getId() == null) {
            return;
        }
        RentDepositDeductRecord latestRecord = rentDepositDeductRecordMapper.selectById(record.getId());
        if (latestRecord == null) {
            return;
        }
        if (ex instanceof RentAftersaleException) {
            RentAftersaleException aftersaleEx = (RentAftersaleException) ex;
            latestRecord.setAlipaySubCode(aftersaleEx.getSubCode());
            latestRecord.setAlipaySubMsg("扣款已成功，补完结售后失败: " + aftersaleEx.getSubMsg());
        } else {
            latestRecord.setAlipaySubCode(null);
            latestRecord.setAlipaySubMsg("扣款已成功，补完结售后失败: " + (ex.getMessage() != null ? ex.getMessage() : "未知错误"));
        }
        latestRecord.setUpdateTime(new Date());
        rentDepositDeductRecordMapper.updateById(latestRecord);
        record.setAlipaySubCode(latestRecord.getAlipaySubCode());
        record.setAlipaySubMsg(latestRecord.getAlipaySubMsg());
        record.setUpdateTime(latestRecord.getUpdateTime());
    }

    private boolean isDuplicateAftersaleCreate(Exception ex) {
        if (!(ex instanceof RentAftersaleException)) {
            return false;
        }
        RentAftersaleException aftersaleException = (RentAftersaleException) ex;
        return containsDuplicateText(aftersaleException.getSubCode())
                || containsDuplicateText(aftersaleException.getSubMsg())
                || containsDuplicateText(aftersaleException.getMessage());
    }

    private boolean containsDuplicateText(String text) {
        if (!hasText(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return text.contains("重复提交") || text.contains("重复") || normalized.contains("duplicate");
    }

    private Map<String, Object> toDeductRecordView(RentDepositDeductRecord record) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", record.getId());
        view.put("orderId", record.getOrderId());
        view.put("orderNo", record.getOrderNo());
        view.put("authNo", record.getAuthNo());
        view.put("aftersaleNo", record.getAftersaleNo());
        view.put("outAftersaleId", record.getOutAftersaleId());
        view.put("aftersaleStatus", record.getAftersaleStatus());
        view.put("lastOperationType", record.getLastOperationType());
        view.put("sourceType", record.getSourceType());
        view.put("needOperation", record.getNeedOperation());
        view.put("confirmRequestNo", record.getConfirmRequestNo());
        view.put("outTradeNo", record.getOutTradeNo());
        view.put("tradeNo", record.getTradeNo());
        view.put("feeType", record.getFeeType());
        view.put("reasonCode", record.getReasonCode());
        view.put("deductAmount", record.getDeductAmount());
        view.put("beforeRemainingDeposit", record.getBeforeRemainingDeposit());
        view.put("afterRemainingDeposit", record.getAfterRemainingDeposit());
        view.put("remark", record.getRemark());
        view.put("status", record.getStatus());
        view.put("alipaySubCode", record.getAlipaySubCode());
        view.put("alipaySubMsg", record.getAlipaySubMsg());
        view.put("lastNotifyId", record.getLastNotifyId());
        view.put("createTime", record.getCreateTime());
        return view;
    }

    private Map<String, Object> toDeductConfirmResult(Order order, RentDepositDeductRecord record) {
        DeductOrderContext orderContext = resolveDeductOrderContext(order);
        Order depositOrder = orderContext.getDepositOrder();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("depositOrderId", depositOrder.getOrderId());
        result.put("depositOrderNo", depositOrder.getOrderNo());
        result.put("deductSource", orderContext.getDeductSource());
        result.put("recordId", record.getId());
        result.put("status", record.getStatus());
        result.put("aftersaleStatus", record.getAftersaleStatus());
        result.put("remainingDeposit", depositOrder.getOrderRestDeposit());
        result.put("authNo", depositOrder.getOrderAuthNo());
        result.put("paymentTradeNo", order.getPaymentTradeNo());
        result.put("aftersaleNo", record.getAftersaleNo());
        result.put("outAftersaleId", record.getOutAftersaleId());
        result.put("tradeNo", record.getTradeNo());
        result.put("outTradeNo", record.getOutTradeNo());
        result.put("lastOperationType", record.getLastOperationType());
        result.put("sourceType", record.getSourceType());
        result.put("needOperation", record.getNeedOperation());
        result.put("alipaySubCode", record.getAlipaySubCode());
        result.put("alipaySubMsg", record.getAlipaySubMsg());
        result.put("beforeRemainingDeposit", record.getBeforeRemainingDeposit());
        result.put("afterRemainingDeposit", record.getAfterRemainingDeposit());
        return result;
    }

    /**
     * 扣押金订单上下文。
     * businessOrder 表示后台当前操作的订单，可能是原单也可能是续租子单；
     * depositOrder 表示本地押金余额实际归属的订单，续租链路下通常是最初的根单。
     */
    private static class DeductOrderContext {
        /**
         * 后台当前操作的业务订单。
         */
        private final Order businessOrder;
        /**
         * 本地押金余额归属订单。
         */
        private final Order depositOrder;
        /**
         * 扣款来源：ORIGIN 表示原单入口，RELET 表示续租单入口。
         */
        private final String deductSource;

        /**
         * 创建扣押金上下文。
         *
         * @param businessOrder 后台当前操作的业务订单
         * @param depositOrder 本地押金余额归属订单
         * @param deductSource 扣款来源标记
         */
        private DeductOrderContext(Order businessOrder, Order depositOrder, String deductSource) {
            this.businessOrder = businessOrder;
            this.depositOrder = depositOrder == null ? businessOrder : depositOrder;
            this.deductSource = deductSource;
        }

        /**
         * 获取后台当前操作的业务订单。
         *
         * @return 业务订单
         */
        private Order getBusinessOrder() {
            return businessOrder;
        }

        /**
         * 获取本地押金余额归属订单。
         *
         * @return 押金记账订单
         */
        private Order getDepositOrder() {
            return depositOrder;
        }

        /**
         * 获取扣款来源标记。
         *
         * @return ORIGIN 或 RELET
         */
        private String getDeductSource() {
            return deductSource;
        }
    }

    private boolean isMerchantAftersale(RentDepositDeductRecord record) {
        // 缺省按私域处理，兼容历史后台创建但尚未收到通知回写 source_type 的记录。
        return !AlipayRentConstants.AFTERSALE_SOURCE_ZHIMA_RENT.equals(record.getSourceType());
    }

    /**
     * 私域售后如果已经自动或人工做过 AFTERSALE_FINISH，就不要重复调售后确认接口。
     */
    private boolean shouldSkipMerchantAftersaleConfirm(RentDepositDeductRecord record, String operationType) {
        return isMerchantAftersale(record)
                && AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)
                && AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(record.getLastOperationType())
                && AlipayRentConstants.AFTERSALE_STATUS_SUCCESS.equals(record.getAftersaleStatus());
    }

    private boolean shouldAllowSuccessRecordFinish(RentDepositDeductRecord record, String operationType) {
        return isMerchantAftersale(record)
                && AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)
                && RentDepositDeductRecord.STATUS_SUCCESS.equals(record.getStatus());
    }

    private boolean isRemoteAftersaleFinished(RentAftersaleSyncSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return Boolean.TRUE.equals(snapshot.getFinished())
                || AlipayRentConstants.AFTERSALE_STATUS_SUCCESS.equals(snapshot.getAftersaleStatus());
    }

    private String displayRemoteAftersaleStatus(RentAftersaleSyncSnapshot snapshot) {
        if (snapshot == null || !hasText(snapshot.getAftersaleStatus())) {
            return "未知状态";
        }
        return snapshot.getAftersaleStatus();
    }

    /**
     * 私域赔付扣款成功后，再补做售后完结，保证售后状态最终收口。
     *
     * 自动完结失败不能反向影响支付成功，所以这里仅记录异常，留给人工补偿处理。
     */
    private void finalizeMerchantAftersaleAfterPay(Integer recordId) {
        try {
            RentDepositDeductRecord record = rentDepositDeductRecordMapper.selectById(recordId);
            if (record == null || !isMerchantAftersale(record)) {
                return;
            }
            if (!hasText(record.getAftersaleNo())
                    || AlipayRentConstants.AFTERSALE_STATUS_SUCCESS.equals(record.getAftersaleStatus())
                    || RentDepositDeductRecord.STATUS_CANCELLED.equals(record.getStatus())) {
                return;
            }

            Order order = orderLocator.requireByIdentifier(String.valueOf(record.getOrderId()));
            rentAftersaleService.confirmCompensationAftersale(
                    order,
                    AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH,
                    record.getFeeType(),
                    record.getReasonCode(),
                    record.getDeductAmount(),
                    record.getAftersaleNo(),
                    record.getOutAftersaleId(),
                    record.getOutTradeNo(),
                    record.getRemark()
            );

            RentAftersaleSyncSnapshot refreshedAftersale = refreshAftersaleAfterFinish(
                    order,
                    record,
                    AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH
            );
            applyRefreshedAftersaleStatus(record, refreshedAftersale);
            record.setLastOperationType(AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH);
            record.setUpdateTime(new Date());
            rentDepositDeductRecordMapper.updateById(record);
        } catch (Exception ex) {
            log.error("赔付支付成功后自动完结售后失败: recordId={}, msg={}", recordId, ex.getMessage(), ex);
            RentDepositDeductRecord latestRecord = rentDepositDeductRecordMapper.selectById(recordId);
            if (latestRecord != null) {
                latestRecord.setAlipaySubMsg("支付成功，但自动完结售后失败: " + ex.getMessage());
                latestRecord.setUpdateTime(new Date());
                rentDepositDeductRecordMapper.updateById(latestRecord);
            }
        }
    }

    private String addYuanAmount(String existing, Integer amountCent) {
        BigDecimal current = BigDecimal.ZERO;
        if (existing != null && !existing.trim().isEmpty()) {
            try {
                current = new BigDecimal(existing.trim());
            } catch (NumberFormatException ignore) {
                current = BigDecimal.ZERO;
            }
        }
        return current.add(OrderUtil.convertCentToYuan(amountCent)).toString();
    }

    private Order locateOrder(Object orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        return orderLocator.requireByIdentifier(String.valueOf(orderId));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
