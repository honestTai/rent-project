package com.common.Jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.common.Constant.constant;

import java.util.Date;

/**
 * 无状态token
 */
public class jwt {

    /**
     * 校验token
     *
     * @param token
     * @param userName
     * @param userPwd
     * @return
     */
    public static boolean verify(String token, String userName, String userPwd) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(userPwd);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaim("userName",userName)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 获得token中的信息无需secret解密也能获得
     *
     * @return token中包含的用户名
     */
    public static String getUsername(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            String userName= jwt.getClaim("userName").asString();
            return userName;
        } catch (JWTDecodeException e) {
            return null;
        }
    }

    /**
     * 生成签名,5min后过期
     *
     * @param userName 用户登录账号
     * @param userPwd  用户的密码
     * @return 加密的token
     */
    public static String sign(String userName, String userPwd) {
        try {
            Date date = new Date(System.currentTimeMillis() + constant.OVERDUE_TIME);
            Algorithm algorithm = Algorithm.HMAC256(userPwd);
            // 附带username信息
            return JWT.create()
                    .withClaim("userName",userName)
                    .withExpiresAt(date)
                    .sign(algorithm);
        } catch (Exception e) {
            return null;
        }
    }
}
