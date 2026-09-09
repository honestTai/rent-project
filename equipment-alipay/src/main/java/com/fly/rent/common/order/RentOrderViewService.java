package com.fly.rent.common.order;

import com.fly.rent.capability.buyout.RentBuyoutPaymentService;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.model.RentUserProfileExtra;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.GoodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统一的订单视图查询服务。
 */
@RequiredArgsConstructor
@Service
public class RentOrderViewService {

    private final GoodMapper goodMapper;
    private final AttrMapper attrMapper;
    private final RentExtensionStore extensionStore;
    private final RentViewAssembler viewAssembler;
    private final RentBuyoutPaymentService buyoutPaymentService;

    public RentViews.RentalOrderView getOrderView(Order order) {
        Good good = order.getGoodId() == null ? null : goodMapper.selectById(order.getGoodId());
        Attr attr = order.getAttrId() == null ? null : attrMapper.selectById(order.getAttrId());
        RentOrderExtension extension = extensionStore.loadOrderExtension(order.getOrderNo());
        RentUserProfileExtra extra = extensionStore.loadUserProfileExtra(order.getUserUuid());
        RentViews.RentalOrderView view = viewAssembler.toRentalOrderView(order, good, attr, extension, extra);
        view.setBuyoutPaid(buyoutPaymentService.isBuyoutPaid(order.getOrderId()));
        return view;
    }

    public List<RentViews.RentalOrderView> getOrderViews(List<Order> orders, String userUuid) {
        if (CollectionUtils.isEmpty(orders)) {
            return Collections.emptyList();
        }
        List<Integer> goodIds = orders.stream()
                .map(Order::getGoodId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Good> goodMap = Collections.emptyMap();
        if (!goodIds.isEmpty()) {
            List<Good> goods = goodMapper.selectBatchIds(goodIds);
            goodMap = goods == null ? Collections.emptyMap()
                    : goods.stream().filter(g -> g.getGoodId() != null).collect(Collectors.toMap(Good::getGoodId, g -> g, (a, b) -> a));
        }
        List<Integer> attrIds = orders.stream()
                .map(Order::getAttrId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Attr> attrMap = Collections.emptyMap();
        if (!attrIds.isEmpty()) {
            List<Attr> attrs = attrMapper.selectBatchIds(attrIds);
            attrMap = attrs == null ? Collections.emptyMap()
                    : attrs.stream().filter(a -> a.getAttrId() != null).collect(Collectors.toMap(Attr::getAttrId, a -> a, (a, b) -> a));
        }
        RentUserProfileExtra extra = StringUtils.hasText(userUuid) ? extensionStore.loadUserProfileExtra(userUuid) : new RentUserProfileExtra();
        List<String> orderNos = orders.stream()
                .map(Order::getOrderNo)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, RentOrderExtension> extensionMap = orderNos.isEmpty() ? Collections.emptyMap() : extensionStore.loadOrderExtensions(orderNos);
        List<RentViews.RentalOrderView> result = new ArrayList<>(orders.size());
        for (Order order : orders) {
            Good good = order.getGoodId() == null ? null : goodMap.get(order.getGoodId());
            Attr attr = order.getAttrId() == null ? null : attrMap.get(order.getAttrId());
            RentOrderExtension extension = order.getOrderNo() == null ? null : extensionMap.get(order.getOrderNo());
            RentViews.RentalOrderView view = viewAssembler.toRentalOrderView(order, good, attr, extension, extra);
            view.setBuyoutPaid(buyoutPaymentService.isBuyoutPaid(order.getOrderId()));
            result.add(view);
        }
        return result;
    }
}
