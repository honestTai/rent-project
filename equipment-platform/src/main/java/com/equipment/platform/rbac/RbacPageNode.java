package com.equipment.platform.rbac;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * RBAC 页面权限节点。
 * <p>
 * 页面归属于系统，下面挂载按钮权限。前端权限中心直接用该结构展示权限树。
 * </p>
 */
@Data
public class RbacPageNode {

    /** 页面主键。 */
    private Long id;

    /** 所属系统编码。 */
    private String systemCode;

    /** 页面编码，同一系统内唯一。 */
    private String pageCode;

    /** 页面名称。 */
    private String pageName;

    /** 前端路由路径。 */
    private String routePath;

    /** 排序值。 */
    private Integer sort;

    /** 是否启用。 */
    private Integer enabled;

    /** 页面下的按钮权限。 */
    private List<RbacButtonNode> buttons = new ArrayList<>();
}
