package com.equipment.platform.rbac;

import lombok.Data;

/**
 * 权限按钮明细视图。
 */
@Data
public class RbacPermissionButtonView {

    /** 按钮权限 ID。 */
    private Long id;

    /** 系统编码。 */
    private String systemCode;

    /** 系统名称。 */
    private String systemName;

    /** 页面编码。 */
    private String pageCode;

    /** 页面名称。 */
    private String pageName;

    /** 按钮编码。 */
    private String buttonCode;

    /** 按钮名称。 */
    private String buttonName;

    /** 权限编码。 */
    private String permissionCode;

    /** 请求方法。 */
    private String apiMethod;

    /** 接口路径。 */
    private String apiPath;
}
