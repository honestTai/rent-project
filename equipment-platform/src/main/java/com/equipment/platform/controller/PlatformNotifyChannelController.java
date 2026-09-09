package com.equipment.platform.controller;

import com.common.Entity.ReturnResult;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.entity.PlatformNotifyChannel;
import com.equipment.platform.service.PlatformNotifyChannelService;
import org.springframework.web.bind.annotation.*;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台通知通道接口。
 */
@RestController
@RequestMapping("/api/platform/notify-channel")
public class PlatformNotifyChannelController {

    private final PlatformNotifyChannelService channelService;

    /**
     * 创建通知通道接口。
     *
     * @param channelService 通知通道服务
     */
    public PlatformNotifyChannelController(PlatformNotifyChannelService channelService) {
        this.channelService = channelService;
    }

    /**
     * 查询通知通道。
     *
     * @param query 查询参数
     * @return 通知通道列表
     */
    @PostMapping("/list")
    public ReturnResult list(@RequestBody(required = false) PlatformQuery query) {
        return new ReturnResult<>(SUCCESS, "查询成功", channelService.page(query));
    }

    /**
     * 保存通知通道。
     *
     * @param channel 通知通道
     * @return 保存结果
     */
    @PostMapping("/save")
    public ReturnResult<Void> save(@RequestBody PlatformNotifyChannel channel) {
        channelService.save(channel);
        return new ReturnResult<>(SUCCESS, "保存成功");
    }

    /**
     * 删除通知通道。
     *
     * @param id 通道 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable("id") Long id) {
        channelService.delete(id);
        return new ReturnResult<>(SUCCESS, "删除成功");
    }
}
