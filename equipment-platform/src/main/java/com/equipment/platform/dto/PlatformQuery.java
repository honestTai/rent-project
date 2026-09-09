package com.equipment.platform.dto;

import lombok.Data;

/**
 * 中台列表查询参数。
 */
@Data
public class PlatformQuery {

    /** 系统编码。 */
    private String systemCode;

    /** 配置分组或通道场景。 */
    private String group;

    /** 关键字，匹配编码、名称或说明。 */
    private String keyword;

    /** 页面编码，用于权限资源明细查询。 */
    private String pageCode;

    /** 按钮权限 ID，用于权限资源明细查询。 */
    private Long buttonId;

    /** 当前页码。 */
    private Integer page;

    /** 每页条数。 */
    private Integer pageSize;

    /** 兼容预留字段；列表接口始终不返回敏感配置原文。 */
    private Boolean includeSecret;
}
