package com.equipment.platform.service;

import com.common.Util.UserInfo.UserContextHeaders;
import com.equipment.platform.rbac.RbacCurrentPermissionView;
import com.equipment.platform.rbac.RbacService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 平台跨系统运行数据访问范围控制。
 */
@Service
public class SystemScopePermissionService {

    private static final String PLATFORM_SYSTEM_CODE = "platform";
    private static final String SUPER_ADMIN_ROLE_CODE = "super_admin";

    private final RbacService rbacService;

    public SystemScopePermissionService(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * 判断当前用户是否拥有平台级全局访问权限。
     *
     * @param request 当前请求
     * @param pageCode 平台页面编码
     * @return true 表示可查看全部系统
     */
    public boolean hasGlobalAccess(HttpServletRequest request, String pageCode) {
        RbacCurrentPermissionView view = currentPermissions(request);
        return isSuperAdmin(view) || hasPage(view, PLATFORM_SYSTEM_CODE, pageCode);
    }

    /**
     * 返回当前用户可查看的系统编码集合。
     *
     * @param request 当前请求
     * @param pageCode 页面编码，例如 monitor 或 systemLog
     * @param supportedSystemCodes 当前接口支持的系统集合
     * @return 可查看系统编码集合
     */
    public Set<String> allowedSystemCodes(HttpServletRequest request,
                                          String pageCode,
                                          Collection<String> supportedSystemCodes) {
        Set<String> supported = normalizeSet(supportedSystemCodes);
        if (supported.isEmpty()) {
            throw new IllegalArgumentException("当前接口未配置可查看系统");
        }
        RbacCurrentPermissionView view = currentPermissions(request);
        if (isSuperAdmin(view) || hasPage(view, PLATFORM_SYSTEM_CODE, pageCode)) {
            return supported;
        }

        Set<String> allowed = new LinkedHashSet<>();
        for (String systemCode : supported) {
            if (hasPage(view, systemCode, pageCode)) {
                allowed.add(systemCode);
            }
        }
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("当前用户没有查看该运行数据的权限");
        }
        return allowed;
    }

    /**
     * 解析并校验请求中的系统编码。
     *
     * @param request 当前请求
     * @param requestedSystemCode 请求系统编码
     * @param pageCode 页面编码
     * @param supportedSystemCodes 支持的系统集合
     * @param defaultSystemCode 默认系统编码
     * @return 已授权系统编码
     */
    public String resolveAllowedSystemCode(HttpServletRequest request,
                                           String requestedSystemCode,
                                           String pageCode,
                                           Collection<String> supportedSystemCodes,
                                           String defaultSystemCode) {
        Set<String> supported = normalizeSet(supportedSystemCodes);
        String normalized = normalizeCode(requestedSystemCode);
        if (!StringUtils.hasText(normalized)) {
            normalized = normalizeCode(defaultSystemCode);
        }
        if (!supported.contains(normalized)) {
            throw new IllegalArgumentException("不支持的系统编码: " + requestedSystemCode);
        }

        Set<String> allowed = allowedSystemCodes(request, pageCode, supported);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("当前用户无权查看该系统运行数据: " + normalized);
        }
        return normalized;
    }

    private RbacCurrentPermissionView currentPermissions(HttpServletRequest request) {
        return rbacService.currentPermissions(readIntegerHeader(request, UserContextHeaders.USER_ID));
    }

    private boolean isSuperAdmin(RbacCurrentPermissionView view) {
        return view != null && view.getRoleCodes() != null && view.getRoleCodes().contains(SUPER_ADMIN_ROLE_CODE);
    }

    private boolean hasPage(RbacCurrentPermissionView view, String systemCode, String pageCode) {
        return view != null
                && view.getPageCodes() != null
                && view.getPageCodes().contains(systemCode + ":" + pageCode);
    }

    private Set<String> normalizeSet(Collection<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = normalizeCode(value);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private Integer readIntegerHeader(HttpServletRequest request, String header) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(header);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
