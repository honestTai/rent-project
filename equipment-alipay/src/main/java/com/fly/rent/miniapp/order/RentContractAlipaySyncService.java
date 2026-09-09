package com.fly.rent.miniapp.order;

import com.alipay.api.FileItem;
import com.alipay.api.request.AlipayOpenFileUploadRequest;
import com.alipay.api.response.AlipayOpenFileUploadResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.web.support.WebOperLogHelper;
import com.fly.rent.web.support.WebOrderOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 签约合同回传支付宝：确认收货后上传 PDF 并把 file_id 关联到租赁交易订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentContractAlipaySyncService {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final long MAX_PDF_BYTES = 5L * 1024L * 1024L;
    private static final String FILE_UPLOAD_BIZ_CODE = "rent_material_upload";
    private static final String MEDIA_TYPE_RENT_CONTRACT = "RENT_CONTRACT";

    private final OrderMapper orderMapper;
    private final RentContractService rentContractService;
    private final AlipayClientService alipayClientService;
    private final AlipayOrderLifecycleNotifyService lifecycleNotifyService;
    private final ObjectMapper objectMapper;
    private final WebOperLogHelper operLogHelper;

    /**
     * 确认收货后的合同回传入口。
     * 该方法不向外抛出支付宝回传异常：确认收货已经成功时，合同回传失败应落状态、记台账、发异常通知，
     * 后续通过人工或定时补偿重试，不能反向回滚本地/支付宝履约状态。
     *
     * @param orderId 本地订单 ID
     * @param source  触发来源，用于台账和补生成 PDF 的来源标记
     */
    public void syncContractAfterReceive(Integer orderId, String source) {
        if (orderId == null) {
            return;
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        if (STATUS_SUCCESS.equals(order.getContractAlipaySyncStatus())
                && StringUtils.hasText(order.getContractAlipayFileId())) {
            recordOperLogSuccess(order, source, "租赁合同回传支付宝-已成功跳过",
                    buildLogBody(order, source, "SKIPPED_SUCCESS", order.getContractAlipayFileId(), "已回传成功，无需重复提交"));
            return;
        }
        if (!isAfterMerchantReceiveStatus(order.getAlipayStatus())) {
            log.info("订单尚未确认收货，跳过租赁合同回传: orderId={}, status={}",
                    order.getOrderId(), order.getAlipayStatus());
            recordOperLogSuccess(order, source, "租赁合同回传支付宝-未确认收货跳过",
                    buildLogBody(order, source, "SKIPPED_NOT_RECEIVED", null, "订单尚未确认收货"));
            return;
        }
        try {
            doSync(order, source);
        } catch (Exception ex) {
            recordFailure(order, source, ex);
            lifecycleNotifyService.notifyException(
                    "租赁合同回传失败",
                    "确认收货后合同回传支付宝失败",
                    ex.getMessage(),
                    order,
                    failureContext(order)
            );
        }
    }

    /**
     * 执行完整回传流程：
     * 1. 确保本地已有合同 PDF 记录；
     * 2. 渲染签署合同 PDF 字节并校验 5MB 限制；
     * 3. 上传文件获取支付宝 file_id；
     * 4. 把 file_id 作为 RENT_CONTRACT 附加材料回传到租赁订单。
     */
    private void doSync(Order order, String source) throws Exception {
        if (!StringUtils.hasText(order.getRentOrderId())) {
            throw new IllegalStateException("订单缺少支付宝租赁订单号，无法回传合同");
        }
        if (!StringUtils.hasText(order.getContractPdfUrl())) {
            rentContractService.generateSignedContractNow(
                    order.getOrderId(),
                    (StringUtils.hasText(source) ? source : "系统") + "-合同回传补生成"
            );
            Order latest = orderMapper.selectById(order.getOrderId());
            if (latest != null) {
                order = latest;
            }
        }

        byte[] pdfBytes = rentContractService.renderSignedContractPdfBytes(order.getOrderId());
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("合同PDF内容为空");
        }
        if (pdfBytes.length > MAX_PDF_BYTES) {
            throw new IllegalStateException("合同PDF超过支付宝5MB文件限制");
        }

        String fileId = uploadContractPdf(order, pdfBytes);
        uploadAdditionalMedia(order, fileId);
        recordSuccess(order, source, fileId, pdfBytes.length);
    }

    /**
     * 调用支付宝文件上传接口。
     * biz_code 固定为租赁材料上传场景，返回的 file_id 后续再绑定到租赁订单。
     */
    private String uploadContractPdf(Order order, byte[] pdfBytes) throws Exception {
        AlipayOpenFileUploadRequest request = new AlipayOpenFileUploadRequest();
        request.setBizCode(FILE_UPLOAD_BIZ_CODE);
        request.setFileContent(new FileItem(contractFilename(order), pdfBytes, "application/pdf"));
        AlipayOpenFileUploadResponse response = alipayClientService.execute(request);
        if (response == null || !response.isSuccess() || !StringUtils.hasText(response.getFileId())) {
            throw new IllegalStateException("支付宝文件上传失败，未返回file_id: "
                    + (response == null ? "无响应" : firstText(response.getSubMsg(), response.getMsg(), response.getSubCode())));
        }
        return response.getFileId();
    }

    /**
     * 调用租赁附加材料回传接口，把文件 file_id 标记为 RENT_CONTRACT。
     */
    private void uploadAdditionalMedia(Order order, String fileId) throws Exception {
        Map<String, Object> media = new LinkedHashMap<String, Object>();
        media.put("type", MEDIA_TYPE_RENT_CONTRACT);
        media.put("value", fileId);

        Map<String, Object> bizContent = new LinkedHashMap<String, Object>();
        bizContent.put("order_id", order.getRentOrderId());
        bizContent.put("fulfillment_additional_media_list", Collections.singletonList(media));

        AlipayCommerceRentAdditionalUploadRequest request = new AlipayCommerceRentAdditionalUploadRequest();
        request.setBizContent(objectMapper.writeValueAsString(bizContent));
        AlipayCommerceRentAdditionalUploadResponse response = alipayClientService.execute(request);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("支付宝合同file_id回传失败: "
                    + (response == null ? "无响应" : firstText(response.getSubMsg(), response.getMsg(), response.getSubCode())));
        }
    }

    /**
     * 成功回传后同时更新订单冗余状态和订单操作台账。
     */
    private void recordSuccess(Order order, String source, String fileId, int pdfBytes) {
        order.setContractAlipayFileId(fileId);
        order.setContractAlipaySyncStatus(STATUS_SUCCESS);
        order.setContractAlipaySyncedAt(System.currentTimeMillis());
        order.setContractAlipaySyncError(null);
        orderMapper.updateById(order);
        recordOperLogSuccess(order, source, WebOrderOperation.CONTRACT_ALIPAY_SYNC.getDescription(),
                buildLogBody(order, source, STATUS_SUCCESS, fileId, "PDF字节数=" + pdfBytes));
        log.info("租赁合同已回传支付宝: orderId={}, rentOrderId={}, fileId={}",
                order.getOrderId(), order.getRentOrderId(), fileId);
    }

    /**
     * 失败只落失败状态和台账，不抛出给确认收货主流程。
     */
    private void recordFailure(Order order, String source, Exception ex) {
        order.setContractAlipaySyncStatus(STATUS_FAILED);
        order.setContractAlipaySyncError(limitError(ex.getMessage()));
        order.setContractAlipaySyncedAt(System.currentTimeMillis());
        orderMapper.updateById(order);
        recordOperLogFailure(order, buildLogBody(order, source, STATUS_FAILED, order.getContractAlipayFileId(), ex.getMessage()), ex);
        log.warn("租赁合同回传支付宝失败: orderId={}, rentOrderId={}, msg={}",
                order.getOrderId(), order.getRentOrderId(), ex.getMessage());
    }

    /**
     * 异常通知上下文，方便后台异常中心快速定位是哪笔租赁订单、哪份合同。
     */
    private Map<String, Object> failureContext(Order order) {
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("orderId", order.getOrderId());
        context.put("orderNo", order.getOrderNo());
        context.put("rentOrderId", order.getRentOrderId());
        context.put("contractPdfUrl", order.getContractPdfUrl());
        return context;
    }

    /**
     * 上传到支付宝时使用稳定文件名，便于支付宝后台和本地台账对照。
     */
    private String contractFilename(Order order) {
        return "rent-contract-" + order.getOrderId() + ".pdf";
    }

    /**
     * 只允许确认收货之后补偿回传。
     * 如果订单已经进入归还中/已归还，说明此前也已经越过确认收货节点，人工补偿仍允许执行。
     */
    private boolean isAfterMerchantReceiveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return AlipayRentConstants.STATUS_RECEIVED.equals(normalized)
                || AlipayRentConstants.STATUS_RETURN_DELIVERED.equals(normalized)
                || AlipayRentConstants.STATUS_RETURN_RECEIVED.equals(normalized)
                || AlipayRentConstants.STATUS_FINISHED.equals(normalized);
    }

    private void recordOperLogSuccess(Order order, String source, String desc, Object resultBody) {
        operLogHelper.logSuccess(
                order,
                WebOrderOperation.CONTRACT_ALIPAY_SYNC.getOperType(),
                desc,
                order.getAlipayStatus(),
                order.getAlipayStatus(),
                buildRequestBody(order, source),
                resultBody,
                WebOperLogHelper.OPERATOR_SYSTEM
        );
    }

    private void recordOperLogFailure(Order order, Object requestBody, Exception ex) {
        operLogHelper.logFailure(
                order,
                WebOrderOperation.CONTRACT_ALIPAY_SYNC.getOperType(),
                WebOrderOperation.CONTRACT_ALIPAY_SYNC.getDescription(),
                order.getAlipayStatus(),
                requestBody,
                limitError(ex.getMessage()),
                WebOperLogHelper.OPERATOR_SYSTEM
        );
    }

    /**
     * 台账请求体保留回传触发来源和订单关键字段，不记录 PDF 原文，避免日志膨胀。
     */
    private Map<String, Object> buildRequestBody(Order order, String source) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("source", StringUtils.hasText(source) ? source : "系统");
        body.put("orderId", order.getOrderId());
        body.put("orderNo", order.getOrderNo());
        body.put("rentOrderId", order.getRentOrderId());
        body.put("alipayStatus", order.getAlipayStatus());
        body.put("contractPdfUrl", order.getContractPdfUrl());
        body.put("contractPdfPath", order.getContractPdfPath());
        body.put("contractAlipayFileId", order.getContractAlipayFileId());
        body.put("contractAlipaySyncStatus", order.getContractAlipaySyncStatus());
        return body;
    }

    /**
     * 台账结果体记录本次回传结论，方便后台按订单日志直接排查。
     */
    private Map<String, Object> buildLogBody(Order order, String source, String status, String fileId, String message) {
        Map<String, Object> body = buildRequestBody(order, source);
        body.put("syncStatus", status);
        body.put("fileId", fileId);
        body.put("message", message);
        body.put("syncedAt", order.getContractAlipaySyncedAt());
        return body;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String limitError(String value) {
        if (!StringUtils.hasText(value)) {
            return "未知错误";
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
