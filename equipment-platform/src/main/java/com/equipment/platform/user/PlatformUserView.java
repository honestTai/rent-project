package com.equipment.platform.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 中台用户列表返回对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformUserView extends PlatformUser {

    /** 最近一次登录时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date loginTime;

    /** 最近一次登录 IP。 */
    private String loginIp;

    /** 最近一次登录地址。 */
    private String loginAddress;

    /** RBAC 角色名称集合。 */
    private String roleNames;

    /** RBAC 角色编码集合。 */
    private String roleCodes;
}
