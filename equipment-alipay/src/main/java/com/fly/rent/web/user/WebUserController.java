package com.fly.rent.web.user;

import com.fly.rent.web.support.AbstractWebController;
import com.fly.rent.entity.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web 兼容层用户管理接口。
 * 提供后台管理系统对C端用户的查询和状态管理功能。
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebUserController extends AbstractWebController {

    private final WebUserService userService;

    /**
     * 分页查询用户列表
     * @param body 查询条件
     * @return 用户列表
     */
    @PostMapping("/users/page")
    public Result pageUsers(@RequestBody(required = false) Map<String, Object> body) {
        return userService.pageUsers(request(body));
    }

    /**
     * 根据支付宝 OpenID 解析后台兼容链路使用的用户 UUID。
     * @param body 查询条件，openid 必填
     * @return 用户身份字段
     */
    @PostMapping("/users/identity/resolve")
    public Result resolveIdentity(@RequestBody(required = false) Map<String, Object> body) {
        return userService.resolveIdentity(request(body));
    }

    /**
     * 更新用户请求状态
     * 用于处理用户的某些申请（如认证申请等）
     * @param body 更新内容
     * @return 操作结果
     */
    @PostMapping("/users/request-status/update")
    public Result updateRequestStatus(@RequestBody(required = false) Map<String, Object> body) {
        return userService.updateRequestStatus(request(body));
    }

    /**
     * 获取角色列表
     * @param body 查询条件
     * @return 角色列表
     */
    @PostMapping("/roles/list")
    public Result listRoles(@RequestBody(required = false) Map<String, Object> body) {
        return userService.listRoles();
    }
}
