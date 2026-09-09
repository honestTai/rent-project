package com.fly.rent.capability.buyout;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayCommerceRentOrderPayModel;
import com.alipay.api.domain.AlipayOpenMiniOrderInstallmentCreateModel;
import com.alipay.api.domain.RentPayItemDTO;
import com.alipay.api.request.AlipayCommerceRentOrderPayRequest;
import com.alipay.api.request.AlipayOpenMiniOrderInstallmentCreateRequest;
import com.alipay.api.response.AlipayCommerceRentOrderPayResponse;
import com.alipay.api.response.AlipayOpenMiniOrderInstallmentCreateResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.RedisClient;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentBuyoutPayment;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.RentBuyoutPaymentMapper;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentBuyoutPaymentService {

    private static final String STATUS_CREATING = "CREATING";
    private static final String STATUS_PAYING = "PAYING";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";

    private final RentBuyoutPaymentMapper buyoutPaymentMapper;
    private final AttrMapper attrMapper;
    private final AlipayClientService alipayClientService;
    private final AlipayPlatformConfigService alipayPlatformConfigService;
    private final ZhongtaiConfigService zhongtaiConfigService;
    private final RedisClient redisClient;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payBuyout(Order order) throws AlipayApiException {
        validateOrder(order);
        Attr attr = loadBuyoutAttr(order);
        int buyoutPrice = resolveBuyoutPrice(attr);
        if (buyoutPrice <= 0) {
            throw new IllegalArgumentException("该订单买断价格未配置");
        }

        String lockKey = "buyout_pay_lock:" + order.getOrderId();
        String lockValue = String.valueOf(System.currentTimeMillis());
        boolean locked = redisClient.tryLock(lockKey, lockValue, alipayPlatformConfigService.payLockTimeoutMinutes(), TimeUnit.MINUTES);
        if (!locked) {
            RentBuyoutPayment current = findByOrderId(order.getOrderId());
            if (current != null && StringUtils.hasText(current.getPaymentTradeNo())) {
                return buildPayResult(current);
            }
            throw new IllegalArgumentException("买断支付正在处理中，请勿重复提交");
        }

        try {
            RentBuyoutPayment payment = preparePayment(order, buyoutPrice);
            if (STATUS_PAID.equals(payment.getStatus())) {
                throw new IllegalArgumentException("该订单已完成买断支付");
            }
            if (StringUtils.hasText(payment.getPaymentTradeNo()) && STATUS_PAYING.equals(payment.getStatus())) {
                return buildPayResult(payment);
            }

            createAlipayBuyoutInstallment(order, payment);
            String tradeNo = createAlipayBuyoutPay(order, payment);
            payment.setPaymentTradeNo(tradeNo);
            payment.setStatus(STATUS_PAYING);
            payment.setLastError(null);
            payment.setUpdatedAt(new Date());
            buyoutPaymentMapper.updateById(payment);
            return buildPayResult(payment);
        } catch (IllegalArgumentException | AlipayApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("发起买断支付异常: orderId={}, msg={}", order.getOrderId(), e.getMessage(), e);
            throw new IllegalArgumentException("发起买断支付失败: " + e.getMessage());
        } finally {
            redisClient.releaseLock(lockKey, lockValue);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RentBuyoutPayment markPaid(String outInstallmentOrderId, String tradeNo, Integer payAmountCent, String rawNotify) {
        if (!StringUtils.hasText(outInstallmentOrderId)) {
            return null;
        }
        RentBuyoutPayment payment = buyoutPaymentMapper.selectOne(new QueryWrapper<RentBuyoutPayment>()
                .eq("out_installment_order_id", outInstallmentOrderId)
                .last("LIMIT 1"));
        if (payment == null) {
            return null;
        }
        if (payAmountCent != null && payment.getBuyoutPrice() != null && !payment.getBuyoutPrice().equals(payAmountCent)) {
            log.warn("买断支付回调金额与本地台账不一致: orderId={}, local={}, callback={}",
                    payment.getOrderId(), payment.getBuyoutPrice(), payAmountCent);
        }
        payment.setPaymentTradeNo(StringUtils.hasText(tradeNo) ? tradeNo : payment.getPaymentTradeNo());
        payment.setStatus(STATUS_PAID);
        payment.setPaidAt(new Date());
        payment.setRawNotify(rawNotify);
        payment.setLastError(null);
        payment.setUpdatedAt(new Date());
        buyoutPaymentMapper.updateById(payment);
        return payment;
    }

    public boolean isBuyoutPaid(Integer orderId) {
        if (orderId == null) {
            return false;
        }
        RentBuyoutPayment payment = findByOrderId(orderId);
        return payment != null && STATUS_PAID.equals(payment.getStatus());
    }

    private void validateOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!StringUtils.hasText(order.getRentOrderId())) {
            throw new IllegalArgumentException("订单缺少支付宝租赁单号，无法发起买断");
        }
        if (!AlipayRentConstants.STATUS_RECEIVED.equals(order.getAlipayStatus())) {
            throw new IllegalArgumentException("订单收货后才能发起买断");
        }
    }

    private Attr loadBuyoutAttr(Order order) {
        if (order.getAttrId() == null) {
            throw new IllegalArgumentException("订单缺少 SKU 信息，无法发起买断");
        }
        Attr attr = attrMapper.selectById(order.getAttrId());
        if (attr == null) {
            throw new IllegalArgumentException("订单 SKU 不存在，无法发起买断");
        }
        if (attr.getBuyout() == null || attr.getBuyout() != 1) {
            throw new IllegalArgumentException("该 SKU 未开启买断");
        }
        return attr;
    }

    private int resolveBuyoutPrice(Attr attr) {
        if (attr == null || attr.getBuyout() == null || attr.getBuyout() != 1) {
            return 0;
        }
        if (attr.getBuyoutval() != null && attr.getBuyoutval() > 0) {
            return attr.getBuyoutval();
        }
        return attr.getAttrDeposit() == null ? 0 : Math.max(attr.getAttrDeposit(), 0);
    }

    private RentBuyoutPayment preparePayment(Order order, int buyoutPrice) {
        RentBuyoutPayment payment = findByOrderId(order.getOrderId());
        Date now = new Date();
        if (payment == null) {
            payment = new RentBuyoutPayment();
            payment.setCreatedAt(now);
        } else if (STATUS_PAID.equals(payment.getStatus()) || (StringUtils.hasText(payment.getPaymentTradeNo()) && STATUS_PAYING.equals(payment.getStatus()))) {
            return payment;
        }
        payment.setOrderId(order.getOrderId());
        payment.setOrderNo(order.getOrderNo());
        payment.setRentOrderId(order.getRentOrderId());
        payment.setAttrId(order.getAttrId());
        payment.setUserUuid(order.getUserUuid());
        payment.setBuyoutPrice(buyoutPrice);
        payment.setOutInstallmentOrderId(buildOutInstallmentOrderId(order));
        payment.setInstallmentOrderId(null);
        payment.setPaymentTradeNo(null);
        payment.setStatus(STATUS_CREATING);
        payment.setLastError(null);
        payment.setUpdatedAt(now);
        if (payment.getId() == null) {
            buyoutPaymentMapper.insert(payment);
        } else {
            buyoutPaymentMapper.updateById(payment);
        }
        return payment;
    }

    private void createAlipayBuyoutInstallment(Order order, RentBuyoutPayment payment) throws AlipayApiException {
        AlipayOpenMiniOrderInstallmentCreateRequest request = new AlipayOpenMiniOrderInstallmentCreateRequest();
        AlipayOpenMiniOrderInstallmentCreateModel model = new AlipayOpenMiniOrderInstallmentCreateModel();
        model.setOrderId(order.getRentOrderId());
        model.setOutOrderId(order.getOrderNo());
        model.setOutInstallmentOrderId(payment.getOutInstallmentOrderId());
        model.setType(AlipayRentConstants.PAY_ITEM_TYPE_BUYOUT);
        model.setPeriodNum(1L);
        model.setStageNo(1L);
        model.setInstallmentNo("1");
        model.setInstallmentPrice(formatYuan(payment.getBuyoutPrice()));
        model.setIsFinishPerformance(Boolean.TRUE);
        AlipayUserIdentityUtil.applyUserIdentity(model, order.getUserUuid());
        request.setBizModel(model);

        AlipayOpenMiniOrderInstallmentCreateResponse response = alipayClientService.execute(request);
        payment.setRawResponse(response.getBody());
        if (!response.isSuccess()) {
            markFailed(payment, response.getSubMsg());
            throw new IllegalArgumentException(response.getSubMsg());
        }
        payment.setInstallmentOrderId(response.getInstallmentOrderId());
        payment.setUpdatedAt(new Date());
        buyoutPaymentMapper.updateById(payment);
    }

    private String createAlipayBuyoutPay(Order order, RentBuyoutPayment payment) throws AlipayApiException {
        AlipayCommerceRentOrderPayRequest request = new AlipayCommerceRentOrderPayRequest();
        AlipayCommerceRentOrderPayModel model = new AlipayCommerceRentOrderPayModel();
        model.setOrderId(order.getRentOrderId());
        model.setOutTradeNo(payment.getOutInstallmentOrderId());
        model.setPayMethod(AlipayRentConstants.PAY_METHOD_JSAPI);
        model.setPayAmount(formatYuan(payment.getBuyoutPrice()));
        model.setPayTimeoutExpress(alipayPlatformConfigService.payTimeoutExpress());
        String notifyUrl = zhongtaiConfigService.getString("alipay", "alipay.notify.trade-url");
        if (StringUtils.hasText(notifyUrl)) {
            model.setPayNotifyUrl(notifyUrl.trim());
        }

        List<RentPayItemDTO> payItems = new ArrayList<>();
        RentPayItemDTO payItem = new RentPayItemDTO();
        payItem.setType(AlipayRentConstants.PAY_ITEM_TYPE_BUYOUT);
        payItem.setPayAmount(formatYuan(payment.getBuyoutPrice()));
        payItem.setReduction(AlipayRentConstants.REDUCTION_AMOUNT);
        payItems.add(payItem);
        model.setPayItems(payItems);
        request.setBizModel(model);

        AlipayCommerceRentOrderPayResponse response = alipayClientService.execute(request);
        payment.setRawResponse(joinRaw(payment.getRawResponse(), response.getBody()));
        if (!response.isSuccess()) {
            markFailed(payment, response.getSubMsg());
            throw new IllegalArgumentException(response.getSubMsg());
        }
        return response.getTradeNo();
    }

    private RentBuyoutPayment findByOrderId(Integer orderId) {
        if (orderId == null) {
            return null;
        }
        return buyoutPaymentMapper.selectOne(new QueryWrapper<RentBuyoutPayment>()
                .eq("order_id", orderId)
                .last("LIMIT 1"));
    }

    private void markFailed(RentBuyoutPayment payment, String message) {
        if (payment == null) {
            return;
        }
        payment.setStatus(STATUS_FAILED);
        payment.setLastError(StringUtils.hasText(message) ? message : "支付宝买断支付失败");
        payment.setUpdatedAt(new Date());
        buyoutPaymentMapper.updateById(payment);
    }

    private Map<String, Object> buildPayResult(RentBuyoutPayment payment) {
        Map<String, Object> data = new HashMap<>();
        data.put("tradeNO", payment.getPaymentTradeNo());
        data.put("buyoutPrice", payment.getBuyoutPrice());
        data.put("outInstallmentOrderId", payment.getOutInstallmentOrderId());
        data.put("installmentOrderId", payment.getInstallmentOrderId());
        data.put("status", payment.getStatus());
        return data;
    }

    private String buildOutInstallmentOrderId(Order order) {
        return "EMSBUYOUT" + order.getOrderId() + "_" + System.currentTimeMillis();
    }

    private String formatYuan(Integer cents) {
        return OrderUtil.convertCentToYuan(cents == null ? 0 : cents)
                .setScale(AlipayRentConstants.DECIMAL_SCALE, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String joinRaw(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }
        if (!StringUtils.hasText(second)) {
            return first;
        }
        return first + "\n" + second;
    }
}
