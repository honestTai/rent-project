package com.common.curd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IN 查询注解（用于集合条件）
 * 标注在 List 类型字段上
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InSearch {
    /**
     * 数据库列名，默认使用字段名
     */
    String column() default "";
}
