package com.fly.rent.common.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentOrderReletRelation;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentOrderReletRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租赁订单续租关系服务。
 * 数据库是续租父子关系的持久化来源，Redis 只保存热点扩展信息，避免缓存丢失后续租单退化为普通租赁单。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RentOrderReletRelationService {

    /** 有效续租关系。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    private final RentOrderReletRelationMapper relationMapper;
    private final OrderMapper orderMapper;
    private final RentExtensionStore rentExtensionStore;

    /**
     * 保存续租关系并刷新 Redis 热点缓存。
     *
     * @param reletOrder  续租子单，必须已落库并尽量包含支付宝租赁订单号
     * @param parentOrder 直接父单，首次续租时就是原单，多次续租时是上一笔续租单
     * @param renewDuration 本次续租天数
     * @return 持久化后的续租关系
     */
    public RentOrderReletRelation saveRelation(Order reletOrder, Order parentOrder, Integer renewDuration) {
        RentOrderReletRelation parentRelation = findByChildOrderNo(parentOrder.getOrderNo());
        Order originOrder = resolveOriginOrder(parentOrder, parentRelation);
        Date now = new Date();

        RentOrderReletRelation relation = findByChildOrderId(reletOrder.getOrderId());
        if (relation == null) {
            relation = new RentOrderReletRelation();
            relation.setChildOrderId(reletOrder.getOrderId());
            relation.setChildOrderNo(reletOrder.getOrderNo());
            relation.setCreatedAt(now);
        }
        relation.setChildRentOrderId(reletOrder.getRentOrderId());
        relation.setParentOrderId(parentOrder.getOrderId());
        relation.setParentOrderNo(parentOrder.getOrderNo());
        relation.setParentRentOrderId(parentOrder.getRentOrderId());
        relation.setOriginOrderId(originOrder.getOrderId());
        relation.setOriginOrderNo(originOrder.getOrderNo());
        relation.setOriginRentOrderId(originOrder.getRentOrderId());
        relation.setUserUuid(reletOrder.getUserUuid());
        relation.setRenewDuration(renewDuration);
        relation.setOriginalRentEnd(parentOrder.getOrderEnd());
        relation.setReletRentStart(reletOrder.getOrderStart());
        relation.setReletRentEnd(reletOrder.getOrderEnd());
        relation.setStatus(STATUS_ACTIVE);
        relation.setUpdatedAt(now);
        if (relation.getId() == null) {
            relationMapper.insert(relation);
        } else {
            relationMapper.updateById(relation);
        }
        refreshExtensionCache(reletOrder.getOrderNo(), parentOrder, relation);
        return relation;
    }

    /**
     * 根据子单订单号查询续租关系。
     *
     * @param childOrderNo 子单订单号
     * @return 续租关系；不存在时返回 null
     */
    public RentOrderReletRelation findByChildOrderNo(String childOrderNo) {
        if (!StringUtils.hasText(childOrderNo)) {
            return null;
        }
        try {
            return relationMapper.selectOne(new QueryWrapper<RentOrderReletRelation>()
                    .eq("child_order_no", childOrderNo)
                    .last("LIMIT 1"));
        } catch (RuntimeException ex) {
            log.warn("查询续租关系失败，按普通租赁订单处理: childOrderNo={}, msg={}", childOrderNo, ex.getMessage());
            return null;
        }
    }

    /**
     * 根据子单订单 ID 查询续租关系。
     *
     * @param childOrderId 子单订单 ID
     * @return 续租关系；不存在时返回 null
     */
    public RentOrderReletRelation findByChildOrderId(Integer childOrderId) {
        if (childOrderId == null) {
            return null;
        }
        return relationMapper.selectOne(new QueryWrapper<RentOrderReletRelation>()
                .eq("child_order_id", childOrderId)
                .last("LIMIT 1"));
    }

    /**
     * 批量查询子单续租关系。
     *
     * @param childOrderNos 子单订单号列表
     * @return key 为子单订单号的续租关系 Map
     */
    public Map<String, RentOrderReletRelation> findByChildOrderNos(List<String> childOrderNos) {
        if (childOrderNos == null || childOrderNos.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RentOrderReletRelation> relations = relationMapper.selectList(new QueryWrapper<RentOrderReletRelation>()
                .in("child_order_no", childOrderNos));
        Map<String, RentOrderReletRelation> result = new HashMap<>();
        if (relations != null) {
            for (RentOrderReletRelation relation : relations) {
                result.put(relation.getChildOrderNo(), relation);
            }
        }
        return result;
    }

    /**
     * 判断订单是否为续租子单。
     *
     * @param order 订单实体
     * @return true 表示该订单存在续租关系
     */
    public boolean isReletOrder(Order order) {
        return order != null && findByChildOrderNo(order.getOrderNo()) != null;
    }

    /**
     * 解析支付宝续租根单号。
     *
     * @param order 当前父单
     * @return 根单支付宝租赁订单号；普通父单回退自身支付宝租赁订单号
     */
    public String resolveOriginRentOrderId(Order order) {
        if (order == null) {
            return null;
        }
        RentOrderReletRelation relation = findByChildOrderNo(order.getOrderNo());
        if (relation != null && StringUtils.hasText(relation.getOriginRentOrderId())) {
            return relation.getOriginRentOrderId();
        }
        return order.getRentOrderId();
    }

    /**
     * 查询订单的续租关系视图。
     *
     * @param order 当前订单
     * @return orderType、父单、根单和续租子单列表
     */
    public Map<String, Object> buildRelationView(Order order) {
        Map<String, Object> relationView = new LinkedHashMap<>();
        RentOrderReletRelation relation = findByChildOrderNo(order.getOrderNo());
        boolean reletOrder = relation != null;
        relationView.put("orderType", reletOrder ? AlipayRentConstants.ORDER_TYPE_RELET : AlipayRentConstants.ORDER_TYPE_RENT);
        relationView.put("parentOrder", relation == null ? null : toRelationOrder(orderMapper.selectById(relation.getParentOrderId())));
        relationView.put("originOrder", relation == null ? null : toRelationOrder(orderMapper.selectById(relation.getOriginOrderId())));
        relationView.put("renewalOrders", findRenewalChildren(order));
        return relationView;
    }

    /**
     * 查询当前订单的续租子单摘要。
     * 普通原单按 parent/origin 匹配，续租父单按直接 parent 匹配，确保多次续租链路能完整展示。
     *
     * @param order 当前订单
     * @return 续租子单摘要列表
     */
    public List<Map<String, Object>> findRenewalChildren(Order order) {
        if (order == null || order.getOrderId() == null) {
            return Collections.emptyList();
        }
        List<RentOrderReletRelation> relations = relationMapper.selectList(new QueryWrapper<RentOrderReletRelation>()
                .and(wrapper -> wrapper.eq("parent_order_id", order.getOrderId())
                        .or()
                        .eq("origin_order_id", order.getOrderId()))
                .orderByDesc("id"));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.ArrayList<Map<String, Object>> children = new java.util.ArrayList<>();
        for (RentOrderReletRelation relation : relations) {
            Order childOrder = orderMapper.selectById(relation.getChildOrderId());
            Map<String, Object> childView = toRelationOrder(childOrder);
            if (childView != null) {
                children.add(childView);
            }
        }
        return children;
    }

    /**
     * 将续租关系转换为订单扩展缓存。
     * 缓存仅用于热点读取，业务判断仍应回到数据库关系表。
     */
    private void refreshExtensionCache(String orderNo, Order parentOrder, RentOrderReletRelation relation) {
        RentOrderExtension parentExtension = rentExtensionStore.loadOrderExtension(parentOrder.getOrderNo());
        RentOrderExtension extension = new RentOrderExtension();
        if (parentExtension != null) {
            extension.setPickupType(parentExtension.getPickupType());
            extension.setRentUnit(parentExtension.getRentUnit());
            extension.setAddressInfo(parentExtension.getAddressInfo());
            extension.setPluginRawParamsJson(parentExtension.getPluginRawParamsJson());
        }
        if (!StringUtils.hasText(extension.getRentUnit())) {
            extension.setRentUnit("day");
        }
        extension.setOrderType(AlipayRentConstants.ORDER_TYPE_RELET);
        extension.setParentOrderNo(relation.getParentOrderNo());
        extension.setParentRentOrderId(relation.getParentRentOrderId());
        extension.setOriginRentOrderId(relation.getOriginRentOrderId());
        rentExtensionStore.saveOrderExtension(orderNo, extension);
    }

    private Order resolveOriginOrder(Order parentOrder, RentOrderReletRelation parentRelation) {
        if (parentRelation == null || parentRelation.getOriginOrderId() == null) {
            return parentOrder;
        }
        Order originOrder = orderMapper.selectById(parentRelation.getOriginOrderId());
        return originOrder == null ? parentOrder : originOrder;
    }

    private Map<String, Object> toRelationOrder(Order order) {
        if (order == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderId", order.getOrderId());
        view.put("orderNo", order.getOrderNo());
        view.put("rentOrderId", order.getRentOrderId());
        view.put("alipayStatus", order.getAlipayStatus());
        view.put("orderTotal", order.getOrderTotal());
        view.put("orderDeposit", order.getOrderDeposit());
        view.put("orderRestDeposit", order.getOrderRestDeposit());
        view.put("rentStart", order.getOrderStart());
        view.put("rentEnd", order.getOrderEnd());
        view.put("createdAt", order.getCreatetime());
        return view;
    }
}
