package com.fly.rent.config;

import lombok.Data;

/**
 * token异常
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class TokenException extends RuntimeException {
    /**
     * 异常信息
     */
    private String message;

    /**
     * 构造函数
     * @param message 异常信息
     */
    public TokenException(String message) {
        super();
        this.message = message;
    }
}
