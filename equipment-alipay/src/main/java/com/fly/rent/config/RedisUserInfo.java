package com.fly.rent.config;

import com.fly.rent.entity.User;
import com.common.Encryption.Aes;
import com.fly.rent.config.JwtHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * 根据redis获取用户信息公共方法
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Component
public class RedisUserInfo {
    /**
     * HttpServletRequest请求对象
     */
    @Autowired
    private HttpServletRequest httpServletRequest;

    /**
     * Redis客户端
     */
    @Resource
    private RedisClient redisClient;

    /**
     * Token解析器
     */
    @Resource
    private RequestTokenResolver requestTokenResolver;

    @Resource
    private JwtHelper jwtHelper;

    /**
     * 获取用户所有信息
     * @return 用户对象
     */
    public User userInfo() {
        String userName = jwtHelper.getUsername(requestTokenResolver.resolve(httpServletRequest));
        if (Objects.isNull(userName)) {
            return null;
        } else {
            User user = redisClient.getCacheObject("voteRedis" + Aes.encrypt(userName));
            if (Objects.isNull(user)) {
                return null;
            } else {
                return user;
            }
        }

    }
}
