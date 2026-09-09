package com.fly.rent.miniapp.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentOrderReturnRecord;
import com.fly.rent.mapper.RentOrderReturnRecordMapper;
import com.fly.rent.miniapp.order.dto.MiniappOrderReturnRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户寄回记录服务。
 * 负责把小程序寄回物流、图片和说明保存到独立表，并为后台订单列表、详情和台账提供统一展示数据。
 */
@Service
@RequiredArgsConstructor
public class OrderReturnRecordService {

    /** 用户已提交寄回资料，尚未确认支付宝履约是否成功。 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 支付宝归还发货履约调用成功。 */
    public static final String STATUS_SEND_SUCCESS = "SEND_SUCCESS";
    /** 支付宝归还发货履约调用失败，本地仍保留用户提交资料。 */
    public static final String STATUS_SEND_FAILED = "SEND_FAILED";

    private final RentOrderReturnRecordMapper returnRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据小程序请求保存或更新订单寄回记录。
     *
     * @param order   本地订单，提供订单号、支付宝订单号和用户标识
     * @param request 小程序寄回请求，包含物流、照片和说明
     * @return 保存后的寄回记录
     */
    public RentOrderReturnRecord saveSubmitted(Order order, MiniappOrderReturnRequest request) {
        RentOrderReturnRecord record = findByOrderId(order.getOrderId());
        Date now = new Date();
        if (record == null) {
            record = new RentOrderReturnRecord();
            record.setOrderId(order.getOrderId());
            record.setOrderNo(order.getOrderNo());
            record.setRentOrderId(order.getRentOrderId());
            record.setUserUuid(order.getUserUuid());
            record.setSubmittedAt(System.currentTimeMillis());
            record.setCreatedAt(now);
        }
        record.setOrderNo(order.getOrderNo());
        record.setRentOrderId(order.getRentOrderId());
        record.setUserUuid(order.getUserUuid());
        record.setReturnType(normalize(firstText(request.getReturnType(), "ONLINE_EXPRESS")));
        record.setExpressCode(normalize(request.getCourCode()));
        record.setExpressCompany(normalize(request.getCourName()));
        record.setExpressNo(normalize(request.getCourno()));
        record.setPhotoUrls(toJson(resolvePhotos(request)));
        record.setDetailRemark(normalize(firstText(request.getDetailRemark(), request.getDetail())));
        record.setStatus(STATUS_SUBMITTED);
        record.setFailReason(null);
        record.setUpdatedAt(now);
        if (record.getId() == null) {
            returnRecordMapper.insert(record);
        } else {
            returnRecordMapper.updateById(record);
        }
        return record;
    }

    /**
     * 标记寄回履约成功。
     *
     * @param orderId 本地订单 ID
     */
    public void markSendSuccess(Integer orderId) {
        updateSendResult(orderId, STATUS_SEND_SUCCESS, null);
    }

    /**
     * 标记寄回履约失败，同时保留用户提交资料，便于后台补处理。
     *
     * @param orderId    本地订单 ID
     * @param failReason 失败原因
     */
    public void markSendFailed(Integer orderId, String failReason) {
        updateSendResult(orderId, STATUS_SEND_FAILED, failReason);
    }

    /**
     * 查询订单最新寄回记录。
     *
     * @param orderId 本地订单 ID
     * @return 寄回记录；不存在时返回 null
     */
    public RentOrderReturnRecord findByOrderId(Integer orderId) {
        if (orderId == null) {
            return null;
        }
        return returnRecordMapper.selectOne(new QueryWrapper<RentOrderReturnRecord>()
                .eq("order_id", orderId)
                .last("LIMIT 1"));
    }

    /**
     * 批量查询订单寄回记录，用于后台订单列表避免逐行查库。
     *
     * @param orderIds 本地订单 ID 列表
     * @return key 为 orderId 的寄回记录 Map
     */
    public Map<Integer, RentOrderReturnRecord> findByOrderIds(List<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RentOrderReturnRecord> records = returnRecordMapper.selectList(new QueryWrapper<RentOrderReturnRecord>()
                .in("order_id", orderIds));
        Map<Integer, RentOrderReturnRecord> map = new LinkedHashMap<>();
        records.forEach(record -> map.put(record.getOrderId(), record));
        return map;
    }

    /**
     * 构建用于台账 requestBody 的寄回记录快照。
     *
     * @param record 寄回记录
     * @return 可 JSON 序列化的 Map
     */
    public Map<String, Object> toLogMap(RentOrderReturnRecord record) {
        Map<String, Object> map = toView(record);
        if (map == null) {
            return Collections.emptyMap();
        }
        return map;
    }

    /**
     * 构建前端展示对象，补充解析后的 photoUrlList。
     *
     * @param record 寄回记录
     * @return 前端展示 Map；无记录时返回 null
     */
    public Map<String, Object> toView(RentOrderReturnRecord record) {
        if (record == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("orderId", record.getOrderId());
        view.put("orderNo", record.getOrderNo());
        view.put("rentOrderId", record.getRentOrderId());
        view.put("userUuid", record.getUserUuid());
        view.put("returnType", record.getReturnType());
        view.put("expressCode", record.getExpressCode());
        view.put("expressCompany", record.getExpressCompany());
        view.put("expressNo", record.getExpressNo());
        view.put("photoUrls", record.getPhotoUrls());
        view.put("photoUrlList", parsePhotoUrls(record.getPhotoUrls()));
        view.put("detailRemark", record.getDetailRemark());
        view.put("status", record.getStatus());
        view.put("failReason", record.getFailReason());
        view.put("submittedAt", record.getSubmittedAt());
        view.put("createdAt", record.getCreatedAt());
        view.put("updatedAt", record.getUpdatedAt());
        return view;
    }

    /**
     * 把请求内容转成台账入参，避免 AOP 只记录 returnType 而丢失寄回凭证。
     *
     * @param request 小程序寄回请求
     * @param record  已保存的寄回记录
     * @return 可 JSON 序列化的台账请求体
     */
    public Map<String, Object> buildReturnLogBody(MiniappOrderReturnRequest request, RentOrderReturnRecord record) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("returnType", request.getReturnType());
        body.put("courCode", request.getCourCode());
        body.put("courno", request.getCourno());
        body.put("courName", request.getCourName());
        body.put("photoUrls", resolvePhotos(request));
        body.put("detailRemark", firstText(request.getDetailRemark(), request.getDetail()));
        body.put("returnRecord", toLogMap(record));
        return body;
    }

    private void updateSendResult(Integer orderId, String status, String failReason) {
        RentOrderReturnRecord record = findByOrderId(orderId);
        if (record == null) {
            return;
        }
        record.setStatus(status);
        record.setFailReason(normalize(failReason));
        record.setUpdatedAt(new Date());
        returnRecordMapper.updateById(record);
    }

    private List<String> resolvePhotos(MiniappOrderReturnRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }
        if (request.getPhotoUrls() != null && !request.getPhotoUrls().isEmpty()) {
            return cleanPhotos(request.getPhotoUrls());
        }
        if (request.getReturnPhotos() != null && !request.getReturnPhotos().isEmpty()) {
            return cleanPhotos(request.getReturnPhotos());
        }
        return Collections.emptyList();
    }

    private List<String> cleanPhotos(List<String> photos) {
        List<String> result = new ArrayList<>();
        for (String photo : photos) {
            String normalized = normalize(photo);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String toJson(List<String> photos) {
        try {
            return objectMapper.writeValueAsString(photos == null ? Collections.emptyList() : photos);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parsePhotoUrls(String photoUrls) {
        if (!StringUtils.hasText(photoUrls)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(photoUrls, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
