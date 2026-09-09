package com.equipment.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.rbac.RbacAssignedPermissionView;
import com.equipment.platform.rbac.RbacButtonNode;
import com.equipment.platform.rbac.RbacPageNode;
import com.equipment.platform.rbac.RbacPermissionButtonView;
import com.equipment.platform.rbac.RbacRole;
import com.equipment.platform.rbac.RbacSystemNode;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RBAC MyBatis-Plus Mapper。
 */
public interface RbacMapper extends BaseMapper<RbacRole> {

    /**
     * 查询角色列表。
     *
     * @param keyword 角色编码或名称关键字
     * @return 角色列表
     */
    @Select("<script>"
            + "select id, role_code, role_name, role_desc, role_level, enabled, built_in, created_at, updated_at "
            + "from platform_role where 1=1 "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "and (role_code like concat('%', #{keyword}, '%') or role_name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "order by built_in desc, id desc"
            + "</script>")
    List<RbacRole> selectRoles(@Param("keyword") String keyword);

    /**
     * 分页查询角色列表。
     *
     * @param page 分页参数
     * @param keyword 角色编码或名称关键字
     * @return 角色分页
     */
    @Select("<script>"
            + "select id, role_code, role_name, role_desc, role_level, enabled, built_in, created_at, updated_at "
            + "from platform_role where 1=1 "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "and (role_code like concat('%', #{keyword}, '%') or role_name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "order by built_in desc, id desc"
            + "</script>")
    Page<RbacRole> selectRolesPage(Page<RbacRole> page, @Param("keyword") String keyword);

    /**
     * 查询系统权限节点。
     *
     * @return 系统节点
     */
    @Select("select id, system_code, system_name, sort, enabled "
            + "from platform_rbac_system where enabled = 1 order by sort, id")
    List<RbacSystemNode> selectSystems();

    /**
     * 查询页面权限节点。
     *
     * @return 页面节点
     */
    @Select("select id, system_code, page_code, page_name, route_path, sort, enabled "
            + "from platform_rbac_page where enabled = 1 order by system_code, sort, id")
    List<RbacPageNode> selectPages();

    /**
     * 查询按钮权限节点。
     *
     * @return 按钮节点
     */
    @Select("select id, system_code, page_code, button_code, button_name, api_method, api_path, sort, enabled "
            + "from platform_rbac_button where enabled = 1 order by system_code, page_code, sort, id")
    List<RbacButtonNode> selectButtons();

    /**
     * 分页查询权限按钮明细。
     *
     * @param query 查询条件
     * @return 权限按钮明细
     */
    @Select("<script>"
            + "select b.id, s.system_code, s.system_name, p.page_code, p.page_name, "
            + "b.button_code, b.button_name, b.api_method, b.api_path, "
            + "concat(b.system_code, ':', b.page_code, ':', b.button_code) as permission_code "
            + "from platform_rbac_button b "
            + "join platform_rbac_system s on s.system_code = b.system_code and s.enabled = 1 "
            + "join platform_rbac_page p on p.system_code = b.system_code and p.page_code = b.page_code and p.enabled = 1 "
            + "where b.enabled = 1 "
            + "<if test='query.systemCode != null and query.systemCode != \"\"'>"
            + "and b.system_code = #{query.systemCode} "
            + "</if>"
            + "<if test='query.pageCode != null and query.pageCode != \"\"'>"
            + "and b.page_code = #{query.pageCode} "
            + "</if>"
            + "<if test='query.buttonId != null'>"
            + "and b.id = #{query.buttonId} "
            + "</if>"
            + "<if test='query.keyword != null and query.keyword != \"\"'>"
            + "and (s.system_name like concat('%', #{query.keyword}, '%') "
            + "or s.system_code like concat('%', #{query.keyword}, '%') "
            + "or p.page_name like concat('%', #{query.keyword}, '%') "
            + "or p.page_code like concat('%', #{query.keyword}, '%') "
            + "or b.button_name like concat('%', #{query.keyword}, '%') "
            + "or b.button_code like concat('%', #{query.keyword}, '%') "
            + "or b.api_method like concat('%', #{query.keyword}, '%') "
            + "or b.api_path like concat('%', #{query.keyword}, '%')) "
            + "</if>"
            + "order by s.sort, p.sort, b.sort, b.id"
            + "</script>")
    List<RbacPermissionButtonView> selectPermissionButtons(@Param("query") PlatformQuery query);

    /**
     * 分页查询权限按钮明细。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 权限按钮分页
     */
    @Select("<script>"
            + "select b.id, s.system_code, s.system_name, p.page_code, p.page_name, "
            + "b.button_code, b.button_name, b.api_method, b.api_path, "
            + "concat(b.system_code, ':', b.page_code, ':', b.button_code) as permission_code "
            + "from platform_rbac_button b "
            + "join platform_rbac_system s on s.system_code = b.system_code and s.enabled = 1 "
            + "join platform_rbac_page p on p.system_code = b.system_code and p.page_code = b.page_code and p.enabled = 1 "
            + "where b.enabled = 1 "
            + "<if test='query.systemCode != null and query.systemCode != \"\"'>"
            + "and b.system_code = #{query.systemCode} "
            + "</if>"
            + "<if test='query.pageCode != null and query.pageCode != \"\"'>"
            + "and b.page_code = #{query.pageCode} "
            + "</if>"
            + "<if test='query.buttonId != null'>"
            + "and b.id = #{query.buttonId} "
            + "</if>"
            + "<if test='query.keyword != null and query.keyword != \"\"'>"
            + "and (s.system_name like concat('%', #{query.keyword}, '%') "
            + "or s.system_code like concat('%', #{query.keyword}, '%') "
            + "or p.page_name like concat('%', #{query.keyword}, '%') "
            + "or p.page_code like concat('%', #{query.keyword}, '%') "
            + "or b.button_name like concat('%', #{query.keyword}, '%') "
            + "or b.button_code like concat('%', #{query.keyword}, '%') "
            + "or b.api_method like concat('%', #{query.keyword}, '%') "
            + "or b.api_path like concat('%', #{query.keyword}, '%')) "
            + "</if>"
            + "order by s.sort, p.sort, b.sort, b.id"
            + "</script>")
    Page<RbacPermissionButtonView> selectPermissionButtonsPage(Page<RbacPermissionButtonView> page,
                                                               @Param("query") PlatformQuery query);

    /**
     * 查询角色按钮权限。
     *
     * @param roleId 角色 ID
     * @return 按钮 ID
     */
    @Select("select button_id from platform_role_permission where role_id = #{roleId}")
    List<Long> selectRoleButtonIds(@Param("roleId") Long roleId);

    /**
     * 查询用户角色。
     *
     * @param userId 用户 ID
     * @return 角色 ID
     */
    @Select("select role_id from platform_user_role where user_id = #{userId}")
    List<Long> selectUserRoleIds(@Param("userId") Integer userId);

    /**
     * 查询拥有指定角色的登录账号。
     *
     * @param roleId 角色 ID
     * @return 登录账号
     */
    @Select("select distinct u.userName from platform_user_role ur "
            + "join `user` u on u.id = ur.user_id where ur.role_id = #{roleId}")
    List<String> selectUserNamesByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询用户登录账号。
     *
     * @param userId 用户 ID
     * @return 登录账号
     */
    @Select("select userName from `user` where id = #{userId}")
    String selectUserNameByUserId(@Param("userId") Integer userId);

    /**
     * 查询用户是否拥有指定按钮权限。
     *
     * @param userId 用户 ID
     * @param systemCode 系统编码
     * @param pageCode 页面编码
     * @param buttonCode 按钮编码
     * @return 权限数量
     */
    @Select("select count(1) "
            + "from platform_user_role ur "
            + "join platform_role r on r.id = ur.role_id and r.enabled = 1 "
            + "join platform_role_permission rp on rp.role_id = r.id "
            + "join platform_rbac_button b on b.id = rp.button_id and b.enabled = 1 "
            + "where ur.user_id = #{userId} "
            + "and b.system_code = #{systemCode} "
            + "and b.page_code = #{pageCode} "
            + "and b.button_code = #{buttonCode}")
    int countUserButtonPermission(@Param("userId") Integer userId,
                                  @Param("systemCode") String systemCode,
                                  @Param("pageCode") String pageCode,
                                  @Param("buttonCode") String buttonCode);

    /**
     * 删除角色按钮权限。
     *
     * @param roleId 角色 ID
     */
    @Delete("delete from platform_role_permission where role_id = #{roleId}")
    void deleteRolePermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除使用指定角色的用户角色关系。
     *
     * @param roleId 角色 ID
     */
    @Delete("delete from platform_user_role where role_id = #{roleId}")
    void deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除用户角色关系。
     *
     * @param userId 用户 ID
     */
    @Delete("delete from platform_user_role where user_id = #{userId}")
    void deleteUserRolesByUserId(@Param("userId") Integer userId);

    /**
     * 新增角色按钮权限。
     *
     * @param roleId 角色 ID
     * @param buttonId 按钮 ID
     */
    @Insert("insert into platform_role_permission(role_id, button_id) values(#{roleId}, #{buttonId})")
    void insertRolePermission(@Param("roleId") Long roleId, @Param("buttonId") Long buttonId);

    /**
     * 新增用户角色关系。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    @Insert("insert into platform_user_role(user_id, role_id) values(#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") Integer userId, @Param("roleId") Long roleId);

    /**
     * 查询用户最小角色等级。
     *
     * @param userId 用户 ID
     * @return 最小角色等级
     */
    @Select("select min(r.role_level) from platform_user_role ur "
            + "join platform_role r on r.id = ur.role_id and r.enabled = 1 where ur.user_id = #{userId}")
    Integer selectCurrentMinRoleLevel(@Param("userId") Integer userId);

    /**
     * 查询角色等级。
     *
     * @param roleId 角色 ID
     * @return 角色等级
     */
    @Select("select role_level from platform_role where id = #{roleId}")
    Integer selectRoleLevel(@Param("roleId") Long roleId);

    /**
     * 查询用户已分配权限。
     *
     * @param userId 用户 ID
     * @return 权限行
     */
    @Select("select distinct r.role_code, b.system_code, b.page_code, b.button_code "
            + "from platform_user_role ur "
            + "join platform_role r on r.id = ur.role_id and r.enabled = 1 "
            + "join platform_role_permission rp on rp.role_id = r.id "
            + "join platform_rbac_button b on b.id = rp.button_id and b.enabled = 1 "
            + "where ur.user_id = #{userId}")
    List<RbacAssignedPermissionView> selectAssignedPermissions(@Param("userId") Integer userId);
}
