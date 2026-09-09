package com.common;

import com.common.Entity.ReturnResult;
import com.common.annotation.UnwrapResponseBody;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.WebUtils;

/**
 * 全局响应包装（装饰器模式）
 * 统一将 Controller 返回值包装为 ReturnResult，消除各 Controller 中手动 new ReturnResult() 的冗余代码
 */
@RestControllerAdvice
@Slf4j
public class ResponseResultBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return !methodParameter.hasMethodAnnotation(UnwrapResponseBody.class);
    }

    /**
     * 全局统一返回值包装
     * - 已经是 ReturnResult 类型的直接透传
     * - null（void 方法）包装为成功结果
     * - 其他类型包装为 ReturnResult.success(body)
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ReturnResult) {
            return body;
        }
        if (body == null) {
            return ReturnResult.success("操作成功");
        }
        return ReturnResult.success(body);
    }

    /**
     * 全局异常处理
     */
    @ExceptionHandler(Exception.class)
    public final ResponseEntity<ReturnResult<?>> exceptionHandler(Exception ex, WebRequest request) {
        log.error("ExceptionHandler: {}", ex.getMessage(), ex);
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof ResultException) {
            return this.handleResultException((ResultException) ex, headers, request);
        }
        return this.handleException(ex, headers, request);
    }

    /**
     * 自定义 ResultException 处理
     */
    protected ResponseEntity<ReturnResult<?>> handleResultException(ResultException ex, HttpHeaders headers, WebRequest request) {
        ReturnResult<?> body = ReturnResult.failure(ex.getResultStatus());
        HttpStatus status = ex.getResultStatus().getHttpStatus();
        return this.handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * 通用异常处理
     */
    protected ResponseEntity<ReturnResult<?>> handleException(Exception ex, HttpHeaders headers, WebRequest request) {
        ReturnResult<?> body = ReturnResult.failure();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return this.handleExceptionInternal(ex, body, headers, status, request);
    }

    protected ResponseEntity<ReturnResult<?>> handleExceptionInternal(
            Exception ex, ReturnResult<?> body, HttpHeaders headers, HttpStatus status, WebRequest request) {
        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
        }
        return new ResponseEntity<>(body, headers, status);
    }
}
