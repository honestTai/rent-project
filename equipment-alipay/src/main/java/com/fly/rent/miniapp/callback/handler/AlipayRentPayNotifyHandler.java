package com.fly.rent.miniapp.callback.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.buyout.RentBuyoutPaymentService;
import com.fly.rent.capability.billing.InstallmentPaymentSyncService;
import com.fly.rent.aftersale.service.RentDepositDeductService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentBuyoutPayment;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.callback.AlipayNotifyHandler;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.web.support.WebOperLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 租赁支付通知处理器。
 *
 * 处理顺序非常重要：
 * 1. 先尝试识别“售后赔付支付”，命中后直接交给扣减领域服务处理；
 * 2. 只有未命中赔付支付时，才按“普通租金支付”更新分期和订单状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayRentPayNotifyHandler implements AlipayNotifyHandler {

    private final OrderMapper orderMapper;
    private final WebOperLogHelper operLogHelper;
    private final AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;
    private final RentDepositDeductService rentDepositDeductService;
    private final InstallmentPaymentSyncService paymentSyncService;
    private final RentBuyoutPaymentService buyoutPaymentService;

    @Override
    public boolean supports(Map<String, String> params) {
        return AlipayRentConstants.ORDER_PAY_NOTIFY_METHOD.equals(params.get("msg_method"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handle(Map<String, String> params) {
        String bizContentStr = params.get("biz_content");
        if (bizContentStr == null || bizContentStr.isEmpty()) {
            log.warn("租赁支付回调缺少 biz_content");
            return "success";
        }

        JSONObject bizContent = JSONUtil.parseObj(bizContentStr);
        String payStatus = bizContent.getStr("pay_status");
        String outOrderId = bizContent.getStr("out_order_id");
        String outTradeNo = bizContent.getStr("out_trade_no");
        String tradeNo = bizContent.getStr("trade_no");
        String payAmount = bizContent.getStr("pay_amount");

        log.info("租赁支付回调: out_order_id={}, out_trade_no={}, trade_no={}, pay_amount={}, pay_status={}",
                outOrderId, outTradeNo, tradeNo, payAmount, payStatus);

        if (!AlipayRentConstants.STATUS_PAID.equals(payStatus)) {
            log.info("租赁支付回调非 PAID 状态，跳过");
            return "success";
        }

        Integer payAmountCent = parseYuanToCent(payAmount);
        boolean handledByDeductService = rentDepositDeductService.handleCompensationPaySuccess(
                outTradeNo, outOrderId, tradeNo, payAmountCent, bizContentStr
        );
        if (handledByDeductService) {
            List<Order> deductOrders = orderMapper.selectList(new QueryWrapper<Order>().eq("order_no", outOrderId));
            if (deductOrders != null && !deductOrders.isEmpty()) {
                Order deductOrder = deductOrders.get(0);
                // 赔付支付回调属于押金扣减链路，operType 用 DEDUCT_DEPOSIT，避免在台账里被标成“租金支付”。
                operLogHelper.logSuccess(deductOrder,
                        "DEDUCT_DEPOSIT", "扣减押金-赔付支付回调",
                        deductOrder.getAlipayStatus(), deductOrder.getAlipayStatus(),
                        params, bizContent, WebOperLogHelper.OPERATOR_CALLBACK);
            } else {
                log.warn("赔付支付回调已落账但找不到订单，未写操作日志: out_order_id={}, out_trade_no={}, trade_no={}",
                        outOrderId, outTradeNo, tradeNo);
            }
            return "success";
        }

        RentBuyoutPayment buyoutPayment = buyoutPaymentService.markPaid(outTradeNo, tradeNo, payAmountCent, bizContentStr);
        if (buyoutPayment != null) {
            Order buyoutOrder = buyoutPayment.getOrderId() == null ? null : orderMapper.selectById(buyoutPayment.getOrderId());
            if (buyoutOrder == null) {
                log.warn("买断支付回调已落账但找不到订单: out_trade_no={}, trade_no={}", outTradeNo, tradeNo);
                return "success";
            }
            String beforeStatus = buyoutOrder.getAlipayStatus();
            if (!AlipayRentConstants.STATUS_FINISHED.equals(beforeStatus)
                    && !AlipayRentConstants.STATUS_CLOSED.equals(beforeStatus)) {
                buyoutOrder.setAlipayStatus(AlipayRentConstants.STATUS_FINISHED);
                buyoutOrder.setOrderLastpay(System.currentTimeMillis());
                buyoutOrder.setUpdateTime(new Date());
                orderMapper.updateById(buyoutOrder);
                alipayOrderLifecycleNotifyService.notifyStatusChanged(
                        buyoutOrder,
                        beforeStatus,
                        buyoutOrder.getAlipayStatus(),
                        "支付宝买断支付回调"
                );
            }
            operLogHelper.logSuccess(buyoutOrder,
                    "BUYOUT_PAY", "买断支付回调",
                    beforeStatus, buyoutOrder.getAlipayStatus(),
                    params, bizContent, WebOperLogHelper.OPERATOR_CALLBACK);
            return "success";
        }

        List<Order> orders = orderMapper.selectList(new QueryWrapper<Order>().eq("order_no", outOrderId));
        if (orders == null || orders.isEmpty()) {
            log.error("租金支付回调找不到订单: out_order_id={}", outOrderId);
            return "failure";
        }
        Order order = orders.get(0);
        String beforeStatus = order.getAlipayStatus();

        if (AlipayRentConstants.STATUS_FINISHED.equals(beforeStatus)
                || AlipayRentConstants.STATUS_CLOSED.equals(beforeStatus)) {
            log.info("订单已完结/关闭，跳过租金支付: orderId={}, status={}", order.getOrderId(), beforeStatus);
            return "success";
        }

        updateInstallments(order, bizContent, tradeNo);

        // 新支付通知优先写入独立交易号字段；历史订单在读取时仍会回退旧字段，因此这里不会影响旧数据。
        if (tradeNo != null && !tradeNo.trim().isEmpty()) {
            order.setPaymentTradeNo(tradeNo);
        }

        if (AlipayRentConstants.STATUS_APPROVED.equals(beforeStatus)) {
            order.setAlipayStatus(AlipayRentConstants.STATUS_PAID);
        }

        order.setUpdateTime(new Date());
        orderMapper.updateById(order);
        if (AlipayRentConstants.STATUS_PAID.equals(order.getAlipayStatus())
                && !AlipayRentConstants.STATUS_PAID.equals(beforeStatus)) {
            alipayOrderLifecycleNotifyService.notifyStatusChanged(
                    order,
                    beforeStatus,
                    order.getAlipayStatus(),
                    "支付宝支付回调"
            );
        }

        operLogHelper.logSuccess(order,
                "RENT_PAY", "租金支付回调",
                beforeStatus, order.getAlipayStatus(),
                params, null, WebOperLogHelper.OPERATOR_CALLBACK);
        return "success";
    }

    private void updateInstallments(Order order, JSONObject bizContent, String tradeNo) {
        JSONArray payItems = bizContent.getJSONArray("pay_items");
        if (payItems == null || payItems.isEmpty()) {
            paymentSyncService.markNextUnpaidPeriodPaid(order, tradeNo, new Date());
            return;
        }
        boolean synced = false;
        for (int i = 0; i < payItems.size(); i++) {
            JSONObject item = payItems.getJSONObject(i);
            Long installmentNo = item.getLong("installment_no");
            if (installmentNo == null) {
                continue;
            }
            paymentSyncService.markPeriodPaid(order, installmentNo.intValue(), tradeNo, new Date());
            synced = true;
        }
        if (!synced) {
            paymentSyncService.markNextUnpaidPeriodPaid(order, tradeNo, new Date());
        }
    }

    private Integer parseYuanToCent(String yuan) {
        if (yuan == null || yuan.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(com.fly.rent.support.util.OrderUtil.convertYuanToCent(yuan.trim()));
        } catch (Exception ex) {
            return null;
        }
    }
}
