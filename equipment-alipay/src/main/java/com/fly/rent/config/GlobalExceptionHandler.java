package com.fly.rent.config;

import com.fly.rent.common.support.RentApiException;
import com.fly.rent.entity.Result;
import com.fly.rent.support.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.WebUtils;

import java.util.LinkedHashMap;

/**
 * 全局异常出口。
 * 项目统一返回 Result，不再区分新旧接口的响应结构。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler implements ResponseBodyAdvice<Object> {

    /**
     * 统一拦截所有异常，转换成项目统一结构。
     * @param ex 异常
     * @param request 请求
     * @return 响应实体
     */
    @ExceptionHandler(Exception.class)
    public final ResponseEntity<?> exceptionHandler(Exception ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RentApiException) {
            log.warn("business_error code={} endpoint={} elapsedMs={} message={}",
                    ((RentApiException) ex).getCode(), request.getDescription(false), elapsedMillis(), ex.getMessage());
            return handleBusinessException(((RentApiException) ex).getCode(), ex.getMessage(), ex, headers, request);
        }
        if (ex instanceof NoUseException) {
            return handleBusinessException(500, ex.getMessage(), ex, headers, request);
        }
        if (ex instanceof TokenException) {
            log.warn("Token 异常: {}", ex.getMessage());
            return handleBusinessException(401, ex.getMessage(), ex, headers, request);
        }
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            log.warn("业务参数异常: {}", ex.getMessage());
            return handleBusinessException(400, safeBusinessMessage(ex), ex, headers, request);
        }
        return handleUnhandledException(ex, headers, request);
    }

    private long elapsedMillis() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return -1L;
        }
        Object startedAt = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest().getAttribute(TraceIdFilter.START_NANOS_ATTRIBUTE);
        return startedAt instanceof Long ? (System.nanoTime() - (Long) startedAt) / 1_000_000L : -1L;
    }

    /**
     * 处理业务异常
     * @param code 错误码
     * @param message 错误信息
     * @param ex 异常
     * @param headers 头信息
     * @param request 请求
     * @return 响应实体
     */
    private ResponseEntity<?> handleBusinessException(
            int code,
            String message,
            Exception ex,
            HttpHeaders headers,
            WebRequest request
    ) {
        return handleExceptionInternal(ex, ResultUtil.error(code, message), headers, HttpStatus.OK, request);
    }

    /**
     * 处理未捕获的异常
     * @param ex 异常
     * @param headers 头信息
     * @param request 请求
     * @return 响应实体
     */
    private ResponseEntity<?> handleUnhandledException(Exception ex, HttpHeaders headers, WebRequest request) {
        log.error("system_error code=500 endpoint={} elapsedMs={}",
                request.getDescription(false), elapsedMillis(), ex);
        return handleExceptionInternal(ex, ResultUtil.error(500, "系统错误"), headers, HttpStatus.OK, request);
    }

    private String safeBusinessMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty() ? "请求参数不正确" : message;
    }

    /**
     * 统一构建 Spring 响应对象。
     * @param ex 异常
     * @param body 响应体
     * @param headers 头信息
     * @param status 状态码
     * @param request 请求
     * @return 响应实体
     */
    protected ResponseEntity<?> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request
    ) {
        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
        }
        return new ResponseEntity<>(body, headers, status);
    }

    /**
     * 是否支持
     * @param methodParameter 方法参数
     * @param converterType 转换器类型
     * @return 是否支持
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> converterType) {
        return false;
    }

    /**
     * 写入响应体之前
     * @param body 响应体
     * @param methodParameter 方法参数
     * @param mediaType 媒体类型
     * @param converterType 转换器类型
     * @param serverHttpRequest 请求
     * @param serverHttpResponse 响应
     * @return 处理后的响应体
     */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter methodParameter,
            MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest serverHttpRequest,
            ServerHttpResponse serverHttpResponse
    ) {
        if (body instanceof Result) {
            return body;
        }
        if (body instanceof LinkedHashMap && ((LinkedHashMap<?, ?>) body).get("status") != null) {
            return ResultUtil.error(404, "Not Found" + ((LinkedHashMap<?, ?>) body).get("path"));
        }
        return ResultUtil.success(body);
    }
}
