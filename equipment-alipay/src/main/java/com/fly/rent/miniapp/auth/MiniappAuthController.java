package com.fly.rent.miniapp.auth;

import com.fly.rent.common.dto.RentRequests;
import com.fly.rent.entity.Result;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序认证入口。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rent/v1/miniapp/auth")
public class MiniappAuthController {

    private final MiniappAuthService authService;

    /**
     * 支付宝小程序静默登录。
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/alipay/login")
    public Result login(@RequestBody RentRequests.AlipayLoginRequest request) {
        return ResultUtil.success(authService.login(request));
    }
}
