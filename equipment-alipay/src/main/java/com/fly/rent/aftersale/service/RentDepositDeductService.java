package com.fly.rent.aftersale.service;

import com.fly.rent.aftersale.model.RentAftersaleNotifyContext;
import com.fly.rent.web.support.WebRequest;

import java.util.List;
import java.util.Map;

/**
 * 押金赔付/违约金扣减领域服务。
 *
 * 职责边界：
 * 1. 处理后台创建扣减、确认扣减、查询扣减记录；
 * 2. 处理售后通知和赔付支付通知，并把支付宝侧结果落到本地台账；
 * 3. 不负责 HTTP 验签、表单解析和路由分发，这些由回调层负责。
 */
public interface RentDepositDeductService {

    Map<String, Object> deductDeposit(WebRequest req);

    List<Map<String, Object>> deductRecords(WebRequest req);

    Map<String, Object> deductRecordsPage(WebRequest req);

    Map<String, Object> confirmDeductRecord(WebRequest req);

    void handleAftersaleNotify(RentAftersaleNotifyContext context);

    boolean handleCompensationPaySuccess(String outTradeNo,
                                         String outOrderId,
                                         String tradeNo,
                                         Integer payAmountCent,
                                         String payloadJson);
}
