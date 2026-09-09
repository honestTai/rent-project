package com.equipment.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipment.platform.user.PlatformUser;
import com.equipment.platform.user.PlatformUserQuery;
import com.equipment.platform.user.PlatformUserView;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 中台用户 MyBatis-Plus Mapper。
 */
public interface PlatformUserMapper extends BaseMapper<PlatformUser> {

    /**
     * 分页查询用户列表。
     *
     * @param query 查询条件
     * @return 用户列表
     */
    @Select("<script>"
            + "select v.* from ("
            + "select u.*, ll.dateTime as loginTime, ll.userIp as loginIp, ll.userAddress as loginAddress, "
            + "rr.roleNames, rr.roleCodes "
            + "from `user` u left join ("
            + "select l1.* from `log` l1 join (select userId, max(dateTime) maxDate from `log` where type = 1 group by userId) lm "
            + "on l1.userId = lm.userId and l1.dateTime = lm.maxDate where l1.type = 1"
            + ") ll on ll.userId = u.id "
            + "left join ("
            + "select ur.user_id, group_concat(r.role_name order by r.role_level, r.id separator '、') as roleNames, "
            + "group_concat(r.role_code order by r.role_level, r.id separator '、') as roleCodes "
            + "from platform_user_role ur join platform_role r on r.id = ur.role_id group by ur.user_id"
            + ") rr on rr.user_id = u.id"
            + ") v where 1=1 "
            + "<if test='query.realName != null and query.realName != \"\"'>"
            + "and v.realName like concat('%', #{query.realName}, '%') "
            + "</if>"
            + "<if test='query.userName != null and query.userName != \"\"'>"
            + "and v.userName like concat('%', #{query.userName}, '%') "
            + "</if>"
            + "<if test='query.userPhoneNum != null and query.userPhoneNum != \"\"'>"
            + "and v.userPhoneNum like concat('%', #{query.userPhoneNum}, '%') "
            + "</if>"
            + "<if test='query.userSex != null'>"
            + "and v.userSex = #{query.userSex} "
            + "</if>"
            + "<if test='query.startTime != null and query.endTime != null'>"
            + "and v.loginTime between #{query.startTime} and #{query.endTime} "
            + "</if>"
            + "<if test='query.loginAddress != null and query.loginAddress != \"\"'>"
            + "and v.loginAddress like concat('%', #{query.loginAddress}, '%') "
            + "</if>"
            + "<if test='query.roleId != null'>"
            + "and exists (select 1 from platform_user_role pur where pur.user_id = v.id and pur.role_id = #{query.roleId}) "
            + "</if>"
            + "order by v.id desc"
            + "</script>")
    Page<PlatformUserView> selectUserPage(Page<PlatformUserView> page, @Param("query") PlatformUserQuery query);

    /**
     * 查询单个用户。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    @Select("select v.* from ("
            + "select u.*, ll.dateTime as loginTime, ll.userIp as loginIp, ll.userAddress as loginAddress, "
            + "rr.roleNames, rr.roleCodes "
            + "from `user` u left join ("
            + "select l1.* from `log` l1 join (select userId, max(dateTime) maxDate from `log` where type = 1 group by userId) lm "
            + "on l1.userId = lm.userId and l1.dateTime = lm.maxDate where l1.type = 1"
            + ") ll on ll.userId = u.id "
            + "left join ("
            + "select ur.user_id, group_concat(r.role_name order by r.role_level, r.id separator '、') as roleNames, "
            + "group_concat(r.role_code order by r.role_level, r.id separator '、') as roleCodes "
            + "from platform_user_role ur join platform_role r on r.id = ur.role_id group by ur.user_id"
            + ") rr on rr.user_id = u.id"
            + ") v where v.id = #{id}")
    PlatformUserView selectUserByIdWithLogin(@Param("id") Integer id);
}
