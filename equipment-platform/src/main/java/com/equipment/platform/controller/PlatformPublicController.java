package com.equipment.platform.controller;

import com.common.Entity.ReturnResult;
import com.equipment.platform.service.PlatformConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台公开配置接口。
 * <p>
 * 仅暴露前端可公开展示的配置，不返回通用配置列表。
 * </p>
 */
@RestController
@RequestMapping("/api/platform/public")
public class PlatformPublicController {

    private static final String SITE_SYSTEM_CODE = "common";
    private static final String SITE_GROUP = "site";
    private static final List<String> SITE_FOOTER_KEYS = Arrays.asList(
            "site.footer.icp-record",
            "site.footer.police-record",
            "site.footer.copyright"
    );

    private final PlatformConfigService configService;

    /**
     * 创建公开配置接口。
     *
     * @param configService 配置服务
     */
    public PlatformPublicController(PlatformConfigService configService) {
        this.configService = configService;
    }

    /**
     * 读取全站页脚备案配置。
     *
     * @return 页脚备案配置
     */
    @GetMapping("/site-footer")
    public ReturnResult<Map<String, String>> siteFooter() {
        return new ReturnResult<>(SUCCESS, "查询成功",
                configService.getEnabledValues(SITE_SYSTEM_CODE, SITE_GROUP, SITE_FOOTER_KEYS));
    }
}
