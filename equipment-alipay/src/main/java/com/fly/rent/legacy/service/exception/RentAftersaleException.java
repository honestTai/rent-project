package com.fly.rent.legacy.service.exception;

/**
 * 租赁售后处理异常。
 */
public class RentAftersaleException extends RuntimeException {

    private final String subCode;
    private final String subMsg;

    public RentAftersaleException(String subCode, String subMsg) {
        super(subMsg != null ? subMsg : "租赁售后处理失败");
        this.subCode = subCode;
        this.subMsg = subMsg;
    }

    public String getSubCode() {
        return subCode;
    }

    public String getSubMsg() {
        return subMsg;
    }
}
