package com.fly.rent.web.support;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作台账事件，由 AOP 切面或 WebOperLogHelper 发布，异步监听器消费后写入 DB。
 */
@Getter
@AllArgsConstructor
public class OrderOperLogEvent {

    private final Integer orderId;
    private final String orderNo;
    private final String operType;
    private final String operDesc;
    private final String beforeStatus;
    private final String afterStatus;
    private final Object requestBody;
    private final Object resultBody;
    private final boolean success;
    private final String failReason;
    private final String operator;
}
