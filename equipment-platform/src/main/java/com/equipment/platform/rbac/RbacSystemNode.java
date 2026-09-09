package com.equipment.platform.rbac;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * RBAC 系统权限节点。
 * <p>
 * 当前模型按三个业务系统组织权限：设备租赁、支付宝租赁、二手交易；中台自身页面也可作为
 * platform 系统纳入同一套授权树。
 * </p>
 */
@Data
public class RbacSystemNode {

    /** 系统主键。 */
    private Long id;

    /** 系统编码。 */
    private String systemCode;

    /** 系统名称。 */
    private String systemName;

    /** 排序值。 */
    private Integer sort;

    /** 是否启用。 */
    private Integer enabled;

    /** 系统下的页面权限。 */
    private List<RbacPageNode> pages = new ArrayList<>();
}
