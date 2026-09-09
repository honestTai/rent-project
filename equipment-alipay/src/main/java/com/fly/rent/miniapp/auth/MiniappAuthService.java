package com.fly.rent.miniapp.auth;

import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.request.AlipayUserInfoShareRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayUserInfoShareResponse;
import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.order.RentViewAssembler;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.common.support.RentTimeSupport;
import com.fly.rent.common.user.RentProfileService;
import com.common.Encryption.Aes;
import com.fly.rent.config.JwtHelper;
import com.fly.rent.config.RedisClient;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.entity.User;
import com.fly.rent.legacy.service.AlipayClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 小程序登录服务。
 * 支付宝 authCode 只用于换取当前用户标识，不向业务层透传支付宝 accessToken。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MiniappAuthService {

    private static final int TOKEN_EXPIRE_MINUTES = 24 * 60;

    private final AlipayClientService alipayClientService;
    private final RedisClient redisClient;
    private final RentProfileService profileService;
    private final RentViewAssembler viewAssembler;
    private final RentExtensionStore extensionStore;
    private final JwtHelper jwtHelper;
    private final AlipayPlatformConfigService platformConfigService;

    private static final String DEMO_USER_UUID = "DEMO_USER_001";

    /**
     * 执行支付宝静默登录，并下发系统自己的业务 token。
     * @param request 登录请求
     * @return 登录响应数据
     */
    public RentViews.LoginDataView login(RentRequests.AlipayLoginRequest request) {
        boolean demoMode = platformConfigService.demoModeEnabled();
        validateRequest(request, demoMode);
        if (demoMode) {
            User demoUser = profileService.syncAlipayProfile(
                    DEMO_USER_UUID, null, null, "HONESTTAI Demo 用户", null);
            return issueSession(request, demoUser, DEMO_USER_UUID, null, null);
        }
        AlipaySystemOauthTokenResponse oauthResponse = exchangeAuthCode(request.getAuthCode().trim());
        String alipayUserId = firstNotBlank(
                oauthResponse.getUserId(),
                oauthResponse.getAlipayUserId()
        );
        String alipayOpenId = firstNotBlank(oauthResponse.getOpenId());
        String primaryIdentity = firstNotBlank(alipayOpenId, alipayUserId);
        if (!StringUtils.hasText(primaryIdentity)) {
            throw new RentApiException(500, "支付宝登录失败，未获取到用户标识");
        }

        User user = profileService.findOrCreatePersistentUser(primaryIdentity, alipayUserId, alipayOpenId);
        AlipayUserInfoShareResponse userInfo = loadAlipayUserInfo(oauthResponse.getAccessToken());
        if (userInfo != null) {
            alipayUserId = firstNotBlank(alipayUserId, userInfo.getUserId());
            alipayOpenId = firstNotBlank(alipayOpenId, userInfo.getOpenId());
            user = profileService.syncAlipayProfile(primaryIdentity, alipayUserId, alipayOpenId,
                    firstNotBlank(userInfo.getNickName(), userInfo.getUserName(), userInfo.getDisplayName()),
                    userInfo.getAvatar());
        }

        return issueSession(request, user, primaryIdentity, alipayUserId, alipayOpenId);
    }

    private RentViews.LoginDataView issueSession(RentRequests.AlipayLoginRequest request, User user,
                                                  String primaryIdentity, String alipayUserId,
                                                  String alipayOpenId) {
        String sessionUserName = "rent:" + primaryIdentity;
        String sessionSecret = UUID.randomUUID().toString().replace("-", "") + request.getDeviceId();

        User sessionUser = new User();
        sessionUser.setUserId(user.getUserId());
        sessionUser.setUAcco(sessionUserName);
        sessionUser.setUPass(sessionSecret);
        sessionUser.setUuid(primaryIdentity);
        sessionUser.setAlipayUserId(alipayUserId);
        sessionUser.setOpenid(alipayOpenId);
        sessionUser.setUserTel(user.getUserTel());
        sessionUser.setIsNoRequest(0);

        String token = jwtHelper.sign(sessionUserName, sessionSecret);
        if (!StringUtils.hasText(token)) {
            throw new RentApiException(500, "生成登录 token 失败");
        }

        int expireMinutes = jwtHelper.getExpireMinutes();
        String redisKey = "voteRedis" + Aes.encrypt(sessionUserName);
        redisClient.setCacheObject(redisKey, sessionUser, expireMinutes, TimeUnit.MINUTES);

        String refreshToken = "refresh_" + UUID.randomUUID().toString().replace("-", "");
        extensionStore.saveRefreshToken(refreshToken, sessionUserName);

        RentViews.LoginDataView data = new RentViews.LoginDataView();
        data.setToken(token);
        data.setRefreshToken(refreshToken);
        data.setExpiredAt(OffsetDateTime.now(RentTimeSupport.ZONE_ID).plusMinutes(jwtHelper.getExpireMinutes()).toString());
        data.setUserId(String.valueOf(user.getUserId()));
        data.setUser(viewAssembler.toUserProfileView(user, profileService.loadProfileExtra(primaryIdentity)));
        return data;
    }

    /**
     * 校验登录请求参数
     * @param request 登录请求
     */
    private void validateRequest(RentRequests.AlipayLoginRequest request, boolean demoMode) {
        if (request == null) {
            throw new RentApiException(400, "请求体不能为空");
        }
        if (!demoMode && !StringUtils.hasText(request.getAuthCode())) {
            throw new RentApiException(400, "authCode 不能为空");
        }
        if (!"alipay-miniapp".equals(request.getPlatform())) {
            throw new RentApiException(400, "platform 仅支持 alipay-miniapp");
        }
        if (!StringUtils.hasText(request.getDeviceId())) {
            throw new RentApiException(400, "deviceId 不能为空");
        }
        if (!StringUtils.hasText(request.getClientVersion())) {
            throw new RentApiException(400, "clientVersion 不能为空");
        }
    }

    /**
     * 调用支付宝换取用户标识。
     * @param authCode 授权码
     * @return 支付宝OAuth响应
     */
    private AlipaySystemOauthTokenResponse exchangeAuthCode(String authCode) {
        try {
            AlipaySystemOauthTokenRequest request = new AlipaySystemOauthTokenRequest();
            request.setGrantType("authorization_code");
            request.setCode(authCode);
            AlipaySystemOauthTokenResponse response = alipayClientService.execute(request);
            if (!response.isSuccess()) {
                throw new RentApiException(400, "支付宝授权失败: " + response.getSubMsg());
            }
            return response;
        } catch (AlipayApiException ex) {
            throw new RentApiException(500, "调用支付宝授权接口失败");
        }
    }

    /**
     * 根据 auth_user 授权 token 拉取支付宝公开资料。
     * @param accessToken 用户授权 token
     * @return 支付宝公开资料，失败时返回 null，不阻断登录
     */
    private AlipayUserInfoShareResponse loadAlipayUserInfo(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        try {
            AlipayUserInfoShareRequest request = new AlipayUserInfoShareRequest();
            AlipayUserInfoShareResponse response = alipayClientService.execute(request, accessToken);
            if (response != null && response.isSuccess()) {
                return response;
            }
            log.warn("获取支付宝用户资料失败: code={}, subCode={}, subMsg={}",
                    response == null ? null : response.getCode(),
                    response == null ? null : response.getSubCode(),
                    response == null ? null : response.getSubMsg());
            return null;
        } catch (AlipayApiException ex) {
            log.warn("调用支付宝用户资料接口异常", ex);
            return null;
        }
    }

    /**
     * 获取第一个非空字符串
     * @param values 字符串数组
     * @return 第一个非空字符串
     */
    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
