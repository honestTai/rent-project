package com.equipment.platform.rbac;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户权限视图。
 * <p>
 * 三个业务前端可读取该结构后，按 pageCodes 控制页面入口，按 buttonCodes 控制功能按钮。
 * </p>
 */
@Data
public class RbacCurrentPermissionView {

    /** 当前用户 ID。 */
    private Integer userId;

    /** 当前用户角色编码集合。 */
    private List<String> roleCodes = new ArrayList<>();

    /** 可访问页面编码集合，格式为 systemCode:pageCode。 */
    private List<String> pageCodes = new ArrayList<>();

    /** 可用按钮编码集合，格式为 systemCode:pageCode:buttonCode。 */
    private List<String> buttonCodes = new ArrayList<>();
}
