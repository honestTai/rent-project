package com.equipment.platform.rbac;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * RBAC 角色实体。
 * <p>
 * 角色由中台统一维护，再通过用户角色关系绑定到具体后台账号。
 * </p>
 */
@Data
@TableName("platform_role")
public class RbacRole {

    /** 角色主键。 */
    private Long id;

    /** 角色编码。 */
    private String roleCode;

    /** 角色名称。 */
    private String roleName;

    /** 角色说明。 */
    private String roleDesc;

    /** 角色等级，数值越小管理权限越高。 */
    private Integer roleLevel;

    /** 是否启用。 */
    private Integer enabled;

    /** 是否系统内置角色，1 表示内置。 */
    private Integer builtIn;

    /** 创建时间。 */
    private Date createdAt;

    /** 更新时间。 */
    private Date updatedAt;
}
