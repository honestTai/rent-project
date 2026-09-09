package com.fly.rent.common.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 订单定位统一入口。
 * <p>
 * 后台与小程序侧传入的“订单标识”可能是：主键 ID、业务订单号 order_no、支付宝租赁订单号 alipay_order_id、或 source_id。
 * 本类集中“按标识查订单”的逻辑，避免各处重复判断；查询顺序：主键（仅纯数字且 int 范围内）→ order_no → alipay_order_id → source_id。
 *
 * @see OrderMapper
 */
@RequiredArgsConstructor
@Component
public class RentOrderLocator {

    private final OrderMapper orderMapper;

    /**
     * 根据标识符获取订单，不存在则抛出 404 业务异常。
     *
     * @param identifier 订单标识（主键、orderNo、rentOrderId、sourceId 等）
     * @return 订单实体，必非 null
     * @throws RentApiException 404 当订单不存在时
     */
    public Order requireByIdentifier(String identifier) {
        Order order = findByIdentifier(identifier);
        if (order == null) {
            throw new RentApiException(404, "订单不存在");
        }
        return order;
    }

    /**
     * 根据标识符查找订单，不存在返回 null。
     * 依次尝试：主键（纯数字且 in int 范围）→ order_no → alipay_order_id → source_id；命中即返回。
     *
     * @param identifier 订单标识，空则返回 null
     * @return 订单实体或 null
     */
    public Order findByIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        String trimmed = identifier.trim();
        // 仅当为纯数字且在 int 范围内时按主键查，避免长数字（如支付宝单号）误当主键
        if (trimmed.matches("\\d+")) {
            try {
                long parsed = Long.parseLong(trimmed);
                if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                    Order byId = orderMapper.selectById((int) parsed);
                    if (byId != null) {
                        return byId;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Order byOrderNo = first(orderMapper.selectList(new QueryWrapper<Order>().eq("order_no", trimmed)));
        if (byOrderNo != null) {
            return byOrderNo;
        }

        Order byRentOrderId = first(orderMapper.selectList(new QueryWrapper<Order>().eq("alipay_order_id", trimmed)));
        if (byRentOrderId != null) {
            return byRentOrderId;
        }

        return first(orderMapper.selectList(new QueryWrapper<Order>().eq("source_id", trimmed)));
    }

    /**
     * 根据 sourceId（及可选 userUuid）查最近一笔订单。
     * 用于交易组件建单后根据来源 ID 反查本地订单。
     *
     * @param sourceId 来源 ID，空则返回 null
     * @param userUuid 用户 UUID，可选，有则按用户过滤
     * @return 按 created_at 倒序的第一条订单，无则 null
     */
    public Order findLatestBySourceId(String sourceId, String userUuid) {
        if (!StringUtils.hasText(sourceId)) {
            return null;
        }
        List<Order> orders = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("source_id", sourceId)
                .eq(StringUtils.hasText(userUuid), "user_uuid", userUuid)
                .orderByDesc("created_at"));
        return first(orders);
    }

    /** 取列表首元素，空列表或 null 返回 null */
    private Order first(List<Order> orders) {
        return orders == null || orders.isEmpty() ? null : orders.get(0);
    }
}
