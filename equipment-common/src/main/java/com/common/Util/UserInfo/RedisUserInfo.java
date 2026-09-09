package com.common.Util.UserInfo;

import com.common.Encryption.Aes;
import com.common.Entity.ReturnResult;
import com.common.Jwt.jwt;
import com.common.Util.Redis.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

import static com.common.Constant.constant.LOGIN_OVERDUE;
import static com.common.Constant.constant.NO_TOKEN;
import static com.common.Constant.constant.REDISNAME;
import static com.common.Constant.constant.TOKEN_OVERDUE;

@Component
public class RedisUserInfo {

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Resource
    private RedisClient redisClient;

    public UserInfo userInfo() {
        String token = resolveToken();
        if (!StringUtils.hasText(token)) {
            new ReturnResult(LOGIN_OVERDUE, NO_TOKEN, null);
            return null;
        }

        String userName = jwt.getUsername(token);
        if (!StringUtils.hasText(userName)) {
            new ReturnResult(LOGIN_OVERDUE, TOKEN_OVERDUE, null);
            return null;
        }

        UserInfo user = loadUserFromRedis(userName);
        if (user != null) {
            return user;
        }

        user = buildHeaderUserInfo();
        if (user != null) {
            return user;
        }

        new ReturnResult(LOGIN_OVERDUE, TOKEN_OVERDUE, null);
        return null;
    }

    public Integer userId() {
        UserInfo user = userInfo();
        if (user == null) {
            new ReturnResult(LOGIN_OVERDUE, NO_TOKEN, null);
            return null;
        }
        return user.getId();
    }

    public Integer userIdLoginNow() {
        UserInfo user = userInfo();
        if (user == null) {
            new ReturnResult(LOGIN_OVERDUE, NO_TOKEN, null);
            return null;
        }
        return user.getId();
    }

    private String resolveToken() {
        Object tokenAttribute = httpServletRequest.getAttribute("token");
        if (tokenAttribute != null && StringUtils.hasText(tokenAttribute.toString())) {
            return tokenAttribute.toString();
        }
        return httpServletRequest.getHeader("token");
    }

    private UserInfo buildHeaderUserInfo() {
        String userName = httpServletRequest.getHeader(UserContextHeaders.USER_NAME);
        if (!StringUtils.hasText(userName)) {
            return null;
        }

        HeaderUserInfo userInfo = new HeaderUserInfo();
        userInfo.setUserName(userName);
        userInfo.setUserPwd(httpServletRequest.getHeader(UserContextHeaders.USER_PWD));
        userInfo.setUuid(httpServletRequest.getHeader(UserContextHeaders.USER_UUID));
        userInfo.setId(parseInteger(httpServletRequest.getHeader(UserContextHeaders.USER_ID)));
        return userInfo;
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UserInfo loadUserFromRedis(String userName) {
        try {
            UserInfo user = redisClient.getCacheObject(REDISNAME + Aes.encrypt(userName));
            if (Objects.isNull(user)) {
                return null;
            }
            return user;
        } catch (Exception e) {
            return null;
        }
    }
}
