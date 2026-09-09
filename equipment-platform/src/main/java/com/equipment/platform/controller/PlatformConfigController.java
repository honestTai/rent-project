package com.equipment.platform.controller;

import com.common.Entity.ReturnResult;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.entity.PlatformConfig;
import com.equipment.platform.service.PlatformConfigService;
import org.springframework.web.bind.annotation.*;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台通用配置接口。
 */
@RestController
@RequestMapping("/api/platform/config")
public class PlatformConfigController {

    private final PlatformConfigService configService;

    /**
     * 创建中台配置接口。
     *
     * @param configService 配置服务
     */
    public PlatformConfigController(PlatformConfigService configService) {
        this.configService = configService;
    }

    /**
     * 查询配置列表。
     *
     * @param query 查询参数
     * @return 配置列表
     */
    @PostMapping("/list")
    public ReturnResult list(@RequestBody(required = false) PlatformQuery query) {
        return new ReturnResult<>(SUCCESS, "查询成功", configService.page(query));
    }

    /**
     * 保存配置。
     *
     * @param config 配置实体
     * @return 保存结果
     */
    @PostMapping("/save")
    public ReturnResult<Void> save(@RequestBody PlatformConfig config) {
        configService.save(config);
        return new ReturnResult<>(SUCCESS, "保存成功");
    }

    /**
     * 删除配置。
     *
     * @param id 配置 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable("id") Long id) {
        configService.delete(id);
        return new ReturnResult<>(SUCCESS, "删除成功");
    }
}
