package com.fly.rent.common.support;

/**
 * 租赁接口统一业务异常。
 * 用显式 code 携带业务错误码，避免到控制器层再做字符串判断。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public class RentApiException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    /**
     * 构造函数
     * @param code 错误码
     * @param message 错误信息
     */
    public RentApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取错误码
     * @return 错误码
     */
    public int getCode() {
        return code;
    }
}
