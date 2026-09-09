package com.fly.rent.miniapp.callback;

import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.capability.contract.OrderContractService;
import com.fly.rent.common.support.RentOrderLocator;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.Result;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 小程序相关回调入口（参照 ApilyRentUp 简化）。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rent/v1/miniapp/callbacks")
public class MiniappCallbackController {

    private final RentOrderLocator orderLocator;
    private final CallbackOperLogService callbackOperLogService;
    private final AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;
    private final OrderContractService orderContractService;

    @PostMapping("/alipay/order-status")
    public Result alipayOrderStatus(@RequestBody RentRequests.AlipayOrderStatusCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getAlipayStatus()) || !StringUtils.hasText(request.getRentOrderId())) {
            alipayOrderLifecycleNotifyService.notifyException(
                    "支付宝回调缺参",
                    "订单状态回调参数不完整",
                    "rentOrderId 或 alipayStatus 缺失",
                    null,
                    buildCallbackContext(request)
            );
            Map<String, Object> ack = new HashMap<>();
            ack.put("accepted", false);
            return ResultUtil.success(ack);
        }

        Order order = orderLocator.findByIdentifier(request.getRentOrderId());
        if (order == null && StringUtils.hasText(request.getOrderId())) {
            order = orderLocator.findByIdentifier(request.getOrderId());
        }
        if (order == null && StringUtils.hasText(request.getSourceId())) {
            order = orderLocator.findByIdentifier(request.getSourceId());
        }

        Map<String, Object> ack = new HashMap<>();
        if (order == null) {
            alipayOrderLifecycleNotifyService.notifyException(
                    "支付宝回调未匹配到订单",
                    "订单状态回调无法定位本地订单",
                    "请核对 rentOrderId/orderId/sourceId 映射关系",
                    null,
                    buildCallbackContext(request)
            );
            ack.put("accepted", false);
            return ResultUtil.success(ack);
        }

        String status = request.getAlipayStatus().trim().toUpperCase(Locale.ROOT);
        if ("PENDINGCANCLE".equalsIgnoreCase(status) || "PENDING_CANCEL".equalsIgnoreCase(status)) {
            status = "PENDING_CANCEL";
        }
        callbackOperLogService.syncOrderStatus(order, status, request);

        ack.put("accepted", true);
        return ResultUtil.success(ack);
    }

    @PostMapping("/esign")
    public Result esign(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) {
            payload = new HashMap<>();
        }
        orderContractService.handleCallback(payload);
        Map<String, Object> ack = new HashMap<>();
        ack.put("accepted", true);
        return ResultUtil.success(ack);
    }

    /**
     * 异常回调通知只保留关键定位字段，避免直接把整包原始回调打进卡片造成噪音。
     */
    private Map<String, Object> buildCallbackContext(RentRequests.AlipayOrderStatusCallbackRequest request) {
        Map<String, Object> context = new HashMap<>();
        if (request == null) {
            return context;
        }
        context.put("rentOrderId", request.getRentOrderId());
        context.put("orderId", request.getOrderId());
        context.put("sourceId", request.getSourceId());
        context.put("alipayStatus", request.getAlipayStatus());
        context.put("contractStatus", request.getContractStatus());
        context.put("payStatus", request.getPayStatus());
        context.put("occurredAt", request.getOccurredAt());
        return context;
    }
}
