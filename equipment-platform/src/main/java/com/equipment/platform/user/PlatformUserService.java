package com.equipment.platform.user;

import com.common.Constant.constant;
import com.common.Encryption.Aes;
import com.common.Encryption.Md5;
import com.common.Entity.ReturnResult;
import com.common.Util.Redis.RedisClient;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipment.platform.mapper.PlatformUserMapper;
import com.equipment.platform.rbac.RbacService;
import com.equipment.platform.rbac.RbacUserRoleSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

import static com.common.Constant.constant.REDISNAME;
import static com.common.Encryption.Aes.encrypt;

/**
 * 中台用户服务。
 * <p>
 * 用户表迁入中台库后，网关登录和后台用户管理都访问同一份 user/log 数据，避免各业务库重复维护账号。
 * </p>
 */
@Service
public class PlatformUserService {

    private final PlatformUserMapper userMapper;
    private final RedisClient redisClient;
    private final RbacService rbacService;

    /**
     * 创建中台用户服务。
     *
     * @param userMapper 中台用户 Mapper
     * @param redisClient Redis 客户端
     * @param rbacService RBAC 服务
     */
    public PlatformUserService(PlatformUserMapper userMapper, RedisClient redisClient, RbacService rbacService) {
        this.userMapper = userMapper;
        this.redisClient = redisClient;
        this.rbacService = rbacService;
    }

    /**
     * 分页查询用户列表。
     *
     * @param query 查询参数
     * @return MyBatis-Plus Page 分页结果
     */
    public ReturnResult<Page<PlatformUserView>> listUser(PlatformUserQuery query) {
        PlatformUserQuery safeQuery = normalizeQuery(query);
        return new ReturnResult<>(constant.SUCCESS, "查询用户列表成功",
                userMapper.selectUserPage(new Page<>(safePage(safeQuery), safePageSize(safeQuery)), safeQuery));
    }

    /**
     * 删除用户。
     *
     * @param query 删除参数
     * @return 删除结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Void> deleteUser(PlatformUserQuery query) {
        List<Integer> ids = query == null || CollectionUtils.isEmpty(query.getUserIdList())
                ? query == null ? null : query.getIdList()
                : query.getUserIdList();
        if (CollectionUtils.isEmpty(ids)) {
            return new ReturnResult<>(constant.SUCCESS, "未选择用户", null);
        }
        for (PlatformUser user : userMapper.selectBatchIds(ids)) {
            forceUserRelogin(user);
        }
        userMapper.deleteBatchIds(ids);
        return new ReturnResult<>(constant.SUCCESS, "用户信息删除成功", null);
    }

    /**
     * 更新用户。
     *
     * @param user 用户信息
     * @return 更新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Void> updateUser(PlatformUser user, Integer operatorUserId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        user.setUserPwd(normalizePassword(user.getUserPwd()));
        userMapper.updateById(user);
        saveUserRolesIfPresent(user.getId(), user.getRoleIds(), operatorUserId);
        forceUserRelogin(userMapper.selectById(user.getId()));
        return new ReturnResult<>(constant.SUCCESS, "修改" + user.getUserName() + "信息成功", null);
    }

    /**
     * 新增用户。
     *
     * @param user 用户信息
     * @return 新增结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Integer> addUser(PlatformUser user, Integer operatorUserId) {
        if (user == null || !StringUtils.hasText(user.getUserName()) || !StringUtils.hasText(user.getUserPwd())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        user.setUserPwd(normalizePassword(user.getUserPwd()));
        user.setCreatUserDate(new Date());
        user.setUuid(firstText(user.getUuid(), "-1"));
        user.setIsNoRequest(user.getIsNoRequest() == null ? 0 : user.getIsNoRequest());
        userMapper.insert(user);
        saveUserRolesIfPresent(user.getId(), user.getRoleIds(), operatorUserId);
        return new ReturnResult<>(constant.SUCCESS, "新增" + user.getUserName() + "信息成功", user.getId());
    }

    /**
     * 重置用户密码。
     *
     * @param user 用户 ID 和新密码
     * @return 重置结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Void> resetPassword(PlatformUser user) {
        if (user == null || user.getId() == null || !StringUtils.hasText(user.getUserPwd())) {
            throw new IllegalArgumentException("用户 ID 和新密码不能为空");
        }
        PlatformUser update = new PlatformUser();
        update.setId(user.getId());
        update.setUserPwd(normalizePassword(user.getUserPwd()));
        userMapper.updateById(update);
        forceUserRelogin(userMapper.selectById(user.getId()));
        return new ReturnResult<>(constant.SUCCESS, "密码重置成功", null);
    }

    /**
     * 查询当前用户资料。
     *
     * @param userId 当前用户 ID
     * @return 当前用户资料
     */
    public ReturnResult<PlatformUserView> currentUser(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("当前用户 ID 不能为空");
        }
        PlatformUserView currentUser = userMapper.selectUserByIdWithLogin(userId);
        if (currentUser != null) {
            currentUser.setUserPwd(null);
        }
        return new ReturnResult<>(constant.SUCCESS, "查询当前用户成功", currentUser);
    }

    /**
     * 修改当前用户密码。
     *
     * @param userId 当前用户 ID
     * @param request 改密参数
     * @return 修改结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Void> changeOwnPassword(Integer userId, PlatformPasswordChangeRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("当前用户 ID 不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getOldPassword())
                || !StringUtils.hasText(request.getNewPassword())) {
            throw new IllegalArgumentException("原密码和新密码不能为空");
        }
        PlatformUser current = userMapper.selectById(userId);
        if (current == null) {
            throw new IllegalArgumentException("当前用户不存在");
        }
        String oldPassword = normalizePassword(request.getOldPassword());
        if (!oldPassword.equals(current.getUserPwd())) {
            return new ReturnResult<>(constant.LOGIN_FAIL_USERPWD, "原密码不正确", null);
        }
        PlatformUser update = new PlatformUser();
        update.setId(userId);
        update.setUserPwd(normalizePassword(request.getNewPassword()));
        userMapper.updateById(update);
        forceUserRelogin(current);
        return new ReturnResult<>(constant.SUCCESS, "密码修改成功，请重新登录", null);
    }

    /**
     * 查询单个用户。
     *
     * @param query 查询参数
     * @return 用户详情
     */
    public ReturnResult<PlatformUserView> viewOneUser(PlatformUserQuery query) {
        if (query == null || query.getId() == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        return new ReturnResult<>(constant.SUCCESS, "查看成功", userMapper.selectUserByIdWithLogin(query.getId()));
    }

    /**
     * 归一化查询参数。
     */
    private PlatformUserQuery normalizeQuery(PlatformUserQuery query) {
        PlatformUserQuery safeQuery = query == null ? new PlatformUserQuery() : query;
        Integer sex = safeQuery.getUserSex() != null ? safeQuery.getUserSex()
                : safeQuery.getSex() != null ? safeQuery.getSex() : safeQuery.getType();
        safeQuery.setUserSex(sex);
        if (!CollectionUtils.isEmpty(safeQuery.getDateList()) && safeQuery.getDateList().size() >= 2) {
            safeQuery.setStartTime(safeQuery.getDateList().get(0));
            safeQuery.setEndTime(safeQuery.getDateList().get(1));
        }
        return safeQuery;
    }

    /**
     * 标准化密码。
     * <p>
     * 前端查看用户时可能回传已有密文；看起来已经是密文时直接保留，避免保存时二次加密。
     * </p>
     */
    private String normalizePassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            return null;
        }
        String password = rawPassword.trim();
        if (password.length() >= 24 && password.matches("^[A-Za-z0-9+/=]+$")) {
            return password;
        }
        return encrypt(Md5.md5String(password));
    }

    private int safePage(PlatformUserQuery query) {
        return query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    }

    private int safePageSize(PlatformUserQuery query) {
        return query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void saveUserRolesIfPresent(Integer userId, List<Long> roleIds, Integer operatorUserId) {
        if (roleIds == null) {
            return;
        }
        RbacUserRoleSaveRequest request = new RbacUserRoleSaveRequest();
        request.setUserId(userId);
        request.setRoleIds(roleIds);
        rbacService.saveUserRoles(request, operatorUserId);
    }

    private void forceUserRelogin(PlatformUser user) {
        if (user == null || !StringUtils.hasText(user.getUserName())) {
            return;
        }
        redisClient.deleteObject(REDISNAME + Aes.encrypt(user.getUserName()));
    }
}
