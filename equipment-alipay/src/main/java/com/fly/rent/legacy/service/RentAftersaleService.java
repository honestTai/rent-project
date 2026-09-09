package com.fly.rent.legacy.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.response.AlipayCommerceRentOrderAftersaleConfirmResponse;
import com.alipay.api.response.AlipayCommerceRentOrderAftersaleCreateResponse;
import com.fly.rent.entity.Order;

/**
 * 支付宝租赁售后服务。
 */
public interface RentAftersaleService {

    AlipayCommerceRentOrderAftersaleCreateResponse createCompensationAftersale(Order order,
                                                                               String feeType,
                                                                               String reasonCode,
                                                                               Integer deductAmountCent,
                                                                               String outAftersaleId,
                                                                               String remark) throws AlipayApiException;

    AlipayCommerceRentOrderAftersaleConfirmResponse confirmCompensationAftersale(Order order,
                                                                                 String operationType,
                                                                                 String feeType,
                                                                                 String reasonCode,
                                                                                 Integer deductAmountCent,
                                                                                 String aftersaleId,
                                                                                 String outAftersaleId,
                                                                                 String outTradeNo,
                                                                                 String remark) throws AlipayApiException;
}
