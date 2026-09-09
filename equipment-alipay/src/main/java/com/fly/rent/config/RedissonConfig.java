package com.fly.rent.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 使用 JsonJacksonCodec，避免默认 MarshallingCodec 的协议版本不兼容
 * （如 Unsupported protocol version 91）。
 *
 * @author HonestTat
 * @since 2026-03-15
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        SingleServerConfig serverConfig = config.useSingleServer().setAddress(address);
        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }
        config.setCodec(new JsonJacksonCodec());
        return Redisson.create(config);
    }
}
