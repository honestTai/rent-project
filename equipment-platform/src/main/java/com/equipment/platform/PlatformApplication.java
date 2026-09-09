package com.equipment.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 中台服务启动类。
 * <p>
 * 中台只承载跨系统配置、通知通道、任务配置和总览入口，不直接侵入三套业务系统的业务实现。
 * </p>
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.aop", "com.common", "com.equipment.platform"})
@MapperScan(basePackages = {
        "com.equipment.platform.mapper",
        "com.common.zhongtai.config",
        "com.common.login",
        "com.common.log.mapper"
})
public class PlatformApplication {

    /**
     * 启动中台服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
