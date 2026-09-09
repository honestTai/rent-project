package com.fly.rent.web.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要自动记录操作台账的方法。
 * 配合 {@link OperLogContext#begin} 使用，切面自动捕获成功/失败并异步写入台账。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {
}
