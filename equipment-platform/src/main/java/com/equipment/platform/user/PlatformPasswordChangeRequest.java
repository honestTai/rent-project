package com.equipment.platform.user;

import lombok.Data;

/**
 * 当前用户修改密码请求。
 */
@Data
public class PlatformPasswordChangeRequest {

    /** 当前密码。 */
    private String oldPassword;

    /** 新密码。 */
    private String newPassword;
}
