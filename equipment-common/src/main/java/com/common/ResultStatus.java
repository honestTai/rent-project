package com.common;

import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

/**
 * HTTP请求状态定义
 */
@ToString
@Getter
public enum ResultStatus {

    //成功
    SUCCESS(HttpStatus.OK, 0, "请求成功"),
    //错误请求
    BAD_REQUEST(HttpStatus.BAD_REQUEST, 400, "错误请求"),
    //服务器错误
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 500, "系统错误"),
    //没有权限访问
    NO_RIGHT_ACCESS(HttpStatus.FORBIDDEN, 403, "无权访问"),
    //没有登录或者登录过期
    NO_LOGIN(HttpStatus.UNAUTHORIZED, 401, "请登录"),
    //账号密码错误
    ERROR_NUM_PWD(HttpStatus.UNAUTHORIZED, 402, "账号密码错误");

    /**
     * 返回的HTTP状态码,  符合http请求
     */
    private HttpStatus httpStatus;
    /**
     * 业务异常码
     */
    private Integer code;
    /**
     * 业务异常信息描述
     */
    private String message;

    ResultStatus(HttpStatus httpStatus, Integer code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}