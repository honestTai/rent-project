package com.fly.rent.common.user;

import com.fly.rent.common.support.RentApiException;
import com.fly.rent.config.RedisUserInfo;
import com.fly.rent.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 当前会话用户读取服务。
 * 统一隔离旧系统的 Redis 登录结构，控制器和业务服务只拿业务需要的用户信息。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@RequiredArgsConstructor
@Service
public class RentCurrentUserService {

    private final RedisUserInfo redisUserInfo;

    /**
     * 获取当前会话用户，如果未登录则抛出异常
     * @return 用户对象
     */
    public User requireSessionUser() {
        User user = redisUserInfo.userInfo();
        if (user == null || !StringUtils.hasText(user.getUuid())) {
            throw new RentApiException(401, "未登录或登录已失效");
        }
        return user;
    }

    /**
     * 获取当前用户UUID
     * @return UUID字符串
     */
    public String requireUserUuid() {
        return requireSessionUser().getUuid();
    }

    /**
     * 历史系统约定 uuid = -1 表示后台管理员会话，这里保留该规则。
     * @return 是否为管理员会话
     */
    public boolean isAdminSession() {
        return "-1".equals(requireSessionUser().getUuid());
    }

    /**
     * 校验是否为管理员会话，如果不是则抛出异常
     */
    public void requireAdminSession() {
        if (!isAdminSession()) {
            throw new RentApiException(403, "当前会话无后台操作权限");
        }
    }
}
