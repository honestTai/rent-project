package com.fly.rent.miniapp.user;

import com.fly.rent.common.dto.RentRequests;
import com.fasterxml.jackson.databind.JsonNode;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.common.user.RentProfileService;
import com.fly.rent.entity.Result;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 小程序用户资料入口。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rent/v1/miniapp/users")
public class MiniappUserController {

    private final RentCurrentUserService currentUserService;
    private final RentProfileService profileService;
    private final AlipayPhoneDecryptService alipayPhoneDecryptService;
    private final AlipayCertifyQueryService alipayCertifyQueryService;
    private final AlipayCertifyInitService alipayCertifyInitService;

    /**
     * 获取当前登录用户资料（含 userTitle 等，供前端展示）。
     */
    @GetMapping("/me")
    public Result me() {
        Map<String, Object> data = new HashMap<>();
        data.put("user", profileService.getCurrentUserProfile(currentUserService.requireUserUuid()));
        return ResultUtil.success(data);
    }

    /**
     * 直接查库判断当前用户是否已填身份证和真实姓名。有则 data 为 true，否则为 false。
     */
    @GetMapping("/me/realname-check")
    public Result realnameCheck() {
        boolean has = profileService.hasRealNameAndIdCard(currentUserService.requireUserUuid());
        return ResultUtil.success(has);
    }

    /**
     * 更新当前登录用户资料。
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/me")
    public Result updateMe(@RequestBody RentRequests.UserProfileUpdateRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", profileService.updateCurrentUserProfile(currentUserService.requireUserUuid(), request));
        return ResultUtil.success(data);
    }

    /**
     * 使用支付宝 my.getPhoneNumber() 返回的加密数据更新当前用户手机号。
     * 请求体需包含 response 字段，值为支付宝返回的完整报文（JSON 字符串，内含 response 与 sign）。
     * 后端使用支付宝 SDK 验签并解密，提取手机号后更新到用户表。
     *
     * @param request 支付宝获取手机号更新请求（含 response 密文）
     * @return 更新后的用户资料
     */
    @PutMapping("/me/phone")
    public Result updateMePhone(@RequestBody JsonNode request) {
        String userUuid = currentUserService.requireUserUuid();
        String mobile = alipayPhoneDecryptService.decryptAndGetMobile(request);
        Map<String, Object> data = new HashMap<>();
        data.put("user", profileService.updateUserPhone(userUuid, mobile));
        return ResultUtil.success(data);
    }

    /**
     * 实人认证初始化：返回 certifyId 和 url，供小程序 my.startAPVerify 唤起支付宝认证页。
     */
    @PostMapping("/realname/certify/init")
    public Result certifyInit(@RequestBody RentRequests.CertifyInitRequest request) {
        if (request == null || request.getName() == null || request.getIdCard() == null) {
            return ResultUtil.error(400, "name 和 idCard 不能为空");
        }
        Map<String, String> data = alipayCertifyInitService.initCertify(request.getName(), request.getIdCard());
        return ResultUtil.success(data);
    }

    /**
     * 提交实名信息。须先经小程序 my.startAPVerify 完成实人认证，再带 certifyId 调用本接口。
     * 先调支付宝 alipay.user.certify.open.query 查认证结果，通过后才落库。
     *
     * @param request 实名认证请求（certifyId、name、idCard 必填）
     * @return data 为 true 表示认证成功，false 表示实人认证未通过
     */
    @PostMapping("/realname/verify")
    public Result verify(@RequestBody RentRequests.RealNameVerifyRequest request) {
        if (request == null || request.getCertifyId() == null || request.getCertifyId().trim().isEmpty()) {
            return ResultUtil.error(400, "请先完成支付宝实人认证");
        }
        if (!alipayCertifyQueryService.isPassed(request.getCertifyId().trim())) {
            return ResultUtil.success(false);
        }
        profileService.verifyRealName(currentUserService.requireUserUuid(), request);
        return ResultUtil.success(true);
    }
}
