package com.fly.rent.miniapp.order;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.FulfillmentDeliveryInfo;
import com.fly.rent.capability.contract.OrderContractService;
import com.fly.rent.web.support.OperLog;
import com.fly.rent.web.support.OperLogContext;
import com.fly.rent.web.support.WebOperLogHelper;
import com.fly.rent.web.support.WebOrderOperation;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentOrderReturnRecord;
import com.fly.rent.entity.Result;
import com.fly.rent.legacy.service.RentOrderFulfillmentService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.order.dto.MiniappOrderReturnRequest;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 小程序侧租赁订单操作，统一走切面 + 事件发布记台账（仅对已存在订单记录）。
 */
@Service
@RequiredArgsConstructor
public class MiniappRentOperLogService {

    private final RentOrderService rentOrderService;
    private final RentOrderFulfillmentService rentOrderFulfillmentService;
    private final OrderMapper orderMapper;
    private final AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;
    private final OrderReturnRecordService orderReturnRecordService;
    private final OrderContractService orderContractService;

    /**
     * 用户申请取消/退款。订单必须已存在，由调用方先查订单。
     */
    @OperLog
    public Result applyRefund(Order order, Map<String, Object> body) {
        OperLogContext.begin(order, WebOrderOperation.MINIAPP_REFUND_APPLY,
                WebRequest.of(body), WebOperLogHelper.OPERATOR_MINIAPP);
        rentOrderService.cancelAndRefund(order);
        return ResultUtil.success("申请已提交");
    }

    /**
     * 用户确认收货。订单必须已存在。
     */
    @OperLog
    public Result confirmReceipt(Order order, Map<String, Object> body) throws AlipayApiException {
        orderContractService.requireReceiptSignReady(order);
        OperLogContext.begin(order, WebOrderOperation.MINIAPP_CONFIRM_RECEIVE,
                WebRequest.of(body), WebOperLogHelper.OPERATOR_MINIAPP);
        try {
            rentOrderFulfillmentService.receive(order, "MERCHANT_DELIVERY_RECEIVED");
            return ResultUtil.success();
        } catch (AlipayApiException | IllegalArgumentException e) {
            alipayOrderLifecycleNotifyService.notifyException(
                    "小程序确认收货失败",
                    "小程序用户确认收货时履约接口报错",
                    e.getMessage(),
                    order,
                    body
            );
            throw e;
        }
    }

    /**
     * 用户归还发货。订单必须已存在；body 可含 returnType，order 可含物流信息。
     */
    @OperLog
    public Result returnDevice(Order order, Map<String, Object> body) throws AlipayApiException {
        MiniappOrderReturnRequest returnRequest = buildReturnRequest(order, body);
        applyReturnRequestToOrder(order, returnRequest);
        applyOfflineCourierIfNeeded(order, returnRequest);
        RentOrderReturnRecord record = orderReturnRecordService.saveSubmitted(order, returnRequest);
        Map<String, Object> logBody = orderReturnRecordService.buildReturnLogBody(returnRequest, record);
        OperLogContext.begin(order, WebOrderOperation.MINIAPP_RETURN_SEND,
                WebRequest.of(logBody), WebOperLogHelper.OPERATOR_MINIAPP);
        try {
            doReturnDevice(order);
            orderReturnRecordService.markSendSuccess(order.getOrderId());
            OperLogContext.setResultBody(Collections.singletonMap("returnRecordId", record.getId()));
            return ResultUtil.success();
        } catch (AlipayApiException | IllegalArgumentException e) {
            orderReturnRecordService.markSendFailed(order.getOrderId(), e.getMessage());
            alipayOrderLifecycleNotifyService.notifyException(
                    "小程序归还发货失败",
                    "小程序用户提交寄回物流时履约接口报错",
                    e.getMessage(),
                    order,
                    body
            );
            throw e;
        }
    }

    /**
     * 执行用户归还发货履约。
     * 寄回资料已在 returnDevice 中落库，这里只负责把物流单号传给支付宝履约接口。
     *
     * @param order 已带寄回物流字段的订单
     * @throws AlipayApiException 支付宝接口异常
     */
    private void doReturnDevice(Order order) throws AlipayApiException {
        order.setRentSendStatus("USER_DELIVERY_SEND");
        doSend(order);
    }

    /**
     * 从订单和请求体构建寄回请求对象。
     * 兼容 /return 端点传入的 Order 字段，以及 /actions 端点直接传 Map 的字段。
     *
     * @param order 订单对象，可能已带 courCode/courno/courName
     * @param body  小程序请求体
     * @return 标准化后的寄回请求
     */
    @SuppressWarnings("unchecked")
    private MiniappOrderReturnRequest buildReturnRequest(Order order, Map<String, Object> body) {
        MiniappOrderReturnRequest request = new MiniappOrderReturnRequest();
        request.setOrderId(order.getOrderId());
        request.setReturnType(firstText(text(body, "returnType"), order.getReturnType()));
        request.setCourCode(firstText(text(body, "courCode"), order.getCourCode()));
        request.setCourno(firstText(text(body, "courno"), order.getCourno()));
        request.setCourName(firstText(text(body, "courName"), order.getCourName()));
        request.setDetailRemark(firstText(text(body, "detailRemark"), text(body, "detail")));
        Object photoUrls = body == null ? null : body.get("photoUrls");
        Object returnPhotos = body == null ? null : body.get("returnPhotos");
        request.setPhotoUrls(photoUrls instanceof List ? (List<String>) photoUrls : parseCsvPhotos(photoUrls));
        request.setReturnPhotos(returnPhotos instanceof List ? (List<String>) returnPhotos : parseCsvPhotos(returnPhotos));
        return request;
    }

    /**
     * 将寄回请求里的物流字段回填到订单对象，供支付宝履约接口读取。
     *
     * @param order   订单对象
     * @param request 寄回请求
     */
    private void applyReturnRequestToOrder(Order order, MiniappOrderReturnRequest request) {
        order.setReturnType(request.getReturnType());
        order.setCourCode(request.getCourCode());
        order.setCourno(request.getCourno());
        order.setCourName(request.getCourName());
    }

    /**
     * 线下归还没有真实快递时使用兼容物流单号。
     * 支付宝履约发货接口仍要求 delivery_id 和 waybill_id，因此生成稳定的本地占位单号。
     *
     * @param order   订单对象
     * @param request 寄回请求
     */
    private void applyOfflineCourierIfNeeded(Order order, MiniappOrderReturnRequest request) {
        if (!AlipayRentConstants.RENT_RETURN_TYPE_OFFLINE.equals(request.getReturnType())) {
            return;
        }
        String waybill = StringUtils.hasText(request.getCourno())
                ? request.getCourno()
                : order.getOrderNo() + "01";
        order.setCourno(waybill);
        order.setCourCode(StringUtils.hasText(request.getCourCode()) ? request.getCourCode() : "NEW_TAOBAO");
        order.setCourName(StringUtils.hasText(request.getCourName()) ? request.getCourName() : "线下归还");
        request.setCourno(order.getCourno());
        request.setCourCode(order.getCourCode());
        request.setCourName(order.getCourName());
    }

    private void doSend(Order order) throws AlipayApiException {
        Order orderDb = orderMapper.selectById(order.getOrderId());
        if (orderDb == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        List<FulfillmentDeliveryInfo> deliveryList = new ArrayList<>();
        FulfillmentDeliveryInfo deliveryInfo = new FulfillmentDeliveryInfo();
        deliveryInfo.setDeliveryId(order.getCourCode() != null ? order.getCourCode() : "EXPRESS");
        deliveryInfo.setWaybillId(order.getCourno());
        deliveryList.add(deliveryInfo);
        orderDb.setRentSendStatus(order.getRentSendStatus());
        if ("MERCHANT_DELIVERY_SEND".equals(order.getRentSendStatus())) {
            orderDb.setCourCode(order.getCourCode());
            orderDb.setCourno(order.getCourno());
            orderDb.setCourName(order.getCourName());
        }
        rentOrderFulfillmentService.send(orderDb, deliveryList);
    }

    private String text(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private List<String> parseCsvPhotos(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return Collections.emptyList();
        }
        List<String> photos = new ArrayList<>();
        String[] items = String.valueOf(value).split(",");
        for (String item : items) {
            if (StringUtils.hasText(item)) {
                photos.add(item.trim());
            }
        }
        return photos;
    }
}
