package com.equipment.platform.rbac;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存角色按钮权限请求。
 */
@Data
public class RbacPermissionSaveRequest {

    /** 角色主键。 */
    private Long roleId;

    /** 授权按钮主键集合。 */
    private List<Long> buttonIds = new ArrayList<>();
}
