package com.common.Constant;

/**
 * 常量类
 */
public class constant {
    //jwt过期时间,24小时
    public static final long OVERDUE_TIME =1440 * 60 * 1000 ;

    //成功调用
    public static final String RIGHT_LOG="成功";

    //失败调用
    public static final String ERR_LOG="失败";

    //登录失效/token过期
    public static final Integer LOGIN_OVERDUE=401;

    //请求接口成功
    public static final Integer SUCCESS=0;

    //请求接口失败
    public static final Integer FAIL=500;

    //无权限
    public static final Integer FORBIDDEN=403;

    //登录失败
    public static final Integer LOGIN_FAIL_USERNAME=700;

    //登录失败
    public static final Integer LOGIN_FAIL_USERPWD=701;

    //AES验证密码
    public static final String AES_SORT = resolveAesSecret();

    private static String resolveAesSecret() {
        String secret = System.getenv("RENTAL_PRODUCT_AES_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getProperty("rental.product.aes-secret");
        }
        return secret;
    }

    //redis前缀
    public static final String REDISNAME="voteRedis";

    //没有token
    public static final String NO_TOKEN="无token，请登录";

    //token过期
    public static final String TOKEN_OVERDUE="token过期，请重新登录";

    public static final String FILE_PATH="/data/equipment/static/";

    //权限不足
    public static final String NO_ROLE="无权操纵";

    //修改密码
    public static final Integer UPDATE_PWD=513;

    //文件存放路径常量
    public static String mImagesPath="file:/data/equipment/static/";

    //db备份
    public static final String dbFilePath=FILE_PATH+"db/";

}
