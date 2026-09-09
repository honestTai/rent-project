package com.fly.rent.miniapp.callback;

import com.fly.rent.web.support.OperLog;
import com.fly.rent.web.support.OperLogContext;
import com.fly.rent.web.support.WebOperLogHelper;
import com.fly.rent.web.support.WebOrderOperation;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.entity.Order;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝回调相关操作，统一走切面 + 事件发布记台账（仅对已存在订单记录）。
 */
@Service
@RequiredArgsConstructor
public class CallbackOperLogService {

    private final AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;

    /**
     * 同步支付宝订单状态到本地。订单必须已存在，由调用方先查订单。
     */
    @OperLog
    public void syncOrderStatus(Order order, String newStatus, RentRequests.AlipayOrderStatusCallbackRequest request) {
        String oldStatus = order.getAlipayStatus();
        Map<String, Object> body = new HashMap<>();
        if (request != null) {
            if (request.getAlipayStatus() != null) body.put("alipayStatus", request.getAlipayStatus());
            if (request.getRentOrderId() != null) body.put("rentOrderId", request.getRentOrderId());
            if (request.getOrderId() != null) body.put("orderId", request.getOrderId());
            if (request.getSourceId() != null) body.put("sourceId", request.getSourceId());
        }
        OperLogContext.begin(order, WebOrderOperation.CALLBACK_STATUS, WebRequest.of(body), WebOperLogHelper.OPERATOR_CALLBACK);
        order.setAlipayStatus(newStatus);
        order.setUpdateTime(new java.util.Date());
        order.updateById();
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, newStatus, "支付宝回调");
    }
}
