package com.equipment.platform.rbac;

import com.common.Encryption.Aes;
import com.common.Util.Redis.RedisClient;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.mapper.RbacMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.common.Constant.constant.REDISNAME;

/**
 * 中台 RBAC 服务。
 * <p>
 * 负责角色、用户角色关系和系统-页面-按钮权限树维护。该服务只管理中台权限主数据，
 * 具体业务服务通过当前用户权限接口读取授权结果。
 * </p>
 */
@Service
public class RbacService {

    private static final int DEFAULT_ROLE_LEVEL = 100;
    private static final int NO_ROLE_LEVEL = 9999;

    private final RbacMapper rbacMapper;
    private final RedisClient redisClient;

    /**
     * 创建中台 RBAC 服务。
     *
     * @param rbacMapper RBAC Mapper
     * @param redisClient Redis 客户端
     */
    public RbacService(RbacMapper rbacMapper, RedisClient redisClient) {
        this.rbacMapper = rbacMapper;
        this.redisClient = redisClient;
    }

    /**
     * 查询角色列表。
     *
     * @param keyword 角色编码或名称关键字
     * @return 角色列表
     */
    public List<RbacRole> listRoles(String keyword) {
        return rbacMapper.selectRoles(trimToNull(keyword));
    }

    /**
     * 分页查询角色列表。
     *
     * @param query 查询参数
     * @return 角色分页数据
     */
    public Page<RbacRole> listRolesPage(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        return rbacMapper.selectRolesPage(new Page<>(safePage(safeQuery), safePageSize(safeQuery)),
                trimToNull(safeQuery.getKeyword()));
    }

    /**
     * 保存角色。
     *
     * @param role 角色数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(RbacRole role) {
        validateRole(role);
        if (role.getEnabled() == null) {
            role.setEnabled(1);
        }
        if (role.getBuiltIn() == null) {
            role.setBuiltIn(0);
        }
        if (role.getRoleLevel() == null) {
            role.setRoleLevel(DEFAULT_ROLE_LEVEL);
        }
        role.setRoleCode(role.getRoleCode().trim());
        role.setRoleName(role.getRoleName().trim());
        if (role.getId() == null) {
            rbacMapper.insert(role);
            return;
        }
        rbacMapper.updateById(role);
        forceRoleUsersRelogin(role.getId());
    }

    /**
     * 按当前操作人等级保存角色。
     *
     * @param role 角色数据
     * @param operatorUserId 当前操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(RbacRole role, Integer operatorUserId) {
        validateRoleManageLevel(role, operatorUserId);
        saveRole(role);
    }

    /**
     * 删除角色。
     *
     * @param roleId 角色主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        RbacRole role = rbacMapper.selectById(roleId);
        if (role != null && Integer.valueOf(1).equals(role.getBuiltIn())) {
            throw new IllegalArgumentException("系统内置角色不能删除");
        }
        forceRoleUsersRelogin(roleId);
        rbacMapper.deleteRolePermissionsByRoleId(roleId);
        rbacMapper.deleteUserRolesByRoleId(roleId);
        rbacMapper.deleteById(roleId);
    }

    /**
     * 按当前操作人等级删除角色。
     *
     * @param roleId 角色主键
     * @param operatorUserId 当前操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId, Integer operatorUserId) {
        validateExistingRoleManageLevel(roleId, operatorUserId);
        deleteRole(roleId);
    }

    /**
     * 查询权限树。
     *
     * @return 系统-页面-按钮权限树
     */
    public List<RbacSystemNode> permissionTree() {
        Map<String, RbacSystemNode> systemMap = new LinkedHashMap<>();
        Map<String, RbacPageNode> pageMap = new LinkedHashMap<>();
        for (RbacSystemNode system : rbacMapper.selectSystems()) {
            systemMap.put(system.getSystemCode(), system);
        }
        for (RbacPageNode page : rbacMapper.selectPages()) {
            RbacSystemNode system = systemMap.get(page.getSystemCode());
            if (system == null) {
                continue;
            }
            system.getPages().add(page);
            pageMap.put(pageKey(page.getSystemCode(), page.getPageCode()), page);
        }
        for (RbacButtonNode button : rbacMapper.selectButtons()) {
            RbacPageNode page = pageMap.get(pageKey(button.getSystemCode(), button.getPageCode()));
            if (page != null) {
                page.getButtons().add(button);
            }
        }
        return new ArrayList<>(systemMap.values());
    }

    /**
     * 分页查询权限按钮明细。
     *
     * @param query 查询条件
     * @return 权限按钮分页数据
     */
    public Page<RbacPermissionButtonView> permissionButtons(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        return rbacMapper.selectPermissionButtonsPage(new Page<>(safePage(safeQuery), safePageSize(safeQuery)),
                safeQuery);
    }

    /**
     * 查询角色拥有的按钮权限主键。
     *
     * @param roleId 角色主键
     * @return 按钮权限主键集合
     */
    public List<Long> roleButtonIds(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        return rbacMapper.selectRoleButtonIds(roleId);
    }

    /**
     * 保存角色按钮权限。
     *
     * @param request 保存请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleButtons(RbacPermissionSaveRequest request) {
        if (request == null || request.getRoleId() == null) {
            throw new IllegalArgumentException("角色 ID 不能为空");
        }
        rbacMapper.deleteRolePermissionsByRoleId(request.getRoleId());
        if (CollectionUtils.isEmpty(request.getButtonIds())) {
            forceRoleUsersRelogin(request.getRoleId());
            return;
        }
        for (Long buttonId : request.getButtonIds()) {
            if (buttonId != null) {
                rbacMapper.insertRolePermission(request.getRoleId(), buttonId);
            }
        }
        forceRoleUsersRelogin(request.getRoleId());
    }

    /**
     * 按当前操作人等级保存角色按钮授权。
     *
     * @param request 保存请求
     * @param operatorUserId 当前操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRoleButtons(RbacPermissionSaveRequest request, Integer operatorUserId) {
        if (request == null || request.getRoleId() == null) {
            throw new IllegalArgumentException("角色 ID 不能为空");
        }
        validateExistingRoleManageLevel(request.getRoleId(), operatorUserId);
        saveRoleButtons(request);
    }

    /**
     * 查询用户角色主键集合。
     *
     * @param userId 用户主键
     * @return 角色主键集合
     */
    public List<Long> userRoleIds(Integer userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return rbacMapper.selectUserRoleIds(userId);
    }

    /**
     * 保存用户角色关系。
     *
     * @param request 保存请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(RbacUserRoleSaveRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        rbacMapper.deleteUserRolesByUserId(request.getUserId());
        if (CollectionUtils.isEmpty(request.getRoleIds())) {
            forceUserRelogin(request.getUserId());
            return;
        }
        for (Long roleId : request.getRoleIds()) {
            if (roleId != null) {
                rbacMapper.insertUserRole(request.getUserId(), roleId);
            }
        }
        forceUserRelogin(request.getUserId());
    }

    /**
     * 按当前操作人等级保存用户角色绑定。
     *
     * @param request 保存请求
     * @param operatorUserId 当前操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(RbacUserRoleSaveRequest request, Integer operatorUserId) {
        validateUserRoleManageLevel(request, operatorUserId);
        saveUserRoles(request);
    }

    /**
     * 查询当前用户权限。
     *
     * @param userId 当前用户 ID
     * @return 当前用户权限视图
     */
    public RbacCurrentPermissionView currentPermissions(Integer userId) {
        RbacCurrentPermissionView view = new RbacCurrentPermissionView();
        view.setUserId(userId);
        fillAssignedPermissions(view, userId);
        return view;
    }

    /**
     * 判断当前用户是否拥有指定按钮权限。
     *
     * @param userId 用户 ID
     * @param systemCode 系统编码
     * @param pageCode 页面编码
     * @param buttonCode 按钮编码
     * @return true 表示有权限
     */
    public boolean hasButtonPermission(Integer userId,
                                       String systemCode,
                                       String pageCode,
                                       String buttonCode) {
        if (userId == null || !StringUtils.hasText(systemCode)
                || !StringUtils.hasText(pageCode) || !StringUtils.hasText(buttonCode)) {
            return false;
        }
        return rbacMapper.countUserButtonPermission(userId, systemCode, pageCode, buttonCode) > 0;
    }

    private void validateRole(RbacRole role) {
        if (role == null || !StringUtils.hasText(role.getRoleCode()) || !StringUtils.hasText(role.getRoleName())) {
            throw new IllegalArgumentException("角色编码和名称不能为空");
        }
    }

    private void validateRoleManageLevel(RbacRole role, Integer operatorUserId) {
        int operatorLevel = currentRoleLevel(operatorUserId);
        if (canManageAll(operatorLevel)) {
            return;
        }
        int targetLevel = role.getRoleLevel() == null ? DEFAULT_ROLE_LEVEL : role.getRoleLevel();
        if (targetLevel <= operatorLevel) {
            throw new IllegalArgumentException("只能维护低于当前等级的角色");
        }
        if (role.getId() != null) {
            validateExistingRoleManageLevel(role.getId(), operatorUserId);
        }
    }

    private void validateExistingRoleManageLevel(Long roleId, Integer operatorUserId) {
        int operatorLevel = currentRoleLevel(operatorUserId);
        if (canManageAll(operatorLevel)) {
            return;
        }
        int targetLevel = roleLevel(roleId);
        if (targetLevel <= operatorLevel) {
            throw new IllegalArgumentException("只能维护低于当前等级的角色");
        }
    }

    private void validateUserRoleManageLevel(RbacUserRoleSaveRequest request, Integer operatorUserId) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        int operatorLevel = currentRoleLevel(operatorUserId);
        if (canManageAll(operatorLevel)) {
            return;
        }
        if (!CollectionUtils.isEmpty(request.getRoleIds())) {
            for (Long roleId : request.getRoleIds()) {
                if (roleId != null && roleLevel(roleId) <= operatorLevel) {
                    throw new IllegalArgumentException("只能给用户绑定低于当前等级的角色");
                }
            }
        }
    }

    private int currentRoleLevel(Integer userId) {
        if (userId == null) {
            return NO_ROLE_LEVEL;
        }
        Integer level = rbacMapper.selectCurrentMinRoleLevel(userId);
        return level == null ? NO_ROLE_LEVEL : level;
    }

    private int roleLevel(Long roleId) {
        Integer level = rbacMapper.selectRoleLevel(roleId);
        return level == null ? NO_ROLE_LEVEL : level;
    }

    private boolean canManageAll(int operatorLevel) {
        return operatorLevel == 0;
    }

    private void fillAssignedPermissions(RbacCurrentPermissionView view, Integer userId) {
        if (userId == null) {
            return;
        }
        for (RbacAssignedPermissionView row : rbacMapper.selectAssignedPermissions(userId)) {
            if (!view.getRoleCodes().contains(row.getRoleCode())) {
                view.getRoleCodes().add(row.getRoleCode());
            }
            String pageCode = pageCode(row.getSystemCode(), row.getPageCode());
            if (!view.getPageCodes().contains(pageCode)) {
                view.getPageCodes().add(pageCode);
            }
            view.getButtonCodes().add(buttonCode(row.getSystemCode(), row.getPageCode(), row.getButtonCode()));
        }
    }

    private String pageKey(String systemCode, String pageCode) {
        return systemCode + ":" + pageCode;
    }

    private String pageCode(String systemCode, String pageCode) {
        return systemCode + ":" + pageCode;
    }

    private String buttonCode(String systemCode, String pageCode, String buttonCode) {
        return systemCode + ":" + pageCode + ":" + buttonCode;
    }

    private int safePage(PlatformQuery query) {
        return query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    }

    private int safePageSize(PlatformQuery query) {
        return query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void forceRoleUsersRelogin(Long roleId) {
        if (roleId == null) {
            return;
        }
        for (String userName : rbacMapper.selectUserNamesByRoleId(roleId)) {
            if (StringUtils.hasText(userName)) {
                redisClient.deleteObject(REDISNAME + Aes.encrypt(userName));
            }
        }
    }

    private void forceUserRelogin(Integer userId) {
        String userName = rbacMapper.selectUserNameByUserId(userId);
        if (StringUtils.hasText(userName)) {
            redisClient.deleteObject(REDISNAME + Aes.encrypt(userName));
        }
    }
}
