package com.fly.rent.config;

import lombok.Data;

/**
 * 禁止使用异常
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class NoUseException extends RuntimeException {
    /**
     * 异常信息
     */
    private String message;

    /**
     * 构造函数
     * @param message 异常信息
     */
    public NoUseException(String message) {
        super();
        this.message = message;
    }
}
