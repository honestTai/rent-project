package com.equipment.gateway.service;

import com.common.Encryption.Aes;
import com.common.Encryption.Md5;
import com.common.Entity.ReturnResult;
import com.common.Jwt.jwt;
import com.common.login.LoginParam;
import com.common.login.LoginSuccess;
import com.common.login.LoginUser;
import eu.bitwalker.useragentutils.UserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import static com.common.Constant.constant.LOGIN_FAIL_USERNAME;
import static com.common.Constant.constant.LOGIN_FAIL_USERPWD;
import static com.common.Constant.constant.REDISNAME;
import static com.common.Constant.constant.SUCCESS;

/**
 * 网关登录服务，负责账号校验、登录限流、登录日志和权限快照写入。
 */
@Service
public class GatewayLoginService {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoginService.class);

    private static final String COUNT_USER_SQL = "select count(*) from user where userName = ?";
    private static final String QUERY_USER_SQL = "select id, realName, userName, userPwd, userPhoneNum, userSex, creatUserDate from user where userName = ? and userPwd = ?";
    private static final String INSERT_LOG_SQL = "insert into `log`(dateTime,content,userId,`type`,userIp,requestMethod,`result`,logMsg,userAddress,userBrowser,userSystem,requestUrl,froms,resultData) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String LOG_CONTENT = "PC登录";
    private static final String LOG_FROM_RENT = "无人机租赁系统";
    private static final String LOG_FROM_SECOND = "二手无人机购买系统";
    private static final String LOG_FROM_PLATFORM = "中台管理系统";
    private static final String LOG_REQUEST_URL = "/api/login/login";
    private static final String LOG_METHOD = "GatewayLoginService.login";
    private static final int LOG_TYPE_PC = 1;
    private static final Duration LOGIN_TTL = Duration.ofHours(24);
    private static final GenericJackson2JsonRedisSerializer REDIS_SERIALIZER = new GenericJackson2JsonRedisSerializer();

    private static final String LOGIN_FAIL_COUNT_PREFIX = "login:fail:count:";
    private static final String LOGIN_LOCKED_PREFIX = "login:locked:";
    private static final int MAX_FAIL_COUNT = 3;
    private static final Duration FAIL_COUNT_WINDOW = Duration.ofMinutes(1);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    public static final int IP_LOCKED_CODE = 423;

    private static final RowMapper<LoginUser> LOGIN_USER_ROW_MAPPER = (rs, rowNum) -> {
        LoginUser user = new LoginUser();
        user.setId(rs.getInt("id"));
        user.setRealName(rs.getString("realName"));
        user.setUserName(rs.getString("userName"));
        user.setUserPwd(rs.getString("userPwd"));
        user.setUserPhoneNum(rs.getString("userPhoneNum"));
        user.setUserSex((Integer) rs.getObject("userSex"));
        Timestamp createUserDate = rs.getTimestamp("creatUserDate");
        if (createUserDate != null) {
            user.setCreatUserDate(new Date(createUserDate.getTime()));
        }
        user.setUuid("-1");
        user.setIsNoRequest(0);
        return user;
    };

    private final JdbcTemplate jdbcTemplate;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayRbacPermissionService rbacPermissionService;

    public GatewayLoginService(JdbcTemplate jdbcTemplate,
                               ReactiveStringRedisTemplate redisTemplate,
                               GatewayRbacPermissionService rbacPermissionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.rbacPermissionService = rbacPermissionService;
    }

    /**
     * 执行 PC 端登录，成功后返回 token 和带权限快照的用户信息。
     */
    public Mono<ReturnResult<LoginSuccess>> login(LoginParam rawLoginParam, String clientIp, String userAgent, String loginSystem) {
        String froms = resolveLogFrom(loginSystem);
        String lockedKey = LOGIN_LOCKED_PREFIX + clientIp;
        return redisTemplate.hasKey(lockedKey)
                .flatMap(locked -> {
                    if (Boolean.TRUE.equals(locked)) {
                        return redisTemplate.getExpire(lockedKey)
                                .map(ttl -> {
                                    long minutes = ttl.toMinutes();
                                    String msg = "登录失败次数过多，请" + (minutes > 0 ? minutes + "分钟" : "稍") + "后再试";
                                    ReturnResult<LoginSuccess> r = new ReturnResult<LoginSuccess>(IP_LOCKED_CODE, msg, null);
                                    writeLoginLogAsync(r, clientIp, userAgent, froms);
                                    return r;
                                });
                    }
                    return Mono.fromCallable(() -> doLogin(rawLoginParam, clientIp))
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnNext(result -> writeLoginLogAsync(result, clientIp, userAgent, froms));
                });
    }

    private static String resolveLogFrom(String loginSystem) {
        if (StringUtils.hasText(loginSystem) && "platform".equalsIgnoreCase(loginSystem.trim())) {
            return LOG_FROM_PLATFORM;
        }
        if (StringUtils.hasText(loginSystem) && "second".equalsIgnoreCase(loginSystem.trim())) {
            return LOG_FROM_SECOND;
        }
        return LOG_FROM_PLATFORM;
    }

    private ReturnResult<LoginSuccess> doLogin(LoginParam rawLoginParam, String clientIp) throws Exception {
        if (rawLoginParam == null || !StringUtils.hasText(rawLoginParam.getUserName())) {
            recordFail(clientIp);
            return new ReturnResult<>(LOGIN_FAIL_USERNAME, "登录账号错误", null);
        }
        if (!StringUtils.hasText(rawLoginParam.getUserPwd())) {
            recordFail(clientIp);
            return new ReturnResult<>(LOGIN_FAIL_USERPWD, "登录密码错误", null);
        }

        String encryptedPwd = Aes.encrypt(Md5.md5String(rawLoginParam.getUserPwd()));
        LoginParam loginParam = new LoginParam(rawLoginParam.getUserName().trim(), encryptedPwd);

        Integer userCount = jdbcTemplate.queryForObject(COUNT_USER_SQL, Integer.class, loginParam.getUserName());
        if (userCount == null || userCount == 0) {
            recordFail(clientIp);
            return new ReturnResult<>(LOGIN_FAIL_USERNAME, "登录账号错误", null);
        }

        List<LoginUser> users = jdbcTemplate.query(
                QUERY_USER_SQL,
                LOGIN_USER_ROW_MAPPER,
                loginParam.getUserName(),
                loginParam.getUserPwd()
        );
        if (users.isEmpty()) {
            recordFail(clientIp);
            return new ReturnResult<>(LOGIN_FAIL_USERPWD, "登录密码错误", null);
        }

        clearFail(clientIp);

        LoginUser loginUser = users.get(0);
        rbacPermissionService.fillPermissionSnapshot(loginUser);
        String token = jwt.sign(loginParam.getUserName(), loginParam.getUserPwd());
        String redisKey = REDISNAME + Aes.encrypt(loginParam.getUserName());
        String userJson = new String(REDIS_SERIALIZER.serialize(loginUser), StandardCharsets.UTF_8);
        Boolean saved = redisTemplate.opsForValue().set(redisKey, userJson, LOGIN_TTL).block();
        if (!Boolean.TRUE.equals(saved)) {
            throw new IllegalStateException("failed to cache login session");
        }

        return new ReturnResult<>(SUCCESS, "登录成功", new LoginSuccess(token, loginUser));
    }

    private void recordFail(String clientIp) {
        String countKey = LOGIN_FAIL_COUNT_PREFIX + clientIp;
        String lockedKey = LOGIN_LOCKED_PREFIX + clientIp;

        Long count = redisTemplate.opsForValue().increment(countKey).block();
        if (count != null && count == 1) {
            redisTemplate.expire(countKey, FAIL_COUNT_WINDOW).block();
        }
        if (count != null && count >= MAX_FAIL_COUNT) {
            redisTemplate.opsForValue().set(lockedKey, String.valueOf(System.currentTimeMillis()), LOCK_DURATION).block();
            redisTemplate.delete(countKey).block();
        }
    }

    private void clearFail(String clientIp) {
        String countKey = LOGIN_FAIL_COUNT_PREFIX + clientIp;
        redisTemplate.delete(countKey).block();
    }

    /**
     * 异步写入登录日志到 log 表，不阻塞登录响应。froms 区分租赁/二手系统。
     */
    private void writeLoginLogAsync(ReturnResult<LoginSuccess> result, String clientIp, String userAgent, String froms) {
        Mono.fromRunnable(() -> {
            try {
                Integer userId = null;
                if (result.getData() != null && result.getData().getUser() != null) {
                    userId = result.getData().getUser().getId();
                }
                String resultStr = SUCCESS.equals(result.getCode()) ? "成功" : "失败";
                String logMsg = result.getMsg() != null ? result.getMsg() : "";
                String browser = null;
                String system = null;
                if (StringUtils.hasText(userAgent)) {
                    try {
                        UserAgent ua = UserAgent.parseUserAgentString(userAgent);
                        browser = ua.getBrowser().getName();
                        system = ua.getOperatingSystem().getName();
                    } catch (Exception ignored) {
                    }
                }
                jdbcTemplate.update(INSERT_LOG_SQL,
                        new Timestamp(System.currentTimeMillis()),
                        LOG_CONTENT,
                        userId,
                        LOG_TYPE_PC,
                        clientIp != null ? clientIp : "",
                        LOG_METHOD,
                        resultStr,
                        logMsg,
                        clientIp != null ? clientIp : null,
                        browser,
                        system,
                        LOG_REQUEST_URL,
                        froms != null ? froms : LOG_FROM_RENT,
                        null);
            } catch (Exception e) {
                log.warn("写入登录日志失败: clientIp={}", clientIp, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }
}
