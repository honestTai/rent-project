package com.equipment.platform.monitor;

import com.common.Entity.ReturnResult;
import com.equipment.platform.service.SystemScopePermissionService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台系统监控接口。
 * <p>
 * 监控数据只聚合运行态健康度和机器资源，不侵入各业务系统内部业务表。
 * </p>
 */
@RestController
@RequestMapping("/api/platform/monitor")
public class PlatformMonitorController {

    private final PlatformMonitorService monitorService;
    private final SystemScopePermissionService scopePermissionService;

    /**
     * 创建系统监控接口。
     *
     * @param monitorService 系统监控服务
     */
    public PlatformMonitorController(PlatformMonitorService monitorService,
                                     SystemScopePermissionService scopePermissionService) {
        this.monitorService = monitorService;
        this.scopePermissionService = scopePermissionService;
    }

    /**
     * 查询中台系统监控总览。
     *
     * @return 服务健康、机器资源和图表数据
     */
    @GetMapping("/overview")
    public ReturnResult<Map<String, Object>> overview(@RequestParam(value = "serviceCode", required = false) String serviceCode,
                                                      HttpServletRequest request) {
        Set<String> supportedServiceCodes = monitorService.supportedServiceCodes();
        boolean globalAccess = scopePermissionService.hasGlobalAccess(request, "monitor");
        Set<String> allowedServiceCodes = scopePermissionService.allowedSystemCodes(
                request,
                "monitor",
                supportedServiceCodes
        );
        String requestedServiceCode = StringUtils.hasText(serviceCode)
                ? serviceCode.trim().toLowerCase(Locale.ROOT)
                : "all";
        if ("all".equals(requestedServiceCode)) {
            if (!globalAccess) {
                throw new IllegalArgumentException("当前用户无权查看全部系统监控");
            }
            return new ReturnResult<>(SUCCESS, "查询成功", monitorService.overview("all"));
        }
        if (!supportedServiceCodes.contains(requestedServiceCode)) {
            throw new IllegalArgumentException("不支持的监控服务: " + serviceCode);
        }
        if (!allowedServiceCodes.contains(requestedServiceCode)) {
            throw new IllegalArgumentException("当前用户无权查看该服务监控: " + requestedServiceCode);
        }
        return new ReturnResult<>(
                SUCCESS,
                "查询成功",
                monitorService.overview(requestedServiceCode, globalAccess ? null : allowedServiceCodes)
        );
    }
}
