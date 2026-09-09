package com.equipment.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.entity.PlatformNotifyChannel;
import com.equipment.platform.mapper.PlatformNotifyChannelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 中台通知通道服务。
 * <p>
 * 用于统一维护飞书机器人等通知通道，让不同业务场景可以拆到不同机器人或群。
 * </p>
 */
@Service
public class PlatformNotifyChannelService {

    private static final String SECRET_MASK = "******";

    private final PlatformNotifyChannelMapper channelMapper;

    /**
     * 创建通知通道服务。
     *
     * @param channelMapper 通知通道 Mapper
     */
    public PlatformNotifyChannelService(PlatformNotifyChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    /**
     * 查询通知通道。
     *
     * @param query 查询参数
     * @return 通知通道列表
     */
    public List<PlatformNotifyChannel> list(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        List<PlatformNotifyChannel> rows = channelMapper.selectList(buildListWrapper(safeQuery));
        for (PlatformNotifyChannel row : rows) {
            maskSecretValue(row);
        }
        return rows;
    }

    /**
     * 分页查询通知通道。
     *
     * @param query 查询参数
     * @return 分页通知通道
     */
    public Page<PlatformNotifyChannel> page(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        Page<PlatformNotifyChannel> page = channelMapper.selectPage(
                new Page<>(safePage(safeQuery), safePageSize(safeQuery)),
                buildListWrapper(safeQuery));
        for (PlatformNotifyChannel row : page.getRecords()) {
            maskSecretValue(row);
        }
        return page;
    }

    private QueryWrapper<PlatformNotifyChannel> buildListWrapper(PlatformQuery safeQuery) {
        QueryWrapper<PlatformNotifyChannel> wrapper = new QueryWrapper<PlatformNotifyChannel>()
                .orderByAsc("system_code", "scene_code", "id");
        if (StringUtils.hasText(safeQuery.getSystemCode())) {
            wrapper.eq("system_code", safeQuery.getSystemCode().trim());
        }
        if (StringUtils.hasText(safeQuery.getGroup())) {
            wrapper.eq("scene_code", safeQuery.getGroup().trim());
        }
        if (StringUtils.hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(w -> w.like("channel_code", keyword)
                    .or().like("scene_code", keyword)
                    .or().like("remark", keyword));
        }
        return wrapper;
    }

    private int safePage(PlatformQuery query) {
        return query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    }

    private int safePageSize(PlatformQuery query) {
        return query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    }

    /**
     * 按业务运行时读取通知通道原值。
     * <p>
     * 该方法供业务服务内部接口使用，不对 appSecret 脱敏。
     * </p>
     *
     * @param systemCode 系统编码
     * @param sceneCode 场景编码
     * @param channelCode 通道编码
     * @return 通知通道，未启用或不存在时返回 null
     */
    public PlatformNotifyChannel getRuntimeChannel(String systemCode, String sceneCode, String channelCode) {
        if (!StringUtils.hasText(systemCode) || !StringUtils.hasText(sceneCode) || !StringUtils.hasText(channelCode)) {
            throw new IllegalArgumentException("系统编码、场景编码和通道编码不能为空");
        }
        return channelMapper.selectOne(new QueryWrapper<PlatformNotifyChannel>()
                .eq("enabled", 1)
                .eq("system_code", systemCode.trim())
                .eq("scene_code", sceneCode.trim())
                .eq("channel_code", channelCode.trim())
                .last("limit 1"));
    }

    /**
     * 新增或更新通知通道。
     *
     * @param channel 通知通道
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(PlatformNotifyChannel channel) {
        validate(channel);
        Date now = new Date();
        applyDefaults(channel);
        PlatformNotifyChannel exist = findExisting(channel);
        if (shouldPreserveSecretValue(channel) && exist != null) {
            channel.setAppSecret(exist.getAppSecret());
        }
        channel.setUpdatedAt(now);
        if (channel.getId() == null && exist != null) {
            channel.setId(exist.getId());
            channel.setCreatedAt(exist.getCreatedAt());
            channelMapper.updateById(channel);
            return;
        }
        if (channel.getId() == null) {
            channel.setCreatedAt(now);
            channelMapper.insert(channel);
            return;
        }
        channelMapper.updateById(channel);
    }

    private void applyDefaults(PlatformNotifyChannel channel) {
        if (!StringUtils.hasText(channel.getChannelType())) {
            channel.setChannelType("feishu");
        }
        if (!StringUtils.hasText(channel.getReceiveIdType())) {
            channel.setReceiveIdType("chat_id");
        }
        if (channel.getEnabled() == null) {
            channel.setEnabled(1);
        }
    }

    /**
     * 删除通知通道。
     *
     * @param id 通道 ID
     */
    public void delete(Long id) {
        if (id != null) {
            channelMapper.deleteById(id);
        }
    }

    private void validate(PlatformNotifyChannel channel) {
        if (channel == null || !StringUtils.hasText(channel.getChannelCode())) {
            throw new IllegalArgumentException("通道编码不能为空");
        }
        if (!StringUtils.hasText(channel.getSystemCode())) {
            throw new IllegalArgumentException("系统编码不能为空");
        }
        if (!StringUtils.hasText(channel.getSceneCode())) {
            throw new IllegalArgumentException("场景编码不能为空");
        }
    }

    private PlatformNotifyChannel findExisting(PlatformNotifyChannel channel) {
        if (channel.getId() != null) {
            PlatformNotifyChannel exist = channelMapper.selectById(channel.getId());
            if (exist != null) {
                return exist;
            }
        }
        return channelMapper.selectOne(new QueryWrapper<PlatformNotifyChannel>()
                .eq("system_code", channel.getSystemCode())
                .eq("scene_code", channel.getSceneCode())
                .eq("channel_code", channel.getChannelCode())
                .last("limit 1"));
    }

    private boolean shouldPreserveSecretValue(PlatformNotifyChannel channel) {
        return !StringUtils.hasText(channel.getAppSecret())
                || SECRET_MASK.equals(channel.getAppSecret().trim());
    }

    private void maskSecretValue(PlatformNotifyChannel channel) {
        if (StringUtils.hasText(channel.getAppSecret())) {
            channel.setAppSecret(SECRET_MASK);
        }
    }
}
