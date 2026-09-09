package com.equipment.platform.rbac;

import lombok.Data;

/**
 * RBAC 按钮权限节点。
 * <p>
 * 按钮是最小授权单元，归属于某个系统的某个页面；apiMethod 和 apiPath 用于后续把按钮权限
 * 关联到后端接口校验。
 * </p>
 */
@Data
public class RbacButtonNode {

    /** 按钮权限主键。 */
    private Long id;

    /** 所属系统编码。 */
    private String systemCode;

    /** 所属页面编码。 */
    private String pageCode;

    /** 按钮编码，同一页面内唯一。 */
    private String buttonCode;

    /** 按钮名称。 */
    private String buttonName;

    /** 关联接口 HTTP 方法，可为空。 */
    private String apiMethod;

    /** 关联接口路径，可为空。 */
    private String apiPath;

    /** 排序值。 */
    private Integer sort;

    /** 是否启用。 */
    private Integer enabled;
}
