package com.fly.rent.miniapp.callback.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.dto.apilyRentUtil.BaseApily;
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
 * 资金授权冻结通知处理器。
 *
 * 这部分逻辑沿用原有行为：
 * - 冻结成功后回写 auth_no；
 * - 把订单状态从 CREATED 推进到 SIGNED；
 * - 扣减库存并记录操作日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayFreezeNotifyHandler implements AlipayNotifyHandler {

    private final OrderMapper orderMapper;
    private final BaseApily baseApily;
    private final WebOperLogHelper operLogHelper;
    private final AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;

    @Override
    public boolean supports(Map<String, String> params) {
        return AlipayRentConstants.NOTIFY_TYPE_FUND_AUTH_FREEZE.equals(params.get("notify_type"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handle(Map<String, String> params) {
        String authNo = params.get("auth_no");
        String outOrderNo = params.get("out_order_no");
        String status = params.get("status");

        log.info("押金冻结回调: out_order_no={}, auth_no={}, amount={}, status={}",
                outOrderNo, authNo, params.get("amount"), status);

        if (!"SUCCESS".equals(status)) {
            log.info("押金冻结回调非 SUCCESS 状态，跳过");
            return "success";
        }

        List<Order> orders = orderMapper.selectList(new QueryWrapper<Order>().eq("order_no", outOrderNo));
        if (orders == null || orders.isEmpty()) {
            log.error("押金冻结回调找不到订单: out_order_no={}", outOrderNo);
            return "failure";
        }

        Order order = orders.get(0);
        String beforeStatus = order.getAlipayStatus();

        // 幂等处理：已经写过同一个 auth_no 时直接返回成功。
        if (authNo != null && authNo.equals(order.getOrderAuthNo())) {
            return "success";
        }
        if (!AlipayRentConstants.STATUS_CREATED.equals(beforeStatus)) {
            log.info("订单状态已推进，跳过冻结回调: orderId={}, status={}", order.getOrderId(), beforeStatus);
            return "success";
        }

        order.setOrderAuthNo(authNo);
        order.setAlipayStatus(AlipayRentConstants.STATUS_SIGNED);
        order.setUpdateTime(new Date());

        Attr attr = baseApily.getAttrMapper().selectById(order.getAttrId());
        if (attr != null && attr.getAttrNum() != null && attr.getAttrNum().intValue() > 0) {
            int deduct = order.getAttrNum() != null ? order.getAttrNum().intValue() : 1;
            attr.setAttrNum(Integer.valueOf(attr.getAttrNum().intValue() - deduct));
            baseApily.getAttrMapper().updateById(attr);
        }

        Good good = baseApily.getGoodMapper().selectById(order.getGoodId());
        if (good != null) {
            int qty = order.getAttrNum() != null ? order.getAttrNum().intValue() : 1;
            good.setDeal(Integer.valueOf((good.getDeal() != null ? good.getDeal().intValue() : 0) + qty));
            baseApily.getGoodMapper().updateById(good);
        }

        orderMapper.updateById(order);
        alipayOrderLifecycleNotifyService.notifyStatusChanged(
                order,
                beforeStatus,
                AlipayRentConstants.STATUS_SIGNED,
                "支付宝冻结回调"
        );

        operLogHelper.logSuccess(order,
                "FREEZE", "押金冻结回调",
                beforeStatus, AlipayRentConstants.STATUS_SIGNED,
                params, null, WebOperLogHelper.OPERATOR_CALLBACK);
        return "success";
    }
}
