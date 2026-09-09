package com.fly.rent.legacy.service.impl;

import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.RedisClient;
import com.fly.rent.common.order.RentOrderReletRelationService;
import com.fly.rent.web.support.WebOperLogHelper;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderSafetyService;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayCommerceRentOrderCloseModel;
import com.alipay.api.domain.AlipayCommerceRentOrderQueryModel;
import com.alipay.api.request.AlipayCommerceRentOrderCloseRequest;
import com.alipay.api.request.AlipayCommerceRentOrderQueryRequest;
import com.alipay.api.response.AlipayCommerceRentOrderCloseResponse;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 租赁订单安全保障实现。
 * 把锁控制、限流、超时关单等横切能力集中起来，避免散落在业务方法中。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Slf4j
@Service
public class RentOrderSafetyServiceImpl implements RentOrderSafetyService {

    private static final String CREATE_LOCK_KEY_PREFIX = "rent_order:create_lock:";
    private static final String CREATE_LIMIT_KEY_PREFIX = "rent_order:create_limit:";
    private static final List<String> UNPAID_STATUSES = Arrays.asList(
            AlipayRentConstants.STATUS_CREATED,
            AlipayRentConstants.STATUS_SIGNED,
            AlipayRentConstants.STATUS_APPROVED
    );

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private RentOrderReletRelationService reletRelationService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AlipayClientService alipayClientService;

    @Autowired
    private WebOperLogHelper operLogHelper;

    @Autowired
    private AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;

    @Autowired
    private AlipayPlatformConfigService alipayPlatformConfigService;

    private static final String AUTO_CLOSE_OPER_TYPE = "CLOSE";
    private static final String AUTO_CLOSE_OPER_DESC = "自动关闭超时未支付订单";

    /**
     * 获取下单锁
     * @param userUuid 用户UUID
     * @return 锁值
     */
    @Override
    public String acquireCreateLock(String userUuid) {
        String lockKey = CREATE_LOCK_KEY_PREFIX + userUuid;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisClient.tryLock(
                lockKey,
                lockValue,
                alipayPlatformConfigService.createLockTimeoutSeconds(),
                TimeUnit.SECONDS
        );
        if (!locked) {
            throw new IllegalArgumentException("下单处理中，请勿重复提交");
        }
        return lockValue;
    }

    /**
     * 释放下单锁
     * @param userUuid 用户UUID
     * @param lockValue 锁值
     */
    @Override
    public void releaseCreateLock(String userUuid, String lockValue) {
        if (userUuid == null || lockValue == null) {
            return;
        }
        redisClient.releaseLock(CREATE_LOCK_KEY_PREFIX + userUuid, lockValue);
    }

    /**
     * 确保允许创建普通租赁订单。
     * 普通租赁只统计普通未支付单，避免续租子单占用普通租赁的未支付名额。
     *
     * @param userUuid 用户UUID
     * @throws AlipayApiException 支付宝异常
     */
    @Override
    public void ensureCreateAllowed(String userUuid) throws AlipayApiException {
        ensureCreateAllowedByOrderType(userUuid, false);
    }

    /**
     * 确保允许创建续租订单。
     * 续租使用独立的未支付数量和频率限制，不阻断用户继续发起普通租赁。
     *
     * @param userUuid 用户UUID
     * @throws AlipayApiException 支付宝异常
     */
    @Override
    public void ensureReletCreateAllowed(String userUuid) throws AlipayApiException {
        ensureCreateAllowedByOrderType(userUuid, true);
    }

    /**
     * 按订单类型校验创建限制。
     * 这里仍共用超时关单兜底，但未支付数量与频率限制按普通租赁/续租拆分。
     *
     * @param userUuid 用户UUID
     * @param reletOrder true 表示续租，false 表示普通租赁
     * @throws AlipayApiException 支付宝异常
     */
    private void ensureCreateAllowedByOrderType(String userUuid, boolean reletOrder) throws AlipayApiException {
        closeExpiredUnpaidOrders(userUuid);

        int unpaidCount = countUnpaidCreateOrders(userUuid, reletOrder);
        int unpaidOrderMaxCount = alipayPlatformConfigService.unpaidOrderMaxCount();
        if (unpaidCount >= unpaidOrderMaxCount) {
            String orderTypeText = reletOrder ? "续租" : "租赁";
            throw new IllegalArgumentException("您有 " + unpaidOrderMaxCount + " 笔未支付"
                    + orderTypeText + "订单，请先关闭后再下单");
        }

        String limitKey = CREATE_LIMIT_KEY_PREFIX + (reletOrder ? "relet:" : "rent:") + userUuid;
        Long count = redisClient.increment(limitKey, 1L);
        if (count != null && count == 1L) {
            redisClient.expire(limitKey, alipayPlatformConfigService.createLimitWindowMinutes(), TimeUnit.MINUTES);
        }
        if (count != null && count > alipayPlatformConfigService.createLimitMaxTimes()) {
            throw new IllegalArgumentException((reletOrder ? "续租" : "下单") + "过于频繁，请稍后再试");
        }
    }

    /**
     * 统计指定类型的未支付创建中订单。
     * 续租身份保存在续租关系表中，因此这里先按用户和未支付状态取候选订单，再按关系表拆分类型。
     *
     * @param userUuid 用户UUID
     * @param reletOrder true 表示统计续租单，false 表示统计普通租赁单
     * @return 当前类型的未支付订单数量
     */
    private int countUnpaidCreateOrders(String userUuid, boolean reletOrder) {
        List<Order> unpaidOrders = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("user_uuid", userUuid)
                .in("alipay_status", UNPAID_STATUSES));
        if (unpaidOrders == null || unpaidOrders.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Order order : unpaidOrders) {
            if (isReletOrder(order) == reletOrder) {
                count++;
            }
        }
        return count;
    }

    /**
     * 确保订单可支付
     * @param order 订单实体
     * @throws AlipayApiException 支付宝异常
     */
    @Override
    public void ensurePayable(Order order) throws AlipayApiException {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!UNPAID_STATUSES.contains(order.getAlipayStatus())) {
            throw new IllegalArgumentException("订单状态不支持支付，当前状态: " + order.getAlipayStatus());
        }
        ensureReletApprovedBeforePay(order);
        if (!isExpired(order)) {
            return;
        }

        closeExpiredOrder(order, WebOperLogHelper.OPERATOR_SYSTEM);
        Order latest = orderMapper.selectById(order.getOrderId());
        if (latest == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (AlipayRentConstants.STATUS_CLOSED.equals(latest.getAlipayStatus())) {
            throw new IllegalArgumentException("订单超过 30 分钟未支付，已自动关闭，请重新下单");
        }
        if (AlipayRentConstants.STATUS_PAID.equals(latest.getAlipayStatus())) {
            throw new IllegalArgumentException("订单已支付，请勿重复支付");
        }
        if (!UNPAID_STATUSES.contains(latest.getAlipayStatus())) {
            throw new IllegalArgumentException("订单状态不支持支付，当前状态: " + latest.getAlipayStatus());
        }
        ensureReletApprovedBeforePay(latest);
    }

    /**
     * 校验续租子单必须先经过后台审核。
     * 普通租赁单沿用历史 CREATED/SIGNED/APPROVED 可支付规则；只有续租关系表标记为续租的订单，
     * 才强制要求商家审核到 APPROVED 后再允许小程序发起租金支付。
     *
     * @param order 待支付订单
     */
    private void ensureReletApprovedBeforePay(Order order) {
        if (!isReletOrder(order)) {
            return;
        }
        if (!AlipayRentConstants.STATUS_APPROVED.equals(order.getAlipayStatus())) {
            throw new IllegalArgumentException("续租订单需商家审核通过后才能支付");
        }
    }

    /**
     * 判断订单是否为续租子单。
     * 续租关系保存在数据库关系表中，不依赖押金金额等派生字段，避免普通免押订单被误判。
     *
     * @param order 订单实体
     * @return true 表示当前订单是续租子单
     */
    private boolean isReletOrder(Order order) {
        if (order == null || order.getOrderNo() == null) {
            return false;
        }
        return reletRelationService.isReletOrder(order);
    }

    /**
     * 关闭过期未支付订单
     * @throws AlipayApiException 支付宝异常
     */
    @Override
    public void closeExpiredUnpaidOrders() throws AlipayApiException {
        closeExpiredOrders(new QueryWrapper<Order>()
                .in("alipay_status", UNPAID_STATUSES)
                .le("created_at", expireBefore()), WebOperLogHelper.OPERATOR_SCHEDULED_TASK);
    }

    /**
     * 关闭指定用户的过期未支付订单
     * @param userUuid 用户UUID
     * @throws AlipayApiException 支付宝异常
     */
    @Override
    public void closeExpiredUnpaidOrders(String userUuid) throws AlipayApiException {
        closeExpiredOrders(new QueryWrapper<Order>()
                .eq("user_uuid", userUuid)
                .in("alipay_status", UNPAID_STATUSES)
                .le("created_at", expireBefore()), WebOperLogHelper.OPERATOR_SYSTEM);
    }

    /**
     * 统一关闭查询出的超时订单。
     * @param wrapper 查询条件
     * @throws AlipayApiException 支付宝异常
     */
    private void closeExpiredOrders(Wrapper<Order> wrapper, String operator) throws AlipayApiException {
        List<Order> orders = orderMapper.selectList(wrapper);
        for (Order order : orders) {
            closeExpiredOrder(order, operator);
        }
    }

    /**
     * 优先同步支付宝侧状态，再决定是否执行关单。
     * @param order 订单实体
     * @throws AlipayApiException 支付宝异常
     */
    private void closeExpiredOrder(Order order, String operator) throws AlipayApiException {
        if (order == null || !UNPAID_STATUSES.contains(order.getAlipayStatus()) || !isExpired(order)) {
            return;
        }
        String beforeStatus = order.getAlipayStatus();

        if (order.getRentOrderId() == null || order.getOutOrderId() == null) {
            markClosed(order);
            logAutoCloseSuccess(order, beforeStatus, operator, "本地订单缺少支付宝单号，直接关闭");
            return;
        }

        String remoteStatus = queryOrderStatus(order);
        if (remoteStatus != null && !UNPAID_STATUSES.contains(remoteStatus)) {
            updateStatus(order, remoteStatus, "自动关单前状态同步");
            return;
        }

        AlipayCommerceRentOrderCloseRequest request = new AlipayCommerceRentOrderCloseRequest();
        AlipayCommerceRentOrderCloseModel model = new AlipayCommerceRentOrderCloseModel();
        model.setOrderId(order.getRentOrderId());
        model.setOutOrderId(order.getOutOrderId());
        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
        model.setReasonCode("3150");
        model.setReasonDesc("超时未支付，系统自动关闭");
        request.setBizModel(model);

        AlipayCommerceRentOrderCloseResponse response = alipayClientService.execute(request);
        if (response.isSuccess()) {
            markClosed(order);
            logAutoCloseSuccess(order, beforeStatus, operator, "调用支付宝关单成功");
            log.info("订单{}超过 30 分钟未支付，已自动关闭", order.getOrderId());
            return;
        }

        operLogHelper.logFailure(order,
                AUTO_CLOSE_OPER_TYPE,
                AUTO_CLOSE_OPER_DESC,
                beforeStatus,
                buildAutoCloseRequest(order, operator),
                response.getSubMsg(),
                operator);
        log.warn("订单{}自动关闭失败: {}", order.getOrderId(), response.getSubMsg());
    }

    /**
     * 查询支付宝侧的实时状态，避免本地状态滞后导致误关单。
     * @param order 订单实体
     * @return 订单状态
     * @throws AlipayApiException 支付宝异常
     */
    private String queryOrderStatus(Order order) throws AlipayApiException {
        AlipayCommerceRentOrderQueryRequest request = new AlipayCommerceRentOrderQueryRequest();
        AlipayCommerceRentOrderQueryModel model = new AlipayCommerceRentOrderQueryModel();
        model.setOutOrderId(order.getOutOrderId());
        model.setOrderId(order.getRentOrderId());
        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
        request.setBizModel(model);

        AlipayCommerceRentOrderQueryResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            log.warn("订单{}自动关单前查询状态失败: {}", order.getOrderId(), response.getSubMsg());
            return null;
        }
        return response.getStatus();
    }

    /**
     * 标记订单为关闭
     * @param order 订单实体
     */
    private void markClosed(Order order) {
        updateStatus(order, AlipayRentConstants.STATUS_CLOSED, "超时自动关单");
    }

    private void logAutoCloseSuccess(Order order, String beforeStatus, String operator, String resultDesc) {
        operLogHelper.logSuccess(order,
                AUTO_CLOSE_OPER_TYPE,
                AUTO_CLOSE_OPER_DESC,
                beforeStatus,
                order.getAlipayStatus(),
                buildAutoCloseRequest(order, operator),
                resultDesc,
                operator);
    }

    private String buildAutoCloseRequest(Order order, String operator) {
        return String.format("source=%s, orderId=%s, orderNo=%s, rentOrderId=%s, outOrderId=%s",
                operator,
                order.getOrderId(),
                order.getOrderNo(),
                order.getRentOrderId(),
                order.getOutOrderId());
    }

    /**
     * 自动兜底链路里只要真实推进了状态，就统一在这里落库并补发生命周期通知。
     * 这样可以避免自动关单前同步、自动关闭等分支各自漏发飞书。
     *
     * @param order 订单实体
     * @param status 状态
     * @param source 来源说明
     */
    private void updateStatus(Order order, String status, String source) {
        String oldStatus = order.getAlipayStatus();
        order.setAlipayStatus(status);
        orderMapper.updateById(order);
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, status, source);
    }

    /**
     * 判断订单是否过期
     * @param order 订单实体
     * @return 是否过期
     */
    private boolean isExpired(Order order) {
        return order.getCreatetime() != null && order.getCreatetime() <= expireBefore();
    }

    /**
     * 获取过期时间阈值
     * @return 时间戳
     */
    private long expireBefore() {
        return System.currentTimeMillis()
                - alipayPlatformConfigService.unpaidOrderTimeoutMinutes() * AlipayRentConstants.MILLIS_PER_MINUTE;
    }
}
