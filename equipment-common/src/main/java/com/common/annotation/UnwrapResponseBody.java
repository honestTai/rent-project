package com.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记在 Controller 方法上时，{@link com.common.ResponseResultBodyAdvice} 不再二次包装返回值，
 * 用于已自行构造与 {@link com.common.Entity.ReturnResult} 同形结构、且需 {@code @JsonRawValue} 直出 JSON 的场景。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UnwrapResponseBody {
}
