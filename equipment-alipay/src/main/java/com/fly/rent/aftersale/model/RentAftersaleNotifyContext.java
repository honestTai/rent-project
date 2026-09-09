package com.fly.rent.aftersale.model;

import lombok.Data;

/**
 * 售后通知上下文。
 *
 * 这个对象把支付宝 form 表单通知里的“公共字段 + biz_content 业务字段”收敛成
 * 一份稳定的领域输入，避免后续业务层直接和原始 Map<String, String> 耦合。
 */
@Data
public class RentAftersaleNotifyContext {

    private String notifyId;
    private String msgMethod;
    private String orderId;
    private String outOrderId;
    private String aftersaleType;
    private String aftersaleId;
    private String outAftersaleId;
    private String aftersaleStatus;
    private String sourceType;
    private String operationType;
    private String needOperation;
    private String buyerId;
    private String buyerOpenId;
    private String payloadJson;
}
