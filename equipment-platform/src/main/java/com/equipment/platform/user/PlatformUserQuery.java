package com.equipment.platform.user;

import com.common.Entity.Page;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 中台用户列表查询参数。
 * <p>
 * 兼容原 `/api/user/userList` 请求体，保留前端历史传参里的 userSex、sex、type 三种性别字段。
 * </p>
 */
@Data
public class PlatformUserQuery extends Page {

    /** 用户真实姓名模糊查询条件。 */
    private String realName;

    /** 登录账号模糊查询条件。 */
    private String userName;

    /** 手机号模糊查询条件。 */
    private String userPhoneNum;

    /** 用户性别，0 表示男，1 表示女。 */
    private Integer userSex;

    /** 前端历史性别字段。 */
    private Integer sex;

    /** 前端历史筛选字段，部分页面误用该字段传性别。 */
    private Integer type;

    /** 登录时间开始。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /** 登录时间结束。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    /** 前端日期范围。 */
    private List<Date> dateList;

    /** 待删除用户 ID 集合。 */
    private List<Integer> userIdList;

    /** 前端历史批量删除 ID 集合。 */
    private List<Integer> idList;

    /** 最后登录地点模糊查询条件。 */
    private String loginAddress;

    /** RBAC 角色 ID，用于查询某个角色下的用户。 */
    private Long roleId;

    /** 用户 ID。 */
    private Integer id;
}
