package com.equipment.gateway.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.common.Encryption.Aes;
import com.common.Util.UserInfo.UserContextHeaders;
import com.equipment.gateway.config.GatewayAuthProperties;
import com.equipment.gateway.service.GatewayRbacPermissionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关统一鉴权过滤器，负责 token 校验、用户上下文透传和 RBAC 接口权限判断。
 */
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String TOKEN_HEADER = "token";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REDIS_PREFIX = "voteRedis";

    private static final Integer FORBIDDEN = 403;
    private static final Integer LOGIN_OVERDUE = 401;

    private static final String NO_TOKEN = "无token，请登录";
    private static final String TOKEN_OVERDUE = "token过期，请重新登录";
    private static final String NO_ROLE = "无权操作";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayAuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final GatewayRbacPermissionService rbacPermissionService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthFilter(ReactiveStringRedisTemplate redisTemplate,
                             GatewayAuthProperties authProperties,
                             ObjectMapper objectMapper,
                             GatewayRbacPermissionService rbacPermissionService) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
        this.rbacPermissionService = rbacPermissionService;
    }

    private static final String LOGIN_LOCKED_PREFIX = "login:locked:";
    private static final Integer IP_LOCKED_CODE = 423;

    /**
     * 过滤所有 /api 请求，登录接口只检查锁定状态，其余接口校验登录和权限。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.OPTIONS.equals(method) || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        if (isLoginPath(path)) {
            String clientIp = resolveClientIp(exchange);
            String lockedKey = LOGIN_LOCKED_PREFIX + clientIp;
            return redisTemplate.hasKey(lockedKey)
                    .flatMap(locked -> {
                        if (Boolean.TRUE.equals(locked)) {
                            return redisTemplate.getExpire(lockedKey)
                                    .flatMap(ttl -> {
                                        long minutes = ttl.toMinutes();
                                        String msg = "登录失败次数过多，请" + (minutes > 0 ? minutes + "分钟" : "稍") + "后再试";
                                        return writeResult(exchange, IP_LOCKED_CODE, msg);
                                    });
                        }
                        return chain.filter(exchange);
                    });
        }

        if (matches(authProperties.getExcludePaths(), method, path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange);
        if (!StringUtils.hasText(token)) {
            return writeResult(exchange, LOGIN_OVERDUE, NO_TOKEN);
        }

        String userName = getUsername(token);
        if (!StringUtils.hasText(userName)) {
            return writeResult(exchange, LOGIN_OVERDUE, TOKEN_OVERDUE);
        }

        String redisKey = REDIS_PREFIX + Aes.encrypt(userName);
        return redisTemplate.opsForValue()
                .get(redisKey)
                .flatMap(userJson -> verifyAndForward(exchange, chain, method, path, token, userJson))
                .switchIfEmpty(Mono.defer(() -> writeResult(exchange, LOGIN_OVERDUE, TOKEN_OVERDUE)));
    }

    private Mono<Void> verifyAndForward(ServerWebExchange exchange,
                                        GatewayFilterChain chain,
                                        HttpMethod method,
                                        String path,
                                        String token,
                                        String userJson) {
        GatewayUser user = parseUser(userJson);
        if (user == null || !StringUtils.hasText(user.getUserName()) || !StringUtils.hasText(user.getUserPwd())) {
            return writeResult(exchange, LOGIN_OVERDUE, TOKEN_OVERDUE);
        }
        if (!verify(token, user.getUserName(), user.getUserPwd())) {
            return writeResult(exchange, LOGIN_OVERDUE, TOKEN_OVERDUE);
        }
        if (matches(authProperties.getUuidAdminPaths(), method, path) && !isUuidAdminUser(user)) {
            return writeResult(exchange, FORBIDDEN, NO_ROLE);
        }
        if (matches(authProperties.getAdminPaths(), method, path) && !isAdminUser(user)) {
            return writeResult(exchange, FORBIDDEN, NO_ROLE);
        }
        return Mono.fromCallable(() -> rbacPermissionService.hasApiPermission(user.getId(), method, path))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(allowed -> {
                    if (!Boolean.TRUE.equals(allowed)) {
                        return writeResult(exchange, FORBIDDEN, NO_ROLE);
                    }
                    return chain.filter(forwardUserContext(exchange, token, user));
                });
    }

    private String resolveToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(TOKEN_HEADER);
        if (StringUtils.hasText(token)) {
            return token;
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private boolean matches(List<String> rules, HttpMethod method, String path) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            if (matchesRule(rule, method, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(String rule, HttpMethod method, String path) {
        if (!StringUtils.hasText(rule)) {
            return false;
        }
        String trimmedRule = rule.trim();
        String requestMethod = "*";
        String pattern = trimmedRule;
        int index = trimmedRule.indexOf(":/");
        if (index > 0) {
            requestMethod = trimmedRule.substring(0, index);
            pattern = trimmedRule.substring(index + 1);
        }
        if (!"*".equals(requestMethod) && (method == null || !requestMethod.equalsIgnoreCase(method.name()))) {
            return false;
        }
        return pathMatcher.match(pattern, path);
    }

    private Mono<Void> writeResult(ServerWebExchange exchange, Integer code, String msg) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", null);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        try {
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        } catch (UnsupportedOperationException ignored) {
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(bytes)));
    }

    private GatewayUser parseUser(String userJson) {
        try {
            JsonNode root = objectMapper.readTree(userJson);
            JsonNode dataNode = root;
            if (root.isArray() && root.size() > 1) {
                dataNode = root.get(1);
            }

            GatewayUser user = new GatewayUser();
            user.setId(readInteger(dataNode, "id"));
            user.setUserName(firstNonBlank(
                    readText(dataNode, "userName"),
                    readText(dataNode, "uAcco"),
                    readText(dataNode, "account")
            ));
            user.setUserPwd(firstNonBlank(
                    readText(dataNode, "userPwd"),
                    readText(dataNode, "uPass"),
                    readText(dataNode, "password")
            ));
            user.setUuid(readText(dataNode, "uuid"));
            return user;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAdminUser(GatewayUser user) {
        return "-1".equals(user.getUuid());
    }

    private boolean isUuidAdminUser(GatewayUser user) {
        return "-1".equals(user.getUuid());
    }

    private ServerWebExchange forwardUserContext(ServerWebExchange exchange, String token, GatewayUser user) {
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(UserContextHeaders.USER_ID);
                    headers.remove(UserContextHeaders.USER_NAME);
                    headers.remove(UserContextHeaders.USER_PWD);
                    headers.remove(UserContextHeaders.USER_UUID);

                    headers.set(TOKEN_HEADER, token);
                    setHeader(headers, UserContextHeaders.USER_ID, user.getId());
                    setHeader(headers, UserContextHeaders.USER_NAME, user.getUserName());
                    setHeader(headers, UserContextHeaders.USER_PWD, user.getUserPwd());
                    setHeader(headers, UserContextHeaders.USER_UUID, user.getUuid());
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private void setHeader(HttpHeaders headers, String name, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            headers.set(name, String.valueOf(value));
        }
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    private Integer readInteger(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return node.get(fieldName).asInt();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean verify(String token, String userName, String userPwd) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(userPwd);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaim("userName", userName)
                    .build();
            verifier.verify(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private String getUsername(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getClaim("userName").asString();
        } catch (JWTDecodeException e) {
            return null;
        }
    }

    private boolean isLoginPath(String path) {
        return "/api/login/login".equals(path);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private static class GatewayUser {
        private Integer id;
        private String userName;
        private String userPwd;
        private String uuid;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getUserPwd() {
            return userPwd;
        }

        public void setUserPwd(String userPwd) {
            this.userPwd = userPwd;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }
    }
}
