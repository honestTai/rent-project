package com.equipment.platform.controller;

import com.common.Entity.ReturnResult;
import com.equipment.platform.dto.SystemLogQuery;
import com.equipment.platform.service.SystemScopePermissionService;
import com.equipment.platform.service.SystemLogAuthService;
import com.equipment.platform.service.SystemLogService;
import com.equipment.platform.vo.SystemLogSourceVo;
import com.equipment.platform.vo.SystemLogViewVo;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台系统日志接口。
 * <p>
 * 只接入 Docker 部署时挂载到中台容器的应用日志和归档文件，业务操作日志继续走各系统原有日志功能。
 * 登录鉴权由网关统一处理，本接口只补充日志中心白名单校验。
 * </p>
 */
@RestController
@RequestMapping("/api/platform/system-log")
public class SystemLogController {

    private final SystemLogService systemLogService;
    private final SystemLogAuthService authService;
    private final SystemScopePermissionService scopePermissionService;

    /**
     * 创建系统日志接口。
     *
     * @param systemLogService 系统日志服务
     * @param authService 日志访问控制服务
     */
    public SystemLogController(SystemLogService systemLogService,
                               SystemLogAuthService authService,
                               SystemScopePermissionService scopePermissionService) {
        this.systemLogService = systemLogService;
        this.authService = authService;
        this.scopePermissionService = scopePermissionService;
    }

    /**
     * 查询日志来源。
     *
     * @param request 当前请求
     * @return 日志来源列表
     */
    @GetMapping("/sources")
    public ReturnResult<List<SystemLogSourceVo>> sources(HttpServletRequest request) {
        authService.checkAccess(request);
        Set<String> allowedSystemCodes = scopePermissionService.allowedSystemCodes(
                request,
                "systemLog",
                systemLogService.supportedSystemCodes()
        );
        List<SystemLogSourceVo> sources = new ArrayList<>();
        for (SystemLogSourceVo source : systemLogService.listSources()) {
            if (allowedSystemCodes.contains(source.getSystemCode())) {
                sources.add(source);
            }
        }
        return new ReturnResult<>(SUCCESS, "查询成功", sources);
    }

    /**
     * 查询日志内容。
     *
     * @param query 查询参数
     * @param request 当前请求
     * @return 日志内容
     */
    @PostMapping("/query")
    public ReturnResult<SystemLogViewVo> query(@RequestBody(required = false) SystemLogQuery query,
                                               HttpServletRequest request) {
        authService.checkAccess(request);
        SystemLogQuery safeQuery = query == null ? new SystemLogQuery() : query;
        safeQuery.setSystemCode(scopePermissionService.resolveAllowedSystemCode(
                request,
                safeQuery.getSystemCode(),
                "systemLog",
                systemLogService.supportedSystemCodes(),
                "platform"
        ));
        return new ReturnResult<>(SUCCESS, "查询成功", systemLogService.query(safeQuery));
    }
}
