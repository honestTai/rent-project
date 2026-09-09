package com.equipment.platform.user;

import com.aop.LoginToken.UserLoginToken;
import com.common.Constant.constant;
import com.common.Entity.ReturnResult;
import com.common.Util.UserInfo.UserContextHeaders;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.rbac.RbacService;
import com.equipment.platform.rbac.RbacUserRoleSaveRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 中台用户管理接口。
 * <p>
 * 路径兼容原租赁服务 `/api/user/*`，网关拆库后可直接把用户管理路由到中台服务。
 * </p>
 */
@CrossOrigin
@RestController
@RequestMapping("/api/user")
public class PlatformUserController {

    private final PlatformUserService userService;
    private final RbacService rbacService;

    /**
     * 创建中台用户管理接口。
     *
     * @param userService 中台用户服务
     * @param rbacService RBAC 服务
     */
    public PlatformUserController(PlatformUserService userService, RbacService rbacService) {
        this.userService = userService;
        this.rbacService = rbacService;
    }

    /**
     * 查询用户列表。
     *
     * @param query 查询参数
     * @return 用户分页列表
     */
    @PostMapping("/userList")
    @UserLoginToken
    public ReturnResult viewUserList(@RequestBody(required = false) PlatformUserQuery query, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "view");
        if (denied != null) {
            return denied;
        }
        return userService.listUser(query);
    }

    /**
     * 修改用户。
     *
     * @param user 用户信息
     * @return 修改结果
     */
    @PostMapping("/updateUser")
    @UserLoginToken
    public ReturnResult userUpdate(@RequestBody PlatformUser user, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "update");
        if (denied != null) {
            return denied;
        }
        return userService.updateUser(user, readIntegerHeader(request, UserContextHeaders.USER_ID));
    }

    /**
     * 删除用户。
     *
     * @param query 删除参数
     * @return 删除结果
     */
    @PostMapping("/deleteUser")
    @UserLoginToken
    public ReturnResult deleteUser(@RequestBody PlatformUserQuery query, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "delete");
        if (denied != null) {
            return denied;
        }
        return userService.deleteUser(query);
    }

    /**
     * 新增用户。
     *
     * @param user 用户信息
     * @return 新增结果
     */
    @PostMapping("/addUser")
    @UserLoginToken
    public ReturnResult addUser(@RequestBody PlatformUser user, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "create");
        if (denied != null) {
            return denied;
        }
        return userService.addUser(user, readIntegerHeader(request, UserContextHeaders.USER_ID));
    }

    /**
     * 用户管理页查询可分配角色。
     *
     * @param query 查询条件
     * @param request 当前请求
     * @return 角色分页数据
     */
    @GetMapping("/roles")
    @UserLoginToken
    public ReturnResult roles(PlatformQuery query, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "view");
        if (denied != null) {
            return denied;
        }
        return new ReturnResult<>(constant.SUCCESS, "查询成功", rbacService.listRolesPage(query));
    }

    /**
     * 用户管理页查询指定用户已绑定角色。
     *
     * @param userId 用户 ID
     * @param request 当前请求
     * @return 角色 ID 集合
     */
    @GetMapping("/roles/{userId}/role-ids")
    @UserLoginToken
    public ReturnResult userRoleIds(@PathVariable("userId") Integer userId, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "view");
        if (denied != null) {
            return denied;
        }
        return new ReturnResult<>(constant.SUCCESS, "查询成功", rbacService.userRoleIds(userId));
    }

    /**
     * 用户管理页保存用户角色授权。
     *
     * @param saveRequest 用户角色保存请求
     * @param request 当前请求
     * @return 保存结果
     */
    @PostMapping("/roles/save")
    @UserLoginToken
    public ReturnResult<Void> saveUserRoles(@RequestBody RbacUserRoleSaveRequest saveRequest,
                                            HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "update");
        if (denied != null) {
            return denied;
        }
        rbacService.saveUserRoles(saveRequest, readIntegerHeader(request, UserContextHeaders.USER_ID));
        return new ReturnResult<>(constant.SUCCESS, "用户角色已保存", null);
    }

    /**
     * 重置用户密码。
     *
     * @param user 用户 ID 和新密码
     * @param request 当前请求
     * @return 重置结果
     */
    @PostMapping("/resetPassword")
    @UserLoginToken
    public ReturnResult resetPassword(@RequestBody PlatformUser user, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "reset_password");
        if (denied != null) {
            return denied;
        }
        return userService.resetPassword(user);
    }

    /**
     * 查询当前登录用户资料。
     *
     * @param request 当前请求
     * @return 当前用户资料
     */
    @GetMapping("/current")
    @UserLoginToken
    public ReturnResult current(HttpServletRequest request) {
        return userService.currentUser(readIntegerHeader(request, UserContextHeaders.USER_ID));
    }

    /**
     * 当前登录用户修改自己的密码。
     *
     * @param passwordRequest 改密参数
     * @param request 当前请求
     * @return 修改结果
     */
    @PostMapping("/changePassword")
    @UserLoginToken
    public ReturnResult changePassword(@RequestBody PlatformPasswordChangeRequest passwordRequest,
                                       HttpServletRequest request) {
        return userService.changeOwnPassword(readIntegerHeader(request, UserContextHeaders.USER_ID), passwordRequest);
    }

    /**
     * 查询单个用户。
     *
     * @param query 查询参数
     * @return 用户详情
     */
    @PostMapping("/viewOneUser")
    @UserLoginToken
    public ReturnResult viewOneUser(@RequestBody PlatformUserQuery query, HttpServletRequest request) {
        ReturnResult<Void> denied = requirePermission(request, "view");
        if (denied != null) {
            return denied;
        }
        return userService.viewOneUser(query);
    }

    private ReturnResult<Void> requirePermission(HttpServletRequest request, String buttonCode) {
        if (rbacService.hasButtonPermission(readIntegerHeader(request, UserContextHeaders.USER_ID),
                "platform", "user", buttonCode)) {
            return null;
        }
        return new ReturnResult<>(constant.FORBIDDEN, constant.NO_ROLE, null);
    }

    private Integer readIntegerHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.parseInt(value);
    }
}
