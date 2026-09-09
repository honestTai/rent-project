package com.fly.rent;

import com.common.config.RedisConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.common.notify.feishu.FeishuNotifyAutoConfiguration;
import com.common.oss.OssUploadAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 项目启动入口。
 * Redis 使用 Redisson + JsonJacksonCodec（见 RedissonConfig），避免 Marshalling 协议版本不兼容。
 * 同时显式导入公共 RedisTemplate 配置，保证通知、缓存等公共能力在当前模块可直接注入。
 * todo 用户手动签约台账没有记录（所有小程序的操作（opeartionOrder的地方台账没有记录）
 * todo 创建续租订单，押金扣款
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@SpringBootApplication(exclude = { RedissonAutoConfiguration.class })
@EnableTransactionManagement
@MapperScan(basePackages = {"com.fly.rent.mapper", "com.fly.rent.report.mapper", "com.common.login"})
@EnableScheduling
@ComponentScan(basePackages = "com.fly.rent")
@Import({RedisConfig.class, OssUploadAutoConfiguration.class, FeishuNotifyAutoConfiguration.class})
public class RentApplication {

    /**
     * 主函数
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RentApplication.class, args);
    }
}
