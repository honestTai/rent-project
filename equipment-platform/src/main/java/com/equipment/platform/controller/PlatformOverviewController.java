package com.equipment.platform.controller;

import com.common.Entity.ReturnResult;
import com.common.Util.UserInfo.UserContextHeaders;
import com.equipment.platform.rbac.RbacCurrentPermissionView;
import com.equipment.platform.rbac.RbacService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台总览接口。
 * <p>
 * 提供产品保留的业务系统入口和可聚合接口地址，前端可按当前 token 直接访问网关下游接口。
 * </p>
 */
@RestController
@RequestMapping("/api/platform/overview")
public class PlatformOverviewController {

    private static final String SUPER_ADMIN_ROLE_CODE = "super_admin";
    private static final List<String> SUMMARY_PAGE_CODES = Arrays.asList("analysis", "report");
    private static final List<SystemOverviewMeta> SYSTEMS = Collections.singletonList(
            new SystemOverviewMeta("alipay", "支付宝租赁系统", "/rent/", "/api/web/analytics/dashboard")
    );

    private final RbacService rbacService;

    /**
     * 创建中台总览接口。
     *
     * @param rbacService RBAC 服务，用于按当前登录用户权限过滤业务系统入口
     */
    public PlatformOverviewController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * 查询当前用户可访问的业务系统入口。
     * <p>
     * 超级管理员返回保留的业务系统且允许加载汇总；其他用户只返回自己有页面权限的系统；
     * 普通业务用户如果没有分析或报表权限，只返回入口，不返回 dashboardApi，前端不会拉取汇总数据。
     * </p>
     *
     * @return 系统入口列表
     */
    @GetMapping("/systems")
    public ReturnResult<List<Map<String, Object>>> systems(HttpServletRequest request) {
        RbacCurrentPermissionView permissionView = rbacService.currentPermissions(
                readIntegerHeader(request, UserContextHeaders.USER_ID));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemOverviewMeta meta : SYSTEMS) {
            if (!canEnterSystem(meta.getCode(), permissionView)) {
                continue;
            }
            result.add(system(meta, canViewSummary(meta.getCode(), permissionView), permissionView));
        }
        return new ReturnResult<>(SUCCESS, "查询成功", result);
    }

    /**
     * 构建业务系统入口返回结构，并按权限决定是否暴露汇总接口地址。
     *
     * @param meta 系统基础信息
     * @param summaryVisible 当前用户是否可查看该系统分析汇总
     * @param permissionView 当前用户权限快照
     * @return 前端可渲染的系统入口数据
     */
    private Map<String, Object> system(SystemOverviewMeta meta,
                                       boolean summaryVisible,
                                       RbacCurrentPermissionView permissionView) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", meta.getCode());
        item.put("name", meta.getName());
        item.put("path", meta.getPath());
        item.put("dashboardApi", summaryVisible ? meta.getDashboardApi() : null);
        item.put("summaryVisible", summaryVisible);
        item.put("accessMode", summaryVisible ? "summary" : "entry");
        item.put("permissionSummary", buildPermissionSummary(meta.getCode(), summaryVisible, permissionView));
        return item;
    }

    /**
     * 判断用户是否拥有系统入口权限；只要有该系统任意页面权限，就允许在中台首页出现入口。
     *
     * @param systemCode 系统编码
     * @param permissionView 当前用户权限快照
     * @return true 表示可显示该系统入口
     */
    private boolean canEnterSystem(String systemCode, RbacCurrentPermissionView permissionView) {
        if (isSuperAdmin(permissionView)) {
            return true;
        }
        for (String pageCode : safeList(permissionView == null ? null : permissionView.getPageCodes())) {
            if (pageCode != null && pageCode.startsWith(systemCode + ":")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断用户是否可以查看系统分析汇总；只有分析或报表页面授权用户才加载指标。
     *
     * @param systemCode 系统编码
     * @param permissionView 当前用户权限快照
     * @return true 表示可查看该系统分析汇总
     */
    private boolean canViewSummary(String systemCode, RbacCurrentPermissionView permissionView) {
        if (isSuperAdmin(permissionView) || hasSystemAdminRole(systemCode, permissionView)) {
            return true;
        }
        for (String pageCode : SUMMARY_PAGE_CODES) {
            if (hasPage(systemCode, pageCode, permissionView)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为超级管理员。
     *
     * @param permissionView 当前用户权限快照
     * @return true 表示拥有全系统权限
     */
    private boolean isSuperAdmin(RbacCurrentPermissionView permissionView) {
        return permissionView != null
                && safeList(permissionView.getRoleCodes()).contains(SUPER_ADMIN_ROLE_CODE);
    }

    /**
     * 兼容系统管理员角色编码，便于无需改接口即可支持 alipay_admin 等角色。
     *
     * @param systemCode 系统编码
     * @param permissionView 当前用户权限快照
     * @return true 表示该用户是当前系统管理员
     */
    private boolean hasSystemAdminRole(String systemCode, RbacCurrentPermissionView permissionView) {
        for (String roleCode : safeList(permissionView == null ? null : permissionView.getRoleCodes())) {
            if ((systemCode + "_admin").equals(roleCode) || (systemCode + "_manager").equals(roleCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断权限快照是否包含指定页面。
     *
     * @param systemCode 系统编码
     * @param pageCode 页面编码
     * @param permissionView 当前用户权限快照
     * @return true 表示拥有页面权限
     */
    private boolean hasPage(String systemCode, String pageCode, RbacCurrentPermissionView permissionView) {
        return safeList(permissionView == null ? null : permissionView.getPageCodes())
                .contains(systemCode + ":" + pageCode);
    }

    /**
     * 生成前端权限说明，帮助用户理解为什么某系统只展示入口或可展示汇总。
     *
     * @param systemCode 系统编码
     * @param summaryVisible 是否可查看汇总
     * @param permissionView 当前用户权限快照
     * @return 权限说明文案
     */
    private String buildPermissionSummary(String systemCode,
                                          boolean summaryVisible,
                                          RbacCurrentPermissionView permissionView) {
        if (summaryVisible) {
            return "已授权查看分析汇总";
        }
        int count = 0;
        for (String pageCode : safeList(permissionView == null ? null : permissionView.getPageCodes())) {
            if (pageCode != null && pageCode.startsWith(systemCode + ":")) {
                count++;
            }
        }
        return count > 0 ? "仅授权进入业务系统，未授权查看分析汇总" : "暂无授权页面";
    }

    /**
     * 从网关透传请求头中读取整数用户上下文，缺失时返回 null。
     *
     * @param request 当前请求
     * @param headerName 请求头名称
     * @return 请求头整数值
     */
    private Integer readIntegerHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 返回安全列表，减少权限字段为空时的重复判空。
     *
     * @param values 原始列表
     * @return 非空列表
     */
    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * 中台首页业务系统元数据。
     */
    private static class SystemOverviewMeta {
        private final String code;
        private final String name;
        private final String path;
        private final String dashboardApi;

        /**
         * 创建业务系统元数据。
         *
         * @param code 系统编码
         * @param name 系统名称
         * @param path 前端入口路径
         * @param dashboardApi 可查看汇总时调用的分析接口
         */
        SystemOverviewMeta(String code, String name, String path, String dashboardApi) {
            this.code = code;
            this.name = name;
            this.path = path;
            this.dashboardApi = dashboardApi;
        }

        String getCode() {
            return code;
        }

        String getName() {
            return name;
        }

        String getPath() {
            return path;
        }

        String getDashboardApi() {
            return dashboardApi;
        }
    }
}
