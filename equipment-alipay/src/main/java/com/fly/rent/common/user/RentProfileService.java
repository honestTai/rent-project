package com.fly.rent.common.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.model.RentUserProfileExtra;
import com.fly.rent.common.order.RentViewAssembler;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.common.support.RentTimeSupport;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.UserMapper;
import com.fly.rent.miniapp.cache.MiniappCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户资料服务。基础字段落 user 表，扩展走扩展存储；读走 Redis 热点缓存（防雪崩/击穿），更新后失效。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RentProfileService {

    private static final String CACHE_KEY_PREFIX = "user:profile:";

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final RentViewAssembler viewAssembler;
    private final RentExtensionStore extensionStore;
    private final MiniappCacheService miniappCache;

    /**
     * 登录后若本地没有用户记录，这里自动补一条最小可用数据。
     * @param userUuid 用户UUID
     * @return 用户对象
     */
    public User findOrCreatePersistentUser(String userUuid) {
        return findOrCreatePersistentUser(userUuid, null, null);
    }

    public User findOrCreatePersistentUser(String userUuid, String alipayUserId, String alipayOpenId) {
        User user = findOneBy("uuid", userUuid);
        if (user == null) {
            user = findOneBy("uuid", alipayOpenId);
        }
        if (user == null) {
            user = findOneBy("uuid", alipayUserId);
        }
        if (user == null) {
            user = findOneBy("alipay_user_id", alipayUserId);
        }
        if (user == null) {
            user = findOneBy("openid", alipayOpenId);
        }
        if (user != null) {
            if (syncAlipayIdentity(user, alipayUserId, alipayOpenId)) {
                user.updateById();
                miniappCache.evict(CACHE_KEY_PREFIX + userUuid);
            }
            return user;
        }
        User created = new User();
        created.setUuid(userUuid);
        created.setAlipayUserId(trimToNull(alipayUserId));
        created.setOpenid(trimToNull(alipayOpenId));
        created.setUserTitle("支付宝用户");
        created.setCreatetime(System.currentTimeMillis());
        created.insert();
        return created;
    }

    /**
     * 用支付宝授权资料刷新基础用户资料。
     */
    public User syncAlipayProfile(String userUuid, String userTitle, String userAvatar) {
        return syncAlipayProfile(userUuid, null, null, userTitle, userAvatar);
    }

    public User syncAlipayProfile(String userUuid, String alipayUserId, String alipayOpenId, String userTitle, String userAvatar) {
        User user = findOrCreatePersistentUser(userUuid, alipayUserId, alipayOpenId);
        boolean updated = false;
        updated = syncAlipayIdentity(user, alipayUserId, alipayOpenId) || updated;
        if (StringUtils.hasText(userTitle) && !userTitle.trim().equals(user.getUserTitle())) {
            user.setUserTitle(userTitle.trim());
            updated = true;
        }
        if (StringUtils.hasText(userAvatar) && !userAvatar.trim().equals(user.getUserAvatar())) {
            user.setUserAvatar(userAvatar.trim());
            updated = true;
        }
        if (updated) {
            user.updateById();
            miniappCache.evict(CACHE_KEY_PREFIX + userUuid);
        }
        return user;
    }

    private User findOneBy(String column, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        List<User> users = userMapper.selectList(new QueryWrapper<User>().eq(column, normalized).last("LIMIT 1"));
        return users == null || users.isEmpty() ? null : users.get(0);
    }

    private boolean syncAlipayIdentity(User user, String alipayUserId, String alipayOpenId) {
        boolean updated = false;
        String normalizedUserId = trimToNull(alipayUserId);
        if (normalizedUserId != null && !normalizedUserId.equals(user.getAlipayUserId())) {
            user.setAlipayUserId(normalizedUserId);
            updated = true;
        }
        String normalizedOpenId = trimToNull(alipayOpenId);
        if (normalizedOpenId != null && !normalizedOpenId.equals(user.getOpenid())) {
            user.setOpenid(normalizedOpenId);
            updated = true;
        }
        return updated;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 加载用户扩展信息
     * @param userUuid 用户UUID
     * @return 扩展信息对象
     */
    public RentUserProfileExtra loadProfileExtra(String userUuid) {
        return extensionStore.loadUserProfileExtra(userUuid);
    }

    /**
     * 直接查库：用户表是否已填身份证和真实姓名（有则 true，否则 false）。不做缓存。
     */
    public boolean hasRealNameAndIdCard(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            return false;
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("uuid", userUuid).last("LIMIT 1"));
        return user != null && StringUtils.hasText(user.getRealName()) && StringUtils.hasText(user.getIdCard());
    }

    /**
     * 判断当前用户是否已绑定手机号。
     */
    public boolean hasBoundPhone(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            return false;
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("uuid", userUuid).last("LIMIT 1"));
        return user != null && StringUtils.hasText(user.getUserTel());
    }

    /**
     * 获取当前用户资料。走 Redis 缓存，更新资料后自动失效。
     */
    public RentViews.UserProfileView getCurrentUserProfile(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            return null;
        }
        return miniappCache.getOrLoad(CACHE_KEY_PREFIX + userUuid,
                () -> viewAssembler.toUserProfileView(findOrCreatePersistentUser(userUuid), loadProfileExtra(userUuid)));
    }

    /**
     * 使用支付宝解密得到的手机号更新当前用户手机号。
     * @param userUuid 用户UUID
     * @param mobile 11 位手机号（已由调用方解密并校验）
     * @return 更新后的用户资料视图
     */
    public RentViews.UserProfileView updateUserPhone(String userUuid, String mobile) {
        if (!StringUtils.hasText(mobile) || !mobile.matches("^\\d{11}$")) {
            throw new RentApiException(400, "手机号格式不正确");
        }
        User user = findOrCreatePersistentUser(userUuid);
        user.setUserTel(mobile.trim());
        user.updateById();
        miniappCache.evict(CACHE_KEY_PREFIX + userUuid);
        return viewAssembler.toUserProfileView(user, loadProfileExtra(userUuid));
    }

    /**
     * 更新当前用户资料
     * @param userUuid 用户UUID
     * @param request 更新请求
     * @return 更新后的用户资料视图
     */
    public RentViews.UserProfileView updateCurrentUserProfile(String userUuid, RentRequests.UserProfileUpdateRequest request) {
        validateProfileRequest(request);
        User user = findOrCreatePersistentUser(userUuid);
        String userTitle = firstText(request.getUserTitle(), request.getNickName());
        if (StringUtils.hasText(userTitle)) {
            user.setUserTitle(userTitle.trim());
        }
        String userAvatar = firstText(request.getUserAvatar(), request.getAvatar());
        if (StringUtils.hasText(userAvatar)) {
            user.setUserAvatar(userAvatar.trim());
        }
        if (StringUtils.hasText(request.getUserTel())) {
            user.setUserTel(request.getUserTel().trim());
        }
        if (StringUtils.hasText(request.getUserBirth())) {
            user.setUserBirth(RentTimeSupport.parseDate(request.getUserBirth()));
        }
        user.updateById();

        RentUserProfileExtra extra = loadProfileExtra(userUuid);
        if (StringUtils.hasText(request.getCity())) {
            extra.setCity(request.getCity().trim());
        }
        extensionStore.saveUserProfileExtra(userUuid, extra);
        miniappCache.evict(CACHE_KEY_PREFIX + userUuid);
        return viewAssembler.toUserProfileView(user, extra);
    }

    /**
     * 实名认证只负责校验和落库，订单链路状态仍以后续交易流程为准。
     * @param userUuid 用户UUID
     * @param request 实名认证请求
     * @return 实名认证视图
     */
    public RentViews.RealNameVerifyView verifyRealName(String userUuid, RentRequests.RealNameVerifyRequest request) {
        if (request == null) {
            throw new RentApiException(400, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new RentApiException(400, "name 不能为空");
        }
        if (!StringUtils.hasText(request.getIdCard()) || request.getIdCard().trim().length() < 15) {
            throw new RentApiException(400, "idCard 格式不正确");
        }

        User user = findOrCreatePersistentUser(userUuid);
        RentUserProfileExtra extra = loadProfileExtra(userUuid);
        extra.setVerified(Boolean.TRUE);
        extra.setRealName(request.getName().trim());
        extra.setIdCard(request.getIdCard().trim());
        extra.setIdCardTail(RentTimeSupport.tailIdCard(request.getIdCard()));
        extra.setLastVerifyAt(System.currentTimeMillis());
        extensionStore.saveUserProfileExtra(userUuid, extra);

        // 同步写入用户表，便于登录/下单时判断实名认证状态
        user.setRealName(request.getName().trim());
        user.setIdCard(request.getIdCard().trim());
        if (!StringUtils.hasText(user.getUserTitle())) {
            user.setUserTitle(request.getName().trim());
        }
        user.updateById();
        miniappCache.evict(CACHE_KEY_PREFIX + userUuid);

        // 如果携带了 orderId，回填订单的身份信息
        if (StringUtils.hasText(request.getOrderId())) {
            try {
                Integer oid = Integer.valueOf(request.getOrderId().trim());
                Order order = orderMapper.selectById(oid);
                if (order != null) {
                    boolean updated = false;
                    if (!StringUtils.hasText(order.getAvatar())) {
                        order.setAvatar(request.getName().trim());
                        updated = true;
                    }
                    if (!StringUtils.hasText(order.getEm())) {
                        order.setEm(RentTimeSupport.maskIdCard(request.getIdCard().trim()));
                        updated = true;
                    }
                    if (updated) {
                        orderMapper.updateById(order);
                        log.info("认证成功，已回填订单身份信息: orderId={}, realName={}", oid, request.getName().trim());
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("认证回填订单失败，orderId格式错误: {}", request.getOrderId());
            }
        }

        RentViews.RealNameVerifyView view = new RentViews.RealNameVerifyView();
        view.setVerified(Boolean.TRUE);
        view.setUser(viewAssembler.toUserProfileView(user, extra));
        return view;
    }

    /**
     * 校验用户资料更新请求
     * @param request 请求对象
     */
    private void validateProfileRequest(RentRequests.UserProfileUpdateRequest request) {
        if (request == null) {
            throw new RentApiException(400, "请求体不能为空");
        }
        // userTitle 可选，不传则不修改
        if (StringUtils.hasText(request.getUserTel()) && !request.getUserTel().trim().matches("^\\d{11}$")) {
            throw new RentApiException(400, "userTel 格式不正确");
        }
        // userTel、userBirth 可选，不传则不修改
    }

    /**
     * 返回第一个有文本的值。
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
