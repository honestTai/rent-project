package com.aop.Log;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * log注解，切面
 * author sexTzt
 * time 2020-08-15
 */

@Target({METHOD, TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Log {
    //日志内容
    String value() default "系统错误";
    //日志状态
    LogEnum logType() default LogEnum.OTHER;
    LogFromEnum logFrom() default LogFromEnum.WRJ_RENT;

    /** 是否在方法执行前查询并记录老数据（需配合 mapperClass、idGetter 使用） */
    boolean needOldData() default false;
    /** 查询老数据用的 Mapper 接口类，如 DeviceMapper.class（需有按 id 查询的方法，如 deviceById/orderById/selectById） */
    Class<?> mapperClass() default Void.class;
    /** 从方法第一个参数上取主键 id 的 getter 方法名，如 "getId"、"getDeviceId"、"getOrderId" */
    String idGetter() default "getId";
}
