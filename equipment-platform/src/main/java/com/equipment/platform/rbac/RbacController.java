package com.equipment.platform.rbac;

import com.common.Entity.ReturnResult;
import com.common.Util.UserInfo.UserContextHeaders;
import com.equipment.platform.dto.PlatformQuery;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台 RBAC 管理接口。
 */
@RestController
@RequestMapping("/api/platform/rbac")
public class RbacController {

    private final RbacService rbacService;

    /**
     * 创建中台 RBAC 管理接口。
     *
     * @param rbacService RBAC 服务
     */
    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * 查询角色列表。
     *
     * @param keyword 角色关键字
     * @return 角色列表
     */
    @GetMapping("/roles")
    public ReturnResult roles(PlatformQuery query) {
        return new ReturnResult<>(SUCCESS, "查询成功", rbacService.listRolesPage(query));
    }

    /**
     * 保存角色。
     *
     * @param role 角色数据
     * @param request 当前请求
     * @return 保存结果
     */
    @PostMapping("/roles/save")
    public ReturnResult<Void> saveRole(@RequestBody RbacRole role, HttpServletRequest request) {
        rbacService.saveRole(role, readIntegerHeader(request, UserContextHeaders.USER_ID));
        return new ReturnResult<>(SUCCESS, "保存成功");
    }

    /**
     * 删除角色。
     *
     * @param id 角色 ID
     * @param request 当前请求
     * @return 删除结果
     */
    @DeleteMapping("/roles/{id}")
    public ReturnResult<Void> deleteRole(@PathVariable("id") Long id, HttpServletRequest request) {
        rbacService.deleteRole(id, readIntegerHeader(request, UserContextHeaders.USER_ID));
        return new ReturnResult<>(SUCCESS, "删除成功");
    }

    /**
     * 查询权限树。
     *
     * @return 权限树
     */
    @GetMapping("/permission-tree")
    public ReturnResult<List<RbacSystemNode>> permissionTree() {
        return new ReturnResult<>(SUCCESS, "查询成功", rbacService.permissionTree());
    }

    /**
     * 分页查询权限按钮明细。
     *
     * @param query 查询条件
     * @return 权限按钮明细
     */
    @GetMapping("/permission-buttons")
    public ReturnResult permissionButtons(PlatformQuery query) {
        return new ReturnResult<>(SUCCESS, "查询成功", rbacService.permissionButtons(query));
    }

    /**
     * 查询角色按钮权限。
     *
     * @param roleId 角色 ID
     * @return 按钮权限 ID 集合
     */
    @GetMapping("/roles/{roleId}/button-ids")
    public ReturnResult<List<Long>> roleButtonIds(@PathVariable("roleId") Long roleId) {
        return new ReturnResult<>(SUCCESS, "查询成功", rbacService.roleButtonIds(roleId));
    }

    /**
     * 保存角色按钮权限。
     *
     * @param saveRequest 保存请求
     * @param request 当前请求
     * @return 保存结果
     */
    @PostMapping("/roles/permissions/save")
    public ReturnResult<Void> saveRoleButtons(@RequestBody RbacPermissionSaveRequest saveRequest, HttpServletRequest request) {
        rbacService.saveRoleButtons(saveRequest, readIntegerHeader(request, UserContextHeaders.USER_ID));
        return new ReturnResult<>(SUCCESS, "保存成功");
    }

    /**
     * 查询用户角色。
     *
     * @param userId 用户 ID
     * @return 角色 ID 集合
     */
    @GetMapping("/users/{userId}/role-ids")
    public ReturnResult<List<Long>> userRoleIds(@PathVariable("userId") Integer userId) {
        return new ReturnResult<>(SUCCESS, "查询成功", rbacService.userRoleIds(userId));
    }

    /**
     * 保存用户角色。
     *
     * @param saveRequest 保存请求
     * @param request 当前请求
     * @return 保存结果
     */
    @PostMapping("/users/roles/save")
    public ReturnResult<Void> saveUserRoles(@RequestBody RbacUserRoleSaveRequest saveRequest, HttpServletRequest request) {
        rbacService.saveUserRoles(saveRequest, readIntegerHeader(request, UserContextHeaders.USER_ID));
        return new ReturnResult<>(SUCCESS, "保存成功");
    }

    /**
     * 查询当前登录用户权限。
     *
     * @param request 当前请求
     * @return 当前用户权限
     */
    @GetMapping("/current")
    public ReturnResult<RbacCurrentPermissionView> current(HttpServletRequest request) {
        return new ReturnResult<>(SUCCESS, "查询成功",
                rbacService.currentPermissions(readIntegerHeader(request, UserContextHeaders.USER_ID)));
    }

    private Integer readIntegerHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.parseInt(value);
    }
}
