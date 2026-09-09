package com.aop.LoginToken;

import com.common.Jwt.jwt;
import com.common.Util.UserInfo.RedisUserInfo;
import com.common.Util.UserInfo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

import static com.common.Constant.constant.*;

/**
 * 登录拦截实现类
 */

public class UserLoginTokenAspect implements HandlerInterceptor {

    @Autowired(required = false)
    private RedisUserInfo redisUserInfo;


    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object object) {
        // 从 http 请求头中取出 token
        String token = httpServletRequest.getHeader("token");
        // 如果不是映射到方法直接通过
        if(!(object instanceof HandlerMethod)){
            return true;
        }
        HandlerMethod handlerMethod=(HandlerMethod)object;
        Method method=handlerMethod.getMethod();
        //检查有没有需要用户权限的注解
        if (method.isAnnotationPresent(UserLoginToken.class)) {
            UserLoginToken jwtToken = method.getAnnotation(UserLoginToken.class);
            if (jwtToken.required()) {
                // 执行认证
                if (token==null ) {
                    throw  new RuntimeException(NO_TOKEN);
                }
                UserInfo user = null;
                //获取redis的值
                try {
                    user= redisUserInfo.userInfo();
                }catch (Exception e){
                    throw  new RuntimeException(TOKEN_OVERDUE);
                }
                if(user==null){
                    throw  new RuntimeException(TOKEN_OVERDUE);
                }else{
                    boolean overdue= jwt.verify(token,user.getUserName(),user.getUserPwd());
                    // 验证 token
                    if(!overdue){
                        throw  new RuntimeException(TOKEN_OVERDUE);
                    }
                }
            }
        }
        return true;
    }
    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
    }
    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {
    }
}
