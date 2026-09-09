package com.equipment.platform.rbac;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存用户角色关系请求。
 */
@Data
public class RbacUserRoleSaveRequest {

    /** 用户主键。 */
    private Integer userId;

    /** 角色主键集合。 */
    private List<Long> roleIds = new ArrayList<>();
}
