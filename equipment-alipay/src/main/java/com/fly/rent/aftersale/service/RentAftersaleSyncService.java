package com.fly.rent.aftersale.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayCommerceRentOrderQueryModel;
import com.alipay.api.domain.RentAftersaleFundInfoVO;
import com.alipay.api.domain.RentAftersaleFundItemVO;
import com.alipay.api.domain.RentAftersaleOperationRecordVO;
import com.alipay.api.domain.RentAftersaleOrderVO;
import com.alipay.api.request.AlipayCommerceRentOrderQueryRequest;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentAftersaleSyncSnapshot;
import com.fly.rent.entity.RentDepositDeductRecord;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentAftersaleSyncSnapshotMapper;
import com.fly.rent.mapper.RentDepositDeductRecordMapper;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.fly.rent.support.util.OrderUtil;
import com.fly.rent.web.support.WebRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝租赁售后同步服务。
 *
 * 该服务只通过 `alipay.commerce.rent.order.query` 拉取订单上的售后单快照，
 * 先保存到快照表供后台核对，再由管理员手动导入到本地扣减台账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentAftersaleSyncService {

    private static final String QUERY_OPTION_AFTERSALE = "aftersale_info";
    private static final String QUERY_OPTION_STATEMENT = "statement_info";
    private static final String NEED_OPERATION_YES = "Y";
    private static final String NEED_OPERATION_NO = "N";

    private final OrderMapper orderMapper;
    private final RentAftersaleSyncSnapshotMapper snapshotMapper;
    private final RentDepositDeductRecordMapper deductRecordMapper;
    private final AlipayClientService alipayClientService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final AlipayPlatformConfigService alipayPlatformConfigService;

    /**
     * 预览单个订单的支付宝售后差异。
     *
     * @param req 请求参数，需包含 orderId 或 orderNo
     * @return 订单、售后差异列表和统计信息
     */
    public Map<String, Object> preview(WebRequest req) throws AlipayApiException {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            orderId = req.raw().get("orderNo");
        }
        Order order = locateOrder(orderId);
        List<Map<String, Object>> rows = syncOrderAftersales(order, normalizeText(req.text("aftersaleStatus")));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("order", toOrderView(order));
        result.put("list", rows);
        result.put("summary", buildSummary(rows, 1, 0));
        return result;
    }

    /**
     * 按本地订单分页扫描支付宝售后。
     *
     * @param req 请求参数，可包含 orderNo、alipayStatus、aftersaleStatus、start、end、page、limit
     * @return 当前订单页对应的支付宝售后差异列表和统计信息
     */
    public Map<String, Object> scan(WebRequest req) {
        Page<Order> page = new Page<Order>(req.page(), req.limit());
        QueryWrapper<Order> wrapper = new QueryWrapper<Order>();
        if (req.hasText("orderNo")) {
            wrapper.like("order_no", req.text("orderNo"));
        }
        if (req.hasText("alipayStatus") && !"-1".equals(req.text("alipayStatus"))) {
            wrapper.eq("alipay_status", req.text("alipayStatus"));
        }

        Long end = req.longValue("end");
        Long start = req.longValue("start");
        long now = System.currentTimeMillis();
        if (end == null || end <= 0) {
            end = now;
        }
        if (start == null || start <= 0) {
            start = end - defaultScanRangeMillis();
        }
        wrapper.ge("created_at", start);
        wrapper.le("created_at", end);
        wrapper.isNotNull("source_id").ne("source_id", "");
        wrapper.orderByDesc("created_at");
        orderMapper.selectPage(page, wrapper);

        String aftersaleStatus = normalizeText(req.text("aftersaleStatus"));
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        int errorCount = 0;
        for (Order order : page.getRecords()) {
            try {
                rows.addAll(syncOrderAftersales(order, aftersaleStatus));
            } catch (Exception ex) {
                errorCount++;
                rows.add(buildErrorView(order, ex));
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("list", rows);
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("scannedOrders", page.getRecords().size());
        result.put("summary", buildSummary(rows, page.getRecords().size(), errorCount));
        return result;
    }

    /**
     * 将选中的支付宝售后快照导入或更新到本地扣减台账。
     *
     * @param req 请求参数，snapshotIds 支持数组或逗号分隔字符串
     * @return 导入结果统计和明细
     */
    public Map<String, Object> importSnapshots(WebRequest req) {
        List<Integer> snapshotIds = parseSnapshotIds(req.raw().get("snapshotIds"));
        if (snapshotIds.isEmpty()) {
            snapshotIds = parseSnapshotIds(req.raw().get("ids"));
        }
        if (snapshotIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的售后单");
        }

        int imported = 0;
        int failed = 0;
        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        for (Integer snapshotId : snapshotIds) {
            RentAftersaleSyncSnapshot snapshot = snapshotMapper.selectById(snapshotId);
            if (snapshot == null) {
                failed++;
                records.add(importError(snapshotId, "售后同步快照不存在"));
                continue;
            }
            try {
                RentDepositDeductRecord record = importSnapshot(snapshot);
                imported++;
                records.add(toImportRecordView(snapshot, record, null));
            } catch (Exception ex) {
                failed++;
                markSnapshotImportFailed(snapshot, ex);
                records.add(toImportRecordView(snapshot, null, ex.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("imported", imported);
        result.put("failed", failed);
        result.put("records", records);
        return result;
    }

    /**
     * 为操作台账解析一次导入请求关联的主订单。
     *
     * @param req 导入请求
     * @return 能解析到则返回订单，否则返回 null
     */
    public Order resolvePrimaryOrderForImport(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            orderId = req.raw().get("orderNo");
        }
        if (orderId != null) {
            try {
                return locateOrder(orderId);
            } catch (Exception ignored) {
                // 导入可能只传 snapshotIds，继续用快照反查订单。
            }
        }
        List<Integer> snapshotIds = parseSnapshotIds(req.raw().get("snapshotIds"));
        if (snapshotIds.isEmpty()) {
            snapshotIds = parseSnapshotIds(req.raw().get("ids"));
        }
        if (snapshotIds.isEmpty()) {
            return null;
        }
        RentAftersaleSyncSnapshot snapshot = snapshotMapper.selectById(snapshotIds.get(0));
        return snapshot == null || snapshot.getOrderId() == null ? null : orderMapper.selectById(snapshot.getOrderId());
    }

    /**
     * 支付宝创建售后返回重复提交时，尝试从订单查询结果中找回已存在售后并回写当前台账。
     *
     * @param order        本地订单
     * @param targetRecord 本次创建前已落库的扣减记录
     * @return 找回并更新后的本地扣减记录；找不到时返回 null
     */
    public RentDepositDeductRecord recoverDuplicateCreate(Order order, RentDepositDeductRecord targetRecord) {
        try {
            List<RentAftersaleSyncSnapshot> snapshots = syncOrderSnapshots(order);
            RentAftersaleSyncSnapshot matched = findRecoverSnapshot(snapshots, targetRecord);
            if (matched == null) {
                return null;
            }
            RentDepositDeductRecord existingRecord = locateDeductRecord(matched.getAftersaleNo(), matched.getOutAftersaleId());
            if (existingRecord != null
                    && (targetRecord == null || targetRecord.getId() == null || !existingRecord.getId().equals(targetRecord.getId()))) {
                markDuplicateTargetFailed(targetRecord, existingRecord);
                return existingRecord;
            }
            return importSnapshotIntoRecord(matched, order, targetRecord, "支付宝返回重复提交，已同步已有售后单");
        } catch (Exception ex) {
            log.warn("重复创建售后找回失败: orderId={}, recordId={}, msg={}",
                    order == null ? null : order.getOrderId(),
                    targetRecord == null ? null : targetRecord.getId(),
                    ex.getMessage());
            return null;
        }
    }

    /**
     * 主动刷新并返回指定售后单的支付宝快照。
     *
     * AFTERSALE_FINISH 调用成功只代表接口受理；这里紧跟一次订单查询，
     * 用支付宝当前快照判断远端是否真的已完结。
     */
    public RentAftersaleSyncSnapshot refreshSingleAftersale(Order order,
                                                           String aftersaleNo,
                                                           String outAftersaleId) throws AlipayApiException {
        List<RentAftersaleSyncSnapshot> snapshots = syncOrderSnapshots(order);
        for (RentAftersaleSyncSnapshot snapshot : snapshots) {
            if (matchesAftersaleIdentity(snapshot, aftersaleNo, outAftersaleId)) {
                return snapshot;
            }
        }
        return null;
    }

    private List<Map<String, Object>> syncOrderAftersales(Order order, String aftersaleStatusFilter) throws AlipayApiException {
        List<RentAftersaleSyncSnapshot> snapshots = syncOrderSnapshots(order);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (RentAftersaleSyncSnapshot snapshot : snapshots) {
            if (StringUtils.hasText(aftersaleStatusFilter)
                    && !aftersaleStatusFilter.equals(snapshot.getAftersaleStatus())) {
                continue;
            }
            RentDepositDeductRecord record = locateDeductRecord(snapshot.getAftersaleNo(), snapshot.getOutAftersaleId());
            rows.add(toSnapshotView(snapshot, record, null));
        }
        return rows;
    }

    private List<RentAftersaleSyncSnapshot> syncOrderSnapshots(Order order) throws AlipayApiException {
        AlipayCommerceRentOrderQueryResponse response = queryAlipayOrderWithAftersales(order);
        if (!response.isSuccess()) {
            throw new IllegalStateException(response.getSubMsg() != null ? response.getSubMsg() : "支付宝订单查询失败");
        }
        List<RentAftersaleOrderVO> aftersales = response.getRentAftersaleOrders();
        if (aftersales == null || aftersales.isEmpty()) {
            return Collections.emptyList();
        }
        List<RentAftersaleSyncSnapshot> snapshots = new ArrayList<RentAftersaleSyncSnapshot>();
        for (RentAftersaleOrderVO aftersale : aftersales) {
            RentDepositDeductRecord record = locateDeductRecord(aftersale.getAftersaleId(), aftersale.getOutAftersaleId());
            snapshots.add(upsertSnapshot(order, aftersale, record));
        }
        return snapshots;
    }

    private AlipayCommerceRentOrderQueryResponse queryAlipayOrderWithAftersales(Order order) throws AlipayApiException {
        AlipayCommerceRentOrderQueryRequest request = new AlipayCommerceRentOrderQueryRequest();
        AlipayCommerceRentOrderQueryModel model = new AlipayCommerceRentOrderQueryModel();
        model.setOutOrderId(order.getOutOrderId());
        model.setOrderId(order.getRentOrderId());
        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
        model.setQueryOptions(Arrays.asList(QUERY_OPTION_STATEMENT, QUERY_OPTION_AFTERSALE));
        request.setBizModel(model);
        return alipayClientService.execute(request);
    }

    private RentAftersaleSyncSnapshot upsertSnapshot(Order order,
                                                    RentAftersaleOrderVO aftersale,
                                                    RentDepositDeductRecord matchedRecord) {
        RentAftersaleOperationRecordVO latestOperation = latestOperation(aftersale.getOperationRecords());
        // 费用类型/金额挂在“赔付扣款”那条操作记录的 fund_info 上；售后完结后最新操作通常是 AFTERSALE_FINISH，
        // 本身不带 fund_info。只取 latestOperation.fundInfo 会导致已完结售后同步出来费用类型/金额为空，
        // 因此必须扫描全部操作记录，取真正带金额的那条。
        RentAftersaleFundInfoVO fundInfo = resolveFundInfo(aftersale.getOperationRecords());
        RentAftersaleFundItemVO fundItem = firstFundItem(fundInfo);
        RentAftersaleSyncSnapshot snapshot = findSnapshot(aftersale.getAftersaleId(), aftersale.getOutAftersaleId());
        boolean create = snapshot == null;
        if (create) {
            snapshot = new RentAftersaleSyncSnapshot();
            snapshot.setCreateTime(System.currentTimeMillis());
        }

        snapshot.setOrderId(order.getOrderId());
        snapshot.setOrderNo(order.getOrderNo());
        snapshot.setRentOrderId(order.getRentOrderId());
        snapshot.setAftersaleNo(aftersale.getAftersaleId());
        snapshot.setOutAftersaleId(aftersale.getOutAftersaleId());
        snapshot.setSourceType(aftersale.getSourceType());
        snapshot.setAftersaleType(aftersale.getAftersaleType());
        snapshot.setAftersaleStatus(aftersale.getAftersaleStatus());
        snapshot.setFinished(aftersale.getFinished());
        snapshot.setApplyTime(aftersale.getApplyTime());
        snapshot.setLastOperationType(latestOperation == null ? null : latestOperation.getOperationType());
        snapshot.setNextOperationTypes(join(latestOperation == null ? null : latestOperation.getNextOperationTypeList()));
        snapshot.setNeedOperation(StringUtils.hasText(snapshot.getNextOperationTypes()) ? NEED_OPERATION_YES : NEED_OPERATION_NO);
        snapshot.setFeeType(fundItem == null ? null : fundItem.getType());
        snapshot.setDeductAmount(resolveDeductAmount(fundInfo, fundItem));
        snapshot.setReasonDescription(resolveReasonDescription(latestOperation, aftersale.getOperationRecords()));
        snapshot.setReasonCode(inferReasonCode(snapshot.getFeeType(), snapshot.getReasonDescription()));
        snapshot.setPayloadJson(toJson(aftersale));
        snapshot.setMatchedDeductRecordId(matchedRecord == null ? null : matchedRecord.getId());
        snapshot.setMatchStatus(matchedRecord == null
                ? RentAftersaleSyncSnapshot.MATCH_STATUS_MISSING_LOCAL
                : RentAftersaleSyncSnapshot.MATCH_STATUS_MATCHED);
        snapshot.setImportStatus(matchedRecord == null
                ? RentAftersaleSyncSnapshot.IMPORT_STATUS_PENDING
                : RentAftersaleSyncSnapshot.IMPORT_STATUS_IMPORTED);
        snapshot.setFailReason(null);
        snapshot.setSyncedAt(new Date());
        snapshot.setUpdateTime(new Date());

        if (create) {
            snapshotMapper.insert(snapshot);
        } else {
            snapshotMapper.updateById(snapshot);
        }
        return snapshot;
    }

    private RentDepositDeductRecord importSnapshot(RentAftersaleSyncSnapshot snapshot) {
        return transactionTemplate.execute(status -> {
            Order order = orderMapper.selectById(snapshot.getOrderId());
            if (order == null) {
                throw new IllegalArgumentException("本地订单不存在: " + snapshot.getOrderId());
            }
            RentDepositDeductRecord record = locateDeductRecord(snapshot.getAftersaleNo(), snapshot.getOutAftersaleId());
            if (record == null) {
                record = new RentDepositDeductRecord();
                record.setCreateTime(System.currentTimeMillis());
            }
            return importSnapshotIntoRecord(snapshot, order, record, null);
        });
    }

    private RentDepositDeductRecord importSnapshotIntoRecord(RentAftersaleSyncSnapshot snapshot,
                                                            Order order,
                                                            RentDepositDeductRecord record,
                                                            String importMsg) {
        boolean create = record.getId() == null;
        record.setOrderId(order.getOrderId());
        record.setOrderNo(order.getOrderNo());
        record.setAuthNo(order.getOrderAuthNo());
        record.setAftersaleNo(snapshot.getAftersaleNo());
        record.setOutAftersaleId(snapshot.getOutAftersaleId());
        if (!StringUtils.hasText(record.getOutRequestNo())) {
            record.setOutRequestNo(buildSyncRequestNo(order, "DD"));
        }
        if (!StringUtils.hasText(record.getConfirmRequestNo())) {
            record.setConfirmRequestNo(buildSyncRequestNo(order, "CF"));
        }
        if (!StringUtils.hasText(record.getOutTradeNo())) {
            record.setOutTradeNo(buildSyncRequestNo(order, "TP"));
        }
        if (!StringUtils.hasText(record.getFeeType())) {
            record.setFeeType(resolveFeeType(snapshot));
        }
        if (!StringUtils.hasText(record.getReasonCode())) {
            record.setReasonCode(resolveReasonCode(snapshot));
        }
        if (record.getDeductAmount() == null) {
            record.setDeductAmount(snapshot.getDeductAmount());
        }
        if (record.getBeforeRemainingDeposit() == null) {
            record.setBeforeRemainingDeposit(currentRemainingDeposit(order));
        }
        if (!StringUtils.hasText(record.getReason())) {
            record.setReason(resolveReasonText(record.getFeeType()));
        }
        if (!StringUtils.hasText(record.getRemark())) {
            record.setRemark(snapshot.getReasonDescription());
        }
        if (!StringUtils.hasText(record.getOperatorName())) {
            record.setOperatorName("AFTERSALE_SYNC");
        }
        record.setAftersaleStatus(snapshot.getAftersaleStatus());
        record.setLastOperationType(snapshot.getLastOperationType());
        record.setSourceType(snapshot.getSourceType());
        record.setNeedOperation(snapshot.getNeedOperation());
        if (!RentDepositDeductRecord.STATUS_SUCCESS.equals(record.getStatus())) {
            record.setStatus(resolveLocalStatus(snapshot));
        }
        if (StringUtils.hasText(importMsg)) {
            record.setAlipaySubMsg(importMsg);
        }
        record.setUpdateTime(new Date());
        if (create) {
            deductRecordMapper.insert(record);
        } else {
            deductRecordMapper.updateById(record);
        }

        snapshot.setMatchedDeductRecordId(record.getId());
        snapshot.setMatchStatus(RentAftersaleSyncSnapshot.MATCH_STATUS_MATCHED);
        snapshot.setImportStatus(RentAftersaleSyncSnapshot.IMPORT_STATUS_IMPORTED);
        snapshot.setImportedAt(new Date());
        snapshot.setFailReason(null);
        snapshot.setUpdateTime(new Date());
        snapshotMapper.updateById(snapshot);
        return record;
    }

    private RentAftersaleSyncSnapshot findRecoverSnapshot(List<RentAftersaleSyncSnapshot> snapshots,
                                                         RentDepositDeductRecord targetRecord) {
        if (snapshots == null || snapshots.isEmpty() || targetRecord == null) {
            return null;
        }
        for (RentAftersaleSyncSnapshot snapshot : snapshots) {
            if (StringUtils.hasText(targetRecord.getOutAftersaleId())
                    && targetRecord.getOutAftersaleId().equals(snapshot.getOutAftersaleId())) {
                return snapshot;
            }
        }
        for (RentAftersaleSyncSnapshot snapshot : snapshots) {
            if (matchesTargetDeduct(snapshot, targetRecord)) {
                return snapshot;
            }
        }
        return null;
    }

    private boolean matchesTargetDeduct(RentAftersaleSyncSnapshot snapshot, RentDepositDeductRecord targetRecord) {
        if (snapshot == null
                || targetRecord == null
                || AlipayRentConstants.AFTERSALE_STATUS_FAIL.equals(snapshot.getAftersaleStatus())) {
            return false;
        }
        if (StringUtils.hasText(targetRecord.getFeeType())
                && StringUtils.hasText(snapshot.getFeeType())
                && !targetRecord.getFeeType().equals(snapshot.getFeeType())) {
            return false;
        }
        if (StringUtils.hasText(targetRecord.getReasonCode())
                && StringUtils.hasText(snapshot.getReasonCode())
                && !targetRecord.getReasonCode().equals(snapshot.getReasonCode())) {
            return false;
        }
        return targetRecord.getDeductAmount() != null
                && snapshot.getDeductAmount() != null
                && targetRecord.getDeductAmount().intValue() == snapshot.getDeductAmount().intValue();
    }

    private void markDuplicateTargetFailed(RentDepositDeductRecord targetRecord, RentDepositDeductRecord existingRecord) {
        if (targetRecord == null || targetRecord.getId() == null) {
            return;
        }
        RentDepositDeductRecord latest = deductRecordMapper.selectById(targetRecord.getId());
        if (latest == null || RentDepositDeductRecord.STATUS_SUCCESS.equals(latest.getStatus())) {
            return;
        }
        latest.setStatus(RentDepositDeductRecord.STATUS_FAILED);
        if (!StringUtils.hasText(latest.getAftersaleNo())) {
            latest.setAftersaleStatus(AlipayRentConstants.AFTERSALE_STATUS_FAIL);
        }
        latest.setAlipaySubCode(null);
        latest.setAlipaySubMsg("支付宝返回重复提交，已存在扣减记录ID: " + existingRecord.getId());
        latest.setUpdateTime(new Date());
        deductRecordMapper.updateById(latest);
    }

    private RentAftersaleSyncSnapshot findSnapshot(String aftersaleNo, String outAftersaleId) {
        if (StringUtils.hasText(aftersaleNo)) {
            RentAftersaleSyncSnapshot snapshot = snapshotMapper.selectOne(
                    new QueryWrapper<RentAftersaleSyncSnapshot>().eq("aftersale_no", aftersaleNo).last("LIMIT 1"));
            if (snapshot != null) {
                return snapshot;
            }
        }
        if (StringUtils.hasText(outAftersaleId)) {
            return snapshotMapper.selectOne(
                    new QueryWrapper<RentAftersaleSyncSnapshot>().eq("out_aftersale_id", outAftersaleId).last("LIMIT 1"));
        }
        return null;
    }

    private boolean matchesAftersaleIdentity(RentAftersaleSyncSnapshot snapshot,
                                             String aftersaleNo,
                                             String outAftersaleId) {
        if (snapshot == null) {
            return false;
        }
        if (StringUtils.hasText(aftersaleNo) && aftersaleNo.equals(snapshot.getAftersaleNo())) {
            return true;
        }
        return StringUtils.hasText(outAftersaleId) && outAftersaleId.equals(snapshot.getOutAftersaleId());
    }

    private RentDepositDeductRecord locateDeductRecord(String aftersaleNo, String outAftersaleId) {
        if (StringUtils.hasText(aftersaleNo)) {
            RentDepositDeductRecord record = deductRecordMapper.selectOne(
                    new QueryWrapper<RentDepositDeductRecord>().eq("aftersale_no", aftersaleNo).last("LIMIT 1"));
            if (record != null) {
                return record;
            }
        }
        if (StringUtils.hasText(outAftersaleId)) {
            return deductRecordMapper.selectOne(
                    new QueryWrapper<RentDepositDeductRecord>().eq("out_aftersale_id", outAftersaleId).last("LIMIT 1"));
        }
        return null;
    }

    private Order locateOrder(Object orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        String value = String.valueOf(orderId).trim();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        Order order = null;
        try {
            order = orderMapper.selectById(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // 非数字按订单号或支付宝订单号继续查。
        }
        if (order == null) {
            order = orderMapper.selectOne(new QueryWrapper<Order>().eq("order_no", value).last("LIMIT 1"));
        }
        if (order == null) {
            order = orderMapper.selectOne(new QueryWrapper<Order>().eq("alipay_order_id", value).last("LIMIT 1"));
        }
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + value);
        }
        return order;
    }

    private RentAftersaleOperationRecordVO latestOperation(List<RentAftersaleOperationRecordVO> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        RentAftersaleOperationRecordVO latest = records.get(0);
        for (RentAftersaleOperationRecordVO record : records) {
            if (record == null) {
                continue;
            }
            if (latest == null || compareDate(record.getOperationTime(), latest.getOperationTime()) > 0) {
                latest = record;
            }
        }
        return latest;
    }

    private int compareDate(Date left, Date right) {
        long leftTime = left == null ? 0L : left.getTime();
        long rightTime = right == null ? 0L : right.getTime();
        return Long.compare(leftTime, rightTime);
    }

    /**
     * 从全部操作记录里解析真正带金额的 fund_info。
     *
     * 支付宝把赔付金额挂在“赔付/扣款”那条操作记录上，售后完结后最新操作记录（如 AFTERSALE_FINISH）
     * 往往不带 fund_info。优先返回带 fund_item 或 total_amount 的记录，找不到再退化到任意非空 fund_info。
     *
     * @param records 售后操作记录列表
     * @return 带金额信息的 fund_info；都没有时返回 null
     */
    private RentAftersaleFundInfoVO resolveFundInfo(List<RentAftersaleOperationRecordVO> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        RentAftersaleFundInfoVO fallback = null;
        for (RentAftersaleOperationRecordVO record : records) {
            if (record == null || record.getFundInfo() == null) {
                continue;
            }
            RentAftersaleFundInfoVO fundInfo = record.getFundInfo();
            if (firstFundItem(fundInfo) != null || StringUtils.hasText(fundInfo.getTotalAmount())) {
                return fundInfo;
            }
            if (fallback == null) {
                fallback = fundInfo;
            }
        }
        return fallback;
    }

    private RentAftersaleFundItemVO firstFundItem(RentAftersaleFundInfoVO fundInfo) {
        if (fundInfo == null || fundInfo.getFundItemList() == null || fundInfo.getFundItemList().isEmpty()) {
            return null;
        }
        return fundInfo.getFundItemList().get(0);
    }

    private Integer resolveDeductAmount(RentAftersaleFundInfoVO fundInfo, RentAftersaleFundItemVO fundItem) {
        String amount = fundInfo == null ? null : fundInfo.getTotalAmount();
        if (!StringUtils.hasText(amount) && fundItem != null) {
            amount = fundItem.getPayAmount();
        }
        if (!StringUtils.hasText(amount)) {
            return null;
        }
        try {
            return OrderUtil.convertYuanToCent(amount);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 解析售后原因描述。
     *
     * 原因/备注挂在“创建或赔付”那条操作记录上，售后完结后最新操作记录（AFTERSALE_FINISH）通常不带原因，
     * 因此先看最新操作，取不到再扫描全部操作记录，避免已完结售后原因/备注丢失、reasonCode 被误推断。
     *
     * @param latestOperation 最新操作记录
     * @param records         全部操作记录
     * @return 原因描述；都没有时返回 null
     */
    private String resolveReasonDescription(RentAftersaleOperationRecordVO latestOperation,
                                            List<RentAftersaleOperationRecordVO> records) {
        String fromLatest = reasonDescriptionOf(latestOperation);
        if (StringUtils.hasText(fromLatest)) {
            return fromLatest;
        }
        if (records != null) {
            for (RentAftersaleOperationRecordVO record : records) {
                String reason = reasonDescriptionOf(record);
                if (StringUtils.hasText(reason)) {
                    return reason;
                }
            }
        }
        return null;
    }

    private String reasonDescriptionOf(RentAftersaleOperationRecordVO operation) {
        if (operation == null) {
            return null;
        }
        if (StringUtils.hasText(operation.getReasonDescription())) {
            return operation.getReasonDescription();
        }
        return operation.getAdditionalDescription();
    }

    private String inferReasonCode(String feeType, String reasonDescription) {
        String text = reasonDescription == null ? "" : reasonDescription;
        if (text.contains("维修")) {
            return AlipayRentConstants.AFTERSALE_REASON_ITEM_REPAIR;
        }
        if (text.contains("丢") || text.contains("遗失")) {
            return AlipayRentConstants.AFTERSALE_REASON_ITEM_LOST;
        }
        if (text.contains("折旧")) {
            return AlipayRentConstants.AFTERSALE_REASON_ITEM_DEPRECIATION;
        }
        if (text.contains("逾期")) {
            return AlipayRentConstants.AFTERSALE_REASON_RETURN_OVERDUE;
        }
        if (text.contains("提前")) {
            return AlipayRentConstants.AFTERSALE_REASON_RETURN_EARLY;
        }
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(feeType)) {
            return AlipayRentConstants.AFTERSALE_REASON_RETURN_OVERDUE;
        }
        return AlipayRentConstants.AFTERSALE_REASON_ITEM_DAMAGED;
    }

    private String resolveFeeType(RentAftersaleSyncSnapshot snapshot) {
        return StringUtils.hasText(snapshot.getFeeType())
                ? snapshot.getFeeType()
                : AlipayRentConstants.AFTERSALE_FEE_TYPE_INDEMNITY;
    }

    private String resolveReasonCode(RentAftersaleSyncSnapshot snapshot) {
        return StringUtils.hasText(snapshot.getReasonCode())
                ? snapshot.getReasonCode()
                : inferReasonCode(resolveFeeType(snapshot), snapshot.getReasonDescription());
    }

    private String resolveReasonText(String feeType) {
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(feeType)) {
            return "违约金";
        }
        return "赔付金";
    }

    private String resolveLocalStatus(RentAftersaleSyncSnapshot snapshot) {
        if (AlipayRentConstants.AFTERSALE_STATUS_FAIL.equals(snapshot.getAftersaleStatus())) {
            return RentDepositDeductRecord.STATUS_FAILED;
        }
        if (AlipayRentConstants.AFTERSALE_OPERATION_USER_CANCEL_APPLY.equals(snapshot.getLastOperationType())) {
            return RentDepositDeductRecord.STATUS_CANCELLED;
        }
        return RentDepositDeductRecord.STATUS_PROCESSING;
    }

    private int currentRemainingDeposit(Order order) {
        if (order.getOrderRestDeposit() != null) {
            return order.getOrderRestDeposit();
        }
        return order.getOrderDeposit() != null ? order.getOrderDeposit() : 0;
    }

    private String buildSyncRequestNo(Order order, String suffix) {
        return order.getOrderNo() + suffix + "SYNC" + System.currentTimeMillis();
    }

    private List<Integer> parseSnapshotIds(Object rawIds) {
        if (rawIds == null) {
            return Collections.emptyList();
        }
        List<Integer> ids = new ArrayList<Integer>();
        if (rawIds instanceof Iterable) {
            for (Object item : (Iterable<?>) rawIds) {
                Integer id = parseInteger(item);
                if (id != null) {
                    ids.add(id);
                }
            }
            return ids;
        }
        String text = String.valueOf(rawIds);
        for (String part : text.split(",")) {
            Integer id = parseInteger(part);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> buildSummary(List<Map<String, Object>> rows, long scannedOrders, int errorCount) {
        int alipayAftersaleCount = 0;
        int matchedCount = 0;
        int pendingImportCount = 0;
        for (Map<String, Object> row : rows) {
            if (RentAftersaleSyncSnapshot.MATCH_STATUS_QUERY_FAILED.equals(row.get("matchStatus"))) {
                continue;
            }
            alipayAftersaleCount++;
            if (RentAftersaleSyncSnapshot.MATCH_STATUS_MATCHED.equals(row.get("matchStatus"))) {
                matchedCount++;
            }
            if (RentAftersaleSyncSnapshot.IMPORT_STATUS_PENDING.equals(row.get("importStatus"))) {
                pendingImportCount++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("scannedOrders", scannedOrders);
        summary.put("alipayAftersaleCount", alipayAftersaleCount);
        summary.put("matchedCount", matchedCount);
        summary.put("pendingImportCount", pendingImportCount);
        summary.put("errorCount", errorCount);
        return summary;
    }

    private Map<String, Object> toSnapshotView(RentAftersaleSyncSnapshot snapshot,
                                               RentDepositDeductRecord record,
                                               String errorMsg) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("snapshotId", snapshot.getId());
        view.put("orderId", snapshot.getOrderId());
        view.put("orderNo", snapshot.getOrderNo());
        view.put("rentOrderId", snapshot.getRentOrderId());
        view.put("aftersaleNo", snapshot.getAftersaleNo());
        view.put("outAftersaleId", snapshot.getOutAftersaleId());
        view.put("sourceType", snapshot.getSourceType());
        view.put("aftersaleType", snapshot.getAftersaleType());
        view.put("aftersaleStatus", snapshot.getAftersaleStatus());
        view.put("finished", snapshot.getFinished());
        view.put("applyTime", snapshot.getApplyTime());
        view.put("lastOperationType", snapshot.getLastOperationType());
        view.put("nextOperationTypes", snapshot.getNextOperationTypes());
        view.put("needOperation", snapshot.getNeedOperation());
        // 费用类型/金额优先用支付宝同步值；为空时回退已匹配的本地扣减台账，避免已完结售后展示成“-”。
        String feeType = StringUtils.hasText(snapshot.getFeeType())
                ? snapshot.getFeeType()
                : (record == null ? null : record.getFeeType());
        // 原因码：本地台账是商户创建时的权威值，snapshot 侧只是按描述推断的兜底，优先用本地台账的原因码。
        String reasonCode = (record != null && StringUtils.hasText(record.getReasonCode()))
                ? record.getReasonCode()
                : snapshot.getReasonCode();
        Integer deductAmount = snapshot.getDeductAmount() != null
                ? snapshot.getDeductAmount()
                : (record == null ? null : record.getDeductAmount());
        view.put("feeType", feeType);
        view.put("reasonCode", reasonCode);
        view.put("deductAmount", deductAmount);
        view.put("reasonDescription", snapshot.getReasonDescription());
        view.put("matchStatus", snapshot.getMatchStatus());
        view.put("importStatus", snapshot.getImportStatus());
        view.put("matchedDeductRecordId", snapshot.getMatchedDeductRecordId());
        view.put("localDeductStatus", record == null ? null : record.getStatus());
        view.put("localAftersaleStatus", record == null ? null : record.getAftersaleStatus());
        view.put("localLastOperationType", record == null ? null : record.getLastOperationType());
        view.put("localTradeNo", record == null ? null : record.getTradeNo());
        view.put("syncedAt", snapshot.getSyncedAt());
        view.put("importedAt", snapshot.getImportedAt());
        view.put("failReason", errorMsg != null ? errorMsg : snapshot.getFailReason());
        return view;
    }

    private Map<String, Object> toOrderView(Order order) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("orderId", order.getOrderId());
        view.put("orderNo", order.getOrderNo());
        view.put("rentOrderId", order.getRentOrderId());
        view.put("alipayStatus", order.getAlipayStatus());
        return view;
    }

    private Map<String, Object> buildErrorView(Order order, Exception ex) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("orderId", order.getOrderId());
        view.put("orderNo", order.getOrderNo());
        view.put("rentOrderId", order.getRentOrderId());
        view.put("matchStatus", RentAftersaleSyncSnapshot.MATCH_STATUS_QUERY_FAILED);
        view.put("importStatus", RentAftersaleSyncSnapshot.IMPORT_STATUS_FAILED);
        view.put("failReason", ex.getMessage() != null ? ex.getMessage() : "售后同步失败");
        return view;
    }

    private Map<String, Object> importError(Integer snapshotId, String errorMsg) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("snapshotId", snapshotId);
        view.put("success", false);
        view.put("errorMsg", errorMsg);
        return view;
    }

    private Map<String, Object> toImportRecordView(RentAftersaleSyncSnapshot snapshot,
                                                   RentDepositDeductRecord record,
                                                   String errorMsg) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("snapshotId", snapshot.getId());
        view.put("orderId", snapshot.getOrderId());
        view.put("orderNo", snapshot.getOrderNo());
        view.put("aftersaleNo", snapshot.getAftersaleNo());
        view.put("deductRecordId", record == null ? null : record.getId());
        view.put("success", errorMsg == null);
        view.put("errorMsg", errorMsg);
        return view;
    }

    private void markSnapshotImportFailed(RentAftersaleSyncSnapshot snapshot, Exception ex) {
        snapshot.setImportStatus(RentAftersaleSyncSnapshot.IMPORT_STATUS_FAILED);
        snapshot.setFailReason(ex.getMessage() != null ? ex.getMessage() : "导入失败");
        snapshot.setUpdateTime(new Date());
        snapshotMapper.updateById(snapshot);
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private long defaultScanRangeMillis() {
        return Math.max(1L, alipayPlatformConfigService.aftersaleScanRangeDays())
                * AlipayRentConstants.MILLIS_PER_DAY;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
