package com.common.curd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 区间查询注解（用于日期范围等）
 * 标注在 List 类型字段上，取 list[0] 和 list[1] 作为 between 的起止值
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BetweenSearch {
    /**
     * 数据库列名，默认使用字段名
     */
    String column() default "";
}
