package com.fly.rent.web.config;

import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.entity.Result;
import com.fly.rent.web.support.AbstractWebController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝租赁管理端运行时配置。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/web/config")
public class WebRuntimeConfigController extends AbstractWebController {

    private final AlipayPlatformConfigService configService;

    @GetMapping("/runtime")
    public Result runtime() {
        Map<String, Object> data = new HashMap<>();
        data.put("imageStorageType", configService.imageStorageType());
        data.put("imageBaseUrl", configService.publicImageBaseUrl());
        data.put("aliyunImageBaseUrl", configService.aliyunImageBaseUrl());
        data.put("localImageBaseUrl", configService.localImageBaseUrl());
        return success(data);
    }
}
