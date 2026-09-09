package com.fly.rent.legacy.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AftersaleCompensationInfoVO;
import com.alipay.api.domain.AftersalePayItemVO;
import com.alipay.api.domain.AlipayCommerceRentOrderAftersaleConfirmModel;
import com.alipay.api.domain.AlipayCommerceRentOrderAftersaleCreateModel;
import com.alipay.api.request.AlipayCommerceRentOrderAftersaleConfirmRequest;
import com.alipay.api.request.AlipayCommerceRentOrderAftersaleCreateRequest;
import com.alipay.api.response.AlipayCommerceRentOrderAftersaleConfirmResponse;
import com.alipay.api.response.AlipayCommerceRentOrderAftersaleCreateResponse;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentAftersaleService;
import com.fly.rent.legacy.service.exception.RentAftersaleException;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

/**
 * 租赁售后服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentAftersaleServiceImpl implements RentAftersaleService {

    private final AlipayClientService alipayClientService;
    private final ZhongtaiConfigService zhongtaiConfigService;

    @Override
    public AlipayCommerceRentOrderAftersaleCreateResponse createCompensationAftersale(Order order,
                                                                                       String feeType,
                                                                                       String reasonCode,
                                                                                       Integer deductAmountCent,
                                                                                       String outAftersaleId,
                                                                                       String remark) throws AlipayApiException {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!StringUtils.hasText(order.getRentOrderId())) {
            throw new IllegalArgumentException("订单缺少交易组件订单号");
        }
        if (!StringUtils.hasText(order.getUserUuid())) {
            throw new IllegalArgumentException("订单缺少买家支付宝用户ID");
        }

        log.info("创建赔付售后请求: orderId={}, rentOrderId={}, outAftersaleId={}, feeType={}, reasonCode={}, amountCent={}",
                order.getOrderId(), order.getRentOrderId(), outAftersaleId, feeType, reasonCode, deductAmountCent);
        AlipayCommerceRentOrderAftersaleCreateResponse createResponse = alipayClientService.execute(
                buildCreateRequest(order, feeType, reasonCode, deductAmountCent, outAftersaleId, remark)
        );
        log.info("创建赔付售后返回: orderId={}, outAftersaleId={}, success={}, subCode={}, subMsg={}, aftersaleId={}, body={}",
                order.getOrderId(), outAftersaleId, createResponse.isSuccess(), createResponse.getSubCode(),
                createResponse.getSubMsg(), createResponse.getAftersaleId(), createResponse.getBody());
        if (!createResponse.isSuccess()) {
            log.warn("创建售后单失败: orderId={}, outAftersaleId={}, subCode={}, subMsg={}, body={}",
                    order.getOrderId(), outAftersaleId, createResponse.getSubCode(), createResponse.getSubMsg(),
                    createResponse.getBody());
            throw new RentAftersaleException(createResponse.getSubCode(), createResponse.getSubMsg());
        }
        return createResponse;
    }

    @Override
    public AlipayCommerceRentOrderAftersaleConfirmResponse confirmCompensationAftersale(Order order,
                                                                                         String operationType,
                                                                                         String feeType,
                                                                                         String reasonCode,
                                                                                         Integer deductAmountCent,
                                                                                         String aftersaleId,
                                                                                         String outAftersaleId,
                                                                                         String outTradeNo,
                                                                                         String remark) throws AlipayApiException {
        log.info("确认赔付售后请求: orderId={}, operationType={}, aftersaleId={}, outAftersaleId={}, outTradeNo={}, reasonCode={}, amountCent={}",
                order.getOrderId(), operationType, aftersaleId, outAftersaleId, outTradeNo, reasonCode, deductAmountCent);
        AlipayCommerceRentOrderAftersaleConfirmResponse confirmResponse =
                alipayClientService.execute(buildConfirmRequest(order, operationType, feeType, reasonCode, deductAmountCent,
                        aftersaleId, outAftersaleId, outTradeNo, remark));
        log.info("确认赔付售后返回: orderId={}, operationType={}, aftersaleId={}, success={}, subCode={}, subMsg={}, tradeNo={}, body={}",
                order.getOrderId(), operationType, aftersaleId, confirmResponse.isSuccess(), confirmResponse.getSubCode(),
                confirmResponse.getSubMsg(), confirmResponse.getTradeNo(), confirmResponse.getBody());
        if (!confirmResponse.isSuccess()) {
            log.warn("确认售后单失败: orderId={}, aftersaleId={}, subCode={}, subMsg={}, body={}",
                    order.getOrderId(), aftersaleId, confirmResponse.getSubCode(), confirmResponse.getSubMsg(),
                    confirmResponse.getBody());
            throw new RentAftersaleException(confirmResponse.getSubCode(), confirmResponse.getSubMsg());
        }
        log.info("确认售后单成功: orderId={}, operationType={}, aftersaleId={}, outAftersaleId={}, tradeNo={}",
                order.getOrderId(), operationType, aftersaleId, outAftersaleId, confirmResponse.getTradeNo());
        return confirmResponse;
    }

    private AlipayCommerceRentOrderAftersaleCreateRequest buildCreateRequest(Order order,
                                                                             String feeType,
                                                                             String reasonCode,
                                                                             Integer deductAmountCent,
                                                                             String outAftersaleId,
                                                                             String remark) {
        AlipayCommerceRentOrderAftersaleCreateRequest request = new AlipayCommerceRentOrderAftersaleCreateRequest();
        AlipayCommerceRentOrderAftersaleCreateModel model = new AlipayCommerceRentOrderAftersaleCreateModel();
        model.setOrderId(order.getRentOrderId());
        model.setAftersaleType(AlipayRentConstants.AFTERSALE_TYPE_COMPENSATION);
        model.setOutAftersaleId(outAftersaleId);
        model.setReasonCode(reasonCode);
        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
        model.setPayItems(Collections.singletonList(buildPayItem(feeType, deductAmountCent)));
        if (StringUtils.hasText(remark)) {
            model.setAdditionalDescription(remark.trim());
        }
        request.setBizModel(model);
        return request;
    }

    private AlipayCommerceRentOrderAftersaleConfirmRequest buildConfirmRequest(Order order,
                                                                               String operationType,
                                                                               String feeType,
                                                                               String reasonCode,
                                                                               Integer deductAmountCent,
                                                                               String aftersaleId,
                                                                               String outAftersaleId,
                                                                               String outTradeNo,
                                                                               String remark) {
        AlipayCommerceRentOrderAftersaleConfirmRequest request = new AlipayCommerceRentOrderAftersaleConfirmRequest();
        AlipayCommerceRentOrderAftersaleConfirmModel model = new AlipayCommerceRentOrderAftersaleConfirmModel();
        model.setOperationType(operationType);
        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
        model.setAftersaleId(aftersaleId);
        model.setOutAftersaleId(outAftersaleId);
        if (AlipayRentConstants.AFTERSALE_OPERATION_APPROVE_WITH_USER_PAY.equals(operationType)) {
            model.setReasonCode(reasonCode);
            model.setPayAmount(OrderUtil.convertCentToYuan(deductAmountCent).toString());
            model.setPayItems(Collections.singletonList(buildPayItem(feeType, deductAmountCent)));
            model.setCompensationInfo(buildCompensationInfo(outTradeNo));
        } else if (AlipayRentConstants.AFTERSALE_OPERATION_MERCHANT_REJECT.equals(operationType)
                && StringUtils.hasText(reasonCode)) {
            // 商户拒绝售后时，官方要求传拒绝原因码；这里把该能力补齐，避免后台只能处理赔付通过场景。
            model.setReasonCode(reasonCode);
        }
        if (StringUtils.hasText(remark)) {
            model.setAdditionalDescription(remark.trim());
        }
        request.setBizModel(model);
        return request;
    }

    private AftersalePayItemVO buildPayItem(String feeType, Integer deductAmountCent) {
        AftersalePayItemVO item = new AftersalePayItemVO();
        item.setType(feeType);
        item.setPayAmount(OrderUtil.convertCentToYuan(deductAmountCent).toString());
        return item;
    }

    private AftersaleCompensationInfoVO buildCompensationInfo(String outTradeNo) {
        AftersaleCompensationInfoVO info = new AftersaleCompensationInfoVO();
        info.setOutTradeNo(outTradeNo);
        String notifyUrl = resolveTradeNotifyUrl();
        if (StringUtils.hasText(notifyUrl)) {
            info.setPayNotifyUrl(notifyUrl.trim());
        }
        return info;
    }

    /**
     * 支付宝售后赔付通知地址统一从中台读取。
     *
     * @return 支付宝交易通知地址
     */
    private String resolveTradeNotifyUrl() {
        return zhongtaiConfigService.getString("alipay", "alipay.notify.trade-url");
    }
}
