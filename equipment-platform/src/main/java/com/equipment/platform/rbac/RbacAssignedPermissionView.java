package com.equipment.platform.rbac;

import lombok.Data;

/**
 * 用户已分配权限行。
 */
@Data
public class RbacAssignedPermissionView {

    /** 角色编码。 */
    private String roleCode;

    /** 系统编码。 */
    private String systemCode;

    /** 页面编码。 */
    private String pageCode;

    /** 按钮编码。 */
    private String buttonCode;
}
