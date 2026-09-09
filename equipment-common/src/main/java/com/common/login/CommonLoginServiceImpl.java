package com.common.login;

import com.common.Encryption.Aes;
import com.common.Entity.ReturnResult;
import com.common.Jwt.jwt;
import com.common.Util.Redis.RedisClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

import static com.common.Constant.constant.LOGIN_FAIL_USERNAME;
import static com.common.Constant.constant.LOGIN_FAIL_USERPWD;
import static com.common.Constant.constant.REDISNAME;
import static com.common.Constant.constant.SUCCESS;

@Service
public class CommonLoginServiceImpl implements LoginService {

    private final UserLoginMapper userLoginMapper;

    @Resource
    private RedisClient redisClient;

    public CommonLoginServiceImpl(UserLoginMapper userLoginMapper) {
        this.userLoginMapper = userLoginMapper;
    }

    @Override
    public ReturnResult login(LoginParam loginParam, HttpServletRequest httpServletRequest) {
        if (userLoginMapper.userName(loginParam) == 0) {
            return new ReturnResult(LOGIN_FAIL_USERNAME, "登录账号错误", loginParam.getUserName());
        }

        LoginUser loginUser = userLoginMapper.userInfo(loginParam);
        if (loginUser == null) {
            return new ReturnResult(LOGIN_FAIL_USERPWD, "登录密码错误", loginParam.getUserName());
        }

        String token = jwt.sign(loginParam.getUserName(), loginParam.getUserPwd());
        redisClient.setCacheObject(REDISNAME + Aes.encrypt(loginParam.getUserName()), loginUser, 24, TimeUnit.HOURS);
        httpServletRequest.setAttribute("token", token);
        return new ReturnResult(SUCCESS, "登录成功", new LoginSuccess(token, loginUser));
    }
}
