package com.fly.rent.legacy.service.impl;

import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderFulfillmentService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.order.RentContractAlipaySyncService;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentFinishModel;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentReceiveModel;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentSendModel;
import com.alipay.api.domain.FulfillmentDeliveryInfo;
import com.alipay.api.request.AlipayCommerceRentOrderFulfillmentFinishRequest;
import com.alipay.api.request.AlipayCommerceRentOrderFulfillmentReceiveRequest;
import com.alipay.api.request.AlipayCommerceRentOrderFulfillmentSendRequest;
import com.alipay.api.response.AlipayCommerceRentOrderFulfillmentFinishResponse;
import com.alipay.api.response.AlipayCommerceRentOrderFulfillmentReceiveResponse;
import com.alipay.api.response.AlipayCommerceRentOrderFulfillmentSendResponse;
import com.alipay.api.domain.RentOrderStatementInfoVO;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 租赁订单履约服务实现。
 * 负责在本地状态和支付宝履约状态之间保持一致。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Slf4j
@Service
public class RentOrderFulfillmentServiceImpl implements RentOrderFulfillmentService {

    @Autowired
    private AlipayClientService alipayClientService;
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RentOrderService rentOrderService;
    @Autowired
    private AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;
    @Autowired
    private RentContractAlipaySyncService contractAlipaySyncService;

    /**
     * 履约发货/归还发货：调支付宝「租赁订单履约发货」接口，成功后按 type 更新本地订单状态（商家发货->DELIVERED，用户归还发货->RETURN_DELIVERED）。
     *
     * @param order        本地订单，提供 rentOrderId、userUuid、rentSendStatus（与 type 一致）。
     * @param deliveryList 物流信息列表，支付宝协议要求的 FulfillmentDeliveryInfo 列表。
     * @throws AlipayApiException 调用支付宝接口异常；返回失败时抛 IllegalArgumentException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(Order order, List<FulfillmentDeliveryInfo> deliveryList) throws AlipayApiException {
        String status = order.getRentSendStatus();
        String oldStatus = order.getAlipayStatus();
        validateSendRequest(order, status);

        AlipayCommerceRentOrderFulfillmentSendRequest request = new AlipayCommerceRentOrderFulfillmentSendRequest();
        AlipayCommerceRentOrderFulfillmentSendModel model = new AlipayCommerceRentOrderFulfillmentSendModel();
        model.setOrderId(order.getRentOrderId());
        AlipayUserIdentityUtil.applyUserIdentity(model, order.getUserUuid());
        model.setType(status);
        model.setDeliveryList(deliveryList);
        request.setBizModel(model);

        AlipayCommerceRentOrderFulfillmentSendResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            log.error("订单发货失败: {}", response.getSubMsg());
            throw new IllegalArgumentException(response.getSubMsg());
        }

        if ("MERCHANT_DELIVERY_SEND".equals(status)) {
            order.setAlipayStatus(AlipayRentConstants.STATUS_DELIVERED);
        } else {
            order.setAlipayStatus(AlipayRentConstants.STATUS_RETURN_DELIVERED);
        }
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "履约发货");
    }

    /**
     * 履约确认收货/归还确认收货：调支付宝「租赁订单履约确认收货」接口，成功后按 type 更新本地状态（商家发货确认->RECEIVED，用户归还确认->RETURN_RECEIVED）。
     *
     * @param order  本地订单，提供 rentOrderId、userUuid。
     * @param status 履约类型，如 MERCHANT_DELIVERY_RECEIVED、USER_RETURN_RECEIVED 等，与支付宝协议一致。
     * @throws AlipayApiException 调用支付宝接口异常；返回失败时抛 IllegalArgumentException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Order order, String status) throws AlipayApiException {
        String oldStatus = order.getAlipayStatus();
        AlipayCommerceRentOrderFulfillmentReceiveRequest request = new AlipayCommerceRentOrderFulfillmentReceiveRequest();
        AlipayCommerceRentOrderFulfillmentReceiveModel model = new AlipayCommerceRentOrderFulfillmentReceiveModel();
        model.setOrderId(order.getRentOrderId());
        AlipayUserIdentityUtil.applyUserIdentity(model, order.getUserUuid());
        model.setType(status);
        request.setBizModel(model);

        AlipayCommerceRentOrderFulfillmentReceiveResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            log.error("确认收货失败: {}", response.getSubMsg());
            throw new IllegalArgumentException(response.getSubMsg());
        }

        if ("MERCHANT_DELIVERY_RECEIVED".equals(status)) {
            order.setAlipayStatus(AlipayRentConstants.STATUS_RECEIVED);
        } else {
            order.setAlipayStatus(AlipayRentConstants.STATUS_RETURN_RECEIVED);
        }
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "履约收货");
        if ("MERCHANT_DELIVERY_RECEIVED".equals(status)) {
            contractAlipaySyncService.syncContractAfterReceive(order.getOrderId(), "履约收货");
        }
    }

    /**
     * 履约完结：调支付宝「租赁订单履约完结」接口，按 status 完结租赁或归还流程，成功后更新本地订单状态。
     *
     * @param order  本地订单，提供 rentOrderId、userUuid。
     * @param status 完结状态，与支付宝协议约定一致。
     * @throws AlipayApiException 调用支付宝接口异常；返回失败时抛 IllegalArgumentException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Order order, String status) throws AlipayApiException {
        String oldStatus = order.getAlipayStatus();
        AlipayCommerceRentOrderFulfillmentFinishRequest request = new AlipayCommerceRentOrderFulfillmentFinishRequest();
        AlipayCommerceRentOrderFulfillmentFinishModel model = new AlipayCommerceRentOrderFulfillmentFinishModel();
        model.setOrderId(order.getRentOrderId());
        AlipayUserIdentityUtil.applyUserIdentity(model, order.getUserUuid());
        model.setStatus(status);
        request.setBizModel(model);

        AlipayCommerceRentOrderFulfillmentFinishResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            log.error("订单完结失败: {}", response.getSubMsg());
            throw new IllegalArgumentException(response.getSubMsg());
        }

        order.setAlipayStatus(AlipayRentConstants.STATUS_FINISHED);
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "履约完结");
    }

    /**
     * 发货前先校验本地与支付宝侧状态是否允许继续履约。
     * @param order 订单实体
     * @param status 状态
     * @throws AlipayApiException 支付宝异常
     */
    private void validateSendRequest(Order order, String status) throws AlipayApiException {
        if ("MERCHANT_DELIVERY_SEND".equals(status)) {
            AlipayCommerceRentOrderQueryResponse queryResponse = rentOrderService.orderStatusSearch(order);
            if (!queryResponse.isSuccess()) {
                throw new IllegalArgumentException("查询支付宝订单状态失败: " + queryResponse.getSubMsg());
            }
            List<RentOrderStatementInfoVO> statements = queryResponse.getRentStatementInfos();
            boolean hasPaid = statements != null && statements.stream()
                    .anyMatch(s -> AlipayRentConstants.STATUS_PAID.equals(s.getStatementStatus()));
            if (!hasPaid) {
                throw new IllegalArgumentException("订单未支付，无法发货，支付宝当前状态: " + queryResponse.getStatus());
            }
            return;
        }

        if (!AlipayRentConstants.STATUS_RECEIVED.equals(order.getAlipayStatus())) {
            throw new IllegalArgumentException("订单状态不正确，无法归还发货，当前状态: " + order.getAlipayStatus());
        }
    }
}
