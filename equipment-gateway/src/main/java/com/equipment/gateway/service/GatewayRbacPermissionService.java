package com.equipment.gateway.service;

import com.common.login.LoginUser;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关 RBAC 权限服务，负责登录权限快照和接口访问判断。
 */
@Service
public class GatewayRbacPermissionService {

    private static final String CURRENT_PERMISSION_PATH = "/api/platform/rbac/current";
    private static final String PLATFORM_OVERVIEW_SYSTEMS_PATH = "/api/platform/overview/systems";
    /** 新增的自有目录后台属于强制 RBAC 区域；漏配具体规则时必须拒绝，不能沿用历史放行策略。 */
    private static final String CATALOG_ADMIN_PATH = "/api/web/catalog/**";

    private static final String QUERY_USER_ROLE_CODES_SQL =
            "select distinct r.role_code from platform_user_role ur " +
                    "join platform_role r on r.id = ur.role_id and r.enabled = 1 " +
                    "where ur.user_id = ?";
    private static final String QUERY_USER_PAGE_CODES_SQL =
            "select distinct concat(b.system_code, ':', b.page_code) from platform_user_role ur " +
                    "join platform_role r on r.id = ur.role_id and r.enabled = 1 " +
                    "join platform_role_permission rp on rp.role_id = r.id " +
                    "join platform_rbac_button b on b.id = rp.button_id and b.enabled = 1 " +
                    "where ur.user_id = ?";
    private static final String QUERY_USER_BUTTON_CODES_SQL =
            "select distinct concat(b.system_code, ':', b.page_code, ':', b.button_code) from platform_user_role ur " +
                    "join platform_role r on r.id = ur.role_id and r.enabled = 1 " +
                    "join platform_role_permission rp on rp.role_id = r.id " +
                    "join platform_rbac_button b on b.id = rp.button_id and b.enabled = 1 " +
                    "where ur.user_id = ?";
    private static final String QUERY_ENABLED_API_RULES_SQL =
            "select api_method, api_path from platform_rbac_button " +
                    "where enabled = 1 and api_path is not null and api_path <> '' " +
                    "union all " +
                    "select ar.api_method, ar.api_path from platform_rbac_api_rule ar " +
                    "join platform_rbac_button b on b.id = ar.button_id and b.enabled = 1 " +
                    "where ar.enabled = 1 and ar.api_path is not null and ar.api_path <> ''";
    private static final String QUERY_USER_API_RULES_SQL =
            "select distinct b.api_method, b.api_path from platform_user_role ur " +
                    "join platform_role r on r.id = ur.role_id and r.enabled = 1 " +
                    "join platform_role_permission rp on rp.role_id = r.id " +
                    "join platform_rbac_button b on b.id = rp.button_id and b.enabled = 1 " +
                    "where ur.user_id = ? and b.api_path is not null and b.api_path <> '' " +
                    "union all " +
                    "select distinct ar.api_method, ar.api_path from platform_user_role ur " +
                    "join platform_role r on r.id = ur.role_id and r.enabled = 1 " +
                    "join platform_role_permission rp on rp.role_id = r.id " +
                    "join platform_rbac_button b on b.id = rp.button_id and b.enabled = 1 " +
                    "join platform_rbac_api_rule ar on ar.button_id = b.id and ar.enabled = 1 " +
                    "where ur.user_id = ? and ar.api_path is not null and ar.api_path <> ''";

    private final JdbcTemplate jdbcTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayRbacPermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 登录成功时生成权限快照，前端菜单和按钮显隐直接读取该快照。
     */
    public void fillPermissionSnapshot(LoginUser user) {
        if (user == null) {
            return;
        }
        if (user.getId() == null) {
            user.setRoleCodes(new ArrayList<>());
            user.setPageCodes(new ArrayList<>());
            user.setButtonCodes(new ArrayList<>());
            return;
        }
        user.setRoleCodes(queryStringList(QUERY_USER_ROLE_CODES_SQL, user.getId()));
        user.setPageCodes(queryStringList(QUERY_USER_PAGE_CODES_SQL, user.getId()));
        user.setButtonCodes(queryStringList(QUERY_USER_BUTTON_CODES_SQL, user.getId()));
    }

    /**
     * 判断当前请求是否拥有对应 RBAC 接口权限。
     */
    public boolean hasApiPermission(Integer userId, HttpMethod method, String path) {
        if (!StringUtils.hasText(path) || isAuthenticatedSharedPath(path)) {
            return true;
        }
        List<ApiRule> enabledRules = queryApiRules(QUERY_ENABLED_API_RULES_SQL);
        boolean managedByRbac = pathMatcher.match(CATALOG_ADMIN_PATH, path)
                || matchesAny(enabledRules, method, path);
        if (!managedByRbac) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        List<ApiRule> userRules = queryApiRules(QUERY_USER_API_RULES_SQL, userId, userId);
        return matchesAny(userRules, method, path);
    }

    /**
     * 判断登录后所有用户都可访问的共享接口。
     * <p>
     * 这些接口自身会按用户权限裁剪返回结果，网关只负责确认已经登录，不再要求用户拥有中台页面权限。
     * </p>
     */
    private boolean isAuthenticatedSharedPath(String path) {
        return CURRENT_PERMISSION_PATH.equals(path) || PLATFORM_OVERVIEW_SYSTEMS_PATH.equals(path);
    }

    private List<String> queryStringList(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), args);
    }

    private List<ApiRule> queryApiRules(String sql, Object... args) {
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ApiRule(rs.getString("api_method"), rs.getString("api_path")),
                args);
    }

    private boolean matchesAny(List<ApiRule> rules, HttpMethod method, String path) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (ApiRule rule : rules) {
            if (matchesRule(rule, method, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(ApiRule rule, HttpMethod method, String path) {
        if (rule == null || !StringUtils.hasText(rule.getApiPath())) {
            return false;
        }
        String apiMethod = rule.getApiMethod();
        if (StringUtils.hasText(apiMethod) && !"*".equals(apiMethod.trim())
                && (method == null || !apiMethod.trim().equalsIgnoreCase(method.name()))) {
            return false;
        }
        return pathMatcher.match(rule.getApiPath().trim(), path);
    }

    /**
     * RBAC 接口规则。
     */
    private static class ApiRule {
        private final String apiMethod;
        private final String apiPath;

        ApiRule(String apiMethod, String apiPath) {
            this.apiMethod = apiMethod;
            this.apiPath = apiPath;
        }

        String getApiMethod() {
            return apiMethod;
        }

        String getApiPath() {
            return apiPath;
        }
    }
}
