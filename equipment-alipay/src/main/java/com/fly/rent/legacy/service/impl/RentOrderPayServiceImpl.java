package com.fly.rent.legacy.service.impl;

import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.RedisClient;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentDepositDeductRecord;
import com.fly.rent.entity.RentInstallmentInfoEntity;

import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;

import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderPayService;
import com.fly.rent.legacy.service.RentOrderSafetyService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.legacy.service.exception.RentOrderPayException;
import com.fly.rent.support.util.OrderUtil;
import com.alipay.api.AlipayApiException;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.alipay.api.domain.AlipayCommerceRentOrderPayModel;
import com.alipay.api.domain.AlipayCommerceRentOrderPaySyncModel;
import com.alipay.api.domain.RentPayItemDTO;
import com.alipay.api.request.AlipayCommerceRentOrderPayRequest;
import com.alipay.api.request.AlipayCommerceRentOrderPaySyncRequest;
import com.alipay.api.response.AlipayCommerceRentOrderPayResponse;
import com.alipay.api.response.AlipayCommerceRentOrderPaySyncResponse;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 租赁订单支付服务实现。
 * 支付前会先做状态兜底校验，并通过分布式锁防止重复发起支付。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Slf4j
@Service
public class RentOrderPayServiceImpl implements RentOrderPayService {

    @Autowired
    private AlipayClientService alipayClientService;

    @Autowired
    private RentInstallmentInfoEntityMapper rentInstallmentInfoEntityMapper;


    @Autowired
    private RedisClient redisClient;

    @Autowired
    private RentOrderService rentOrderService;

    @Autowired
    private RentOrderSafetyService rentOrderSafetyService;
    @Autowired
    private ZhongtaiConfigService zhongtaiConfigService;
    @Autowired
    private AlipayPlatformConfigService alipayPlatformConfigService;

    /**
     * 发起支付宝租赁订单支付：校验可支付状态、加分布式锁防重复支付，调支付宝「租赁订单支付」接口，
     * 回写 tradeNo 到独立的 `paymentTradeNo` 字段。
     *
     * @param order 本地订单，需含 rentOrderId、outOrderId、orderRequestNo、orderId（查分期）；支付成功后写入 paymentTradeNo。
     * @return 支付成功时返回支付宝交易号；若命中历史兼容场景，则允许回退旧字段。
     * @throws AlipayApiException 调用支付宝支付接口异常；业务校验或接口返回失败时抛 IllegalArgumentException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String pay(Order order) throws AlipayApiException {
        rentOrderSafetyService.ensurePayable(order);

        String lockKey = "pay_lock:" + order.getRentOrderId();
        String lockValue = order.getOutOrderId();
        boolean lockAcquired = redisClient.tryLock(
                lockKey,
                lockValue,
                alipayPlatformConfigService.payLockTimeoutMinutes(),
                TimeUnit.MINUTES
        );
        if (!lockAcquired) {
            log.warn("订单{}正在支付中，禁止重复支付", order.getOrderId());
            return resolvePaymentTradeNo(order);
        }

        try {
            AlipayCommerceRentOrderPayRequest request = new AlipayCommerceRentOrderPayRequest();
            AlipayCommerceRentOrderPayModel model = new AlipayCommerceRentOrderPayModel();
            model.setOutTradeNo(order.getOrderRequestNo());
            model.setOrderId(order.getRentOrderId());
            model.setPayMethod(AlipayRentConstants.PAY_METHOD_JSAPI);

            List<RentPayItemDTO> payItems = getRentPayItemDTOS(order);
            if (payItems.isEmpty()) {
                throw new IllegalArgumentException("订单没有可支付的分期");
            }

            BigDecimal totalPayAmount = payItems.stream()
                    .map(item -> new BigDecimal(item.getPayAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.setPayAmount(totalPayAmount.setScale(AlipayRentConstants.DECIMAL_SCALE, RoundingMode.HALF_UP).toString());
            model.setPayItems(payItems);
            model.setPayTimeoutExpress(alipayPlatformConfigService.payTimeoutExpress());
            request.setBizModel(model);

            AlipayCommerceRentOrderPayResponse response = alipayClientService.execute(request);
            if (!response.isSuccess()) {
                redisClient.releaseLock(lockKey, lockValue);
                throw new IllegalArgumentException(response.getSubMsg());
            }

            // 租金支付交易号与资金授权号必须分离存储，否则后续退款和押金扣减会混号。
            order.setPaymentTradeNo(response.getTradeNo());
            order.updateById();
            return resolvePaymentTradeNo(order);
        } catch (Exception e) {
            redisClient.releaseLock(lockKey, lockValue);
            log.error("订单{}支付过程中发生异常: {}", order.getOrderId(), e.getMessage(), e);
            throw new IllegalArgumentException("支付过程中发生异常: " + e.getMessage());
        }
    }

    /**
     * 发起售后赔付/违约金的押金转支付。
     *
     * 这里走的是支付宝租赁「order.pay」接口，但支付方式改成 PRE_AUTH，
     * 并且必须带上 aftersaleId/outTradeNo，才能把这笔支付识别为售后赔付扣款。
     */
    @Override
    public String payCompensation(Order order, RentDepositDeductRecord deductRecord) throws AlipayApiException {
        validateCompensationPayable(order, deductRecord);

        String lockKey = "compensation_pay_lock:" + deductRecord.getOutTradeNo();
        String lockValue = deductRecord.getOutTradeNo();
        boolean lockAcquired = redisClient.tryLock(
                lockKey,
                lockValue,
                alipayPlatformConfigService.payLockTimeoutMinutes(),
                TimeUnit.MINUTES
        );
        if (!lockAcquired) {
            log.warn("赔付记录{}正在发起扣款，禁止重复提交", deductRecord.getId());
            return deductRecord.getTradeNo();
        }

        try {
            AlipayCommerceRentOrderPayRequest request = new AlipayCommerceRentOrderPayRequest();
            AlipayCommerceRentOrderPayModel model = new AlipayCommerceRentOrderPayModel();
            model.setOrderId(order.getRentOrderId());
            model.setAftersaleId(deductRecord.getAftersaleNo());
            model.setOutTradeNo(deductRecord.getOutTradeNo());
            model.setPayMethod(AlipayRentConstants.PAY_METHOD_PRE_AUTH);
            model.setReasonCode(deductRecord.getReasonCode());
            if (StringUtils.hasText(deductRecord.getRemark())) {
                model.setReasonDesc(deductRecord.getRemark().trim());
            }
            String notifyUrl = resolveTradeNotifyUrl();
            if (StringUtils.hasText(notifyUrl)) {
                model.setPayNotifyUrl(notifyUrl.trim());
            }

            List<RentPayItemDTO> payItems = new ArrayList<>();
            payItems.add(createCompensationPayItem(deductRecord));
            model.setPayItems(payItems);
            model.setPayAmount(OrderUtil.convertCentToYuan(deductRecord.getDeductAmount()).toString());
            model.setPayTimeoutExpress(alipayPlatformConfigService.payTimeoutExpress());
            request.setBizModel(model);

            // PAYMENT_FAIL 这类扣款失败的真实子原因只能从原始请求/返回里看，必须把 biz_content 与 response.body 落日志，
            // 否则后台只能看到 subCode/subMsg=扣款失败，无法判断是额度不足、授权已解冻还是用户支付能力不足。
            log.info("赔付记录{}发起押金转支付请求: orderId={}, aftersaleId={}, outTradeNo={}, payMethod={}, payAmount={}, reasonCode={}, feeType={}",
                    deductRecord.getId(), model.getOrderId(), model.getAftersaleId(), model.getOutTradeNo(),
                    model.getPayMethod(), model.getPayAmount(), model.getReasonCode(), deductRecord.getFeeType());

            AlipayCommerceRentOrderPayResponse response = alipayClientService.execute(request);
            log.info("赔付记录{}押金转支付返回: success={}, code={}, subCode={}, subMsg={}, tradeNo={}, body={}",
                    deductRecord.getId(), response.isSuccess(), response.getCode(), response.getSubCode(),
                    response.getSubMsg(), response.getTradeNo(), response.getBody());
            if (!response.isSuccess()) {
                log.warn("赔付记录{}发起押金转支付失败: orderId={}, aftersaleNo={}, outTradeNo={}, payAmount={}, subCode={}, subMsg={}, body={}",
                        deductRecord.getId(), order.getOrderId(), deductRecord.getAftersaleNo(), deductRecord.getOutTradeNo(),
                        model.getPayAmount(), response.getSubCode(), response.getSubMsg(), response.getBody());
                throw new RentOrderPayException(response.getSubCode(), response.getSubMsg());
            }
            return response.getTradeNo();
        } catch (Exception e) {
            log.error("赔付记录{}发起押金转支付异常: {}", deductRecord.getId(), e.getMessage(), e);
            throw e instanceof IllegalArgumentException ? (IllegalArgumentException) e
                    : new IllegalArgumentException("发起赔付扣款异常: " + e.getMessage());
        } finally {
            redisClient.releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 组装本次支付提交给支付宝的分期明细（payItems）：从本地分期表取当前未付的第一期，无分期时退化为整单总金额一笔。
     *
     * @param order 本地订单，用 orderId 查 rent_installment_info，用 orderTotal 做兜底金额。
     * @return 支付项列表，供 AlipayCommerceRentOrderPayModel.setPayItems 使用；无待付分期时返回空列表（调用方会抛异常）。
     */
    private List<RentPayItemDTO> getRentPayItemDTOS(Order order) {
        List<RentPayItemDTO> payItems = new ArrayList<>();

        List<RentInstallmentInfoEntity> installmentList = rentInstallmentInfoEntityMapper.selectList(
                new QueryWrapper<RentInstallmentInfoEntity>()
                        .eq("order_id", order.getOrderId())
                        .orderByAsc("period_no")
        );

        if (installmentList == null || installmentList.isEmpty()) {
            log.warn("订单{}没有找到分期数据，使用默认支付方式", order.getOrderId());
            payItems.add(createRentPayItem(OrderUtil.convertCentToYuan(order.getOrderTotal()), 1L));
        } else {
            RentInstallmentInfoEntity currentInstallment = null;
            for (RentInstallmentInfoEntity installment : installmentList) {
                if (installment.getStatus() == null
                        || installment.getStatus() == AlipayRentConstants.INSTALLMENT_STATUS_UNPAID) {
                    currentInstallment = installment;
                    break;
                }
            }

            if (currentInstallment == null) {
                log.warn("订单{}所有分期都已支付", order.getOrderId());
                return payItems;
            }

            payItems.add(createRentPayItem(
                    new BigDecimal(currentInstallment.getInstallmentPrice()),
                    currentInstallment.getInstallmentNo())
            );
        }


        return payItems;
    }

    /**
     * 构建单个支付项。
     * @param payAmount 支付金额
     * @param installmentNo 期数
     * @return 支付项DTO
     */
    private RentPayItemDTO createRentPayItem(BigDecimal payAmount, Long installmentNo) {
        RentPayItemDTO payItem = new RentPayItemDTO();
        payItem.setType(AlipayRentConstants.PAY_ITEM_TYPE_RENT);
        payItem.setPayAmount(payAmount.setScale(AlipayRentConstants.DECIMAL_SCALE, RoundingMode.HALF_UP).toString());
        payItem.setInstallmentNo(installmentNo);
        payItem.setReduction(AlipayRentConstants.REDUCTION_AMOUNT);
        return payItem;
    }

    /**
     * 售后赔付/违约金的 pay_item 不再绑定分期号，而是直接按售后费用类型提交。
     */
    private RentPayItemDTO createCompensationPayItem(RentDepositDeductRecord deductRecord) {
        RentPayItemDTO payItem = new RentPayItemDTO();
        payItem.setType(deductRecord.getFeeType());
        payItem.setPayAmount(OrderUtil.convertCentToYuan(deductRecord.getDeductAmount()).toString());
        payItem.setReduction(AlipayRentConstants.REDUCTION_AMOUNT);
        return payItem;
    }

    /**
     * 同步支付结果
     * @param order 订单实体
     * @throws AlipayApiException 支付宝异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncPayResult(Order order) throws AlipayApiException {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        AlipayCommerceRentOrderQueryResponse queryResponse = rentOrderService.orderStatusSearch(order);
        if (!queryResponse.isSuccess()) {
            throw new IllegalArgumentException(queryResponse.getSubMsg());
        }

        // 订单查询流程里已经会刷新本地分期状态，这里只负责触发一次同步。
    }

    /**
     * 同步支付
     * @param order 订单实体
     * @throws AlipayApiException 支付宝异常
     */
    @Override
    public void syncPay(Order order) throws AlipayApiException {
        AlipayCommerceRentOrderPaySyncRequest request = new AlipayCommerceRentOrderPaySyncRequest();
        AlipayCommerceRentOrderPaySyncModel model = new AlipayCommerceRentOrderPaySyncModel();
        // 支付同步优先走新字段，历史订单 paymentTradeNo 为空时回退旧字段，避免老数据无法继续同步。
        String paymentTradeNo = resolvePaymentTradeNo(order);
        if (paymentTradeNo == null || paymentTradeNo.trim().isEmpty()) {
            throw new IllegalArgumentException("订单缺少可同步的支付交易号");
        }
        model.setOutTradeNo(paymentTradeNo);
        model.setOrderId(order.getRentOrderId());

        List<RentPayItemDTO> payItems = new ArrayList<>();
        payItems.add(createRentPayItem(new BigDecimal("0.05"), 2L));
        model.setPayItems(payItems);
        model.setPayChannel("OTHER");
        request.setBizModel(model);

        AlipayCommerceRentOrderPaySyncResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            throw new IllegalArgumentException(response.getSubMsg());
        }
    }

    private String resolvePaymentTradeNo(Order order) {
        if (order == null) {
            return null;
        }
        if (StringUtils.hasText(order.getPaymentTradeNo())) {
            return order.getPaymentTradeNo();
        }
        // 历史兼容：旧订单仍可能把交易号写在 orderAuthNo 中。
        return order.getOrderAuthNo();
    }

    /**
     * 支付宝赔付扣款通知地址统一从中台读取。
     *
     * @return 支付宝交易通知地址
     */
    private String resolveTradeNotifyUrl() {
        return zhongtaiConfigService.getString("alipay", "alipay.notify.trade-url");
    }

    /**
     * 售后赔付扣款必须依赖已创建的私域售后单和预生成的 outTradeNo，
     * 否则支付回调无法准确认领到本地扣减台账。
     */
    private void validateCompensationPayable(Order order, RentDepositDeductRecord deductRecord) {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (deductRecord == null) {
            throw new IllegalArgumentException("扣减记录不存在");
        }
        if (!StringUtils.hasText(order.getRentOrderId())) {
            throw new IllegalArgumentException("订单缺少交易组件订单号，无法发起赔付扣款");
        }
        if (!StringUtils.hasText(deductRecord.getAftersaleNo())) {
            throw new IllegalArgumentException("扣减记录缺少售后单号，无法发起赔付扣款");
        }
        if (!StringUtils.hasText(deductRecord.getOutTradeNo())) {
            throw new IllegalArgumentException("扣减记录缺少外部支付单号，无法发起赔付扣款");
        }
        if (deductRecord.getDeductAmount() == null || deductRecord.getDeductAmount() <= 0) {
            throw new IllegalArgumentException("扣减金额不合法");
        }
    }
}
