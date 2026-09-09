package com.equipment.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.entity.PlatformConfig;
import com.equipment.platform.mapper.PlatformConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中台通用配置服务。
 * <p>
 * 负责配置查询、保存和启停。业务系统读取同一张表，避免配置继续散落到 yml 和硬编码里。
 * </p>
 */
@Service
public class PlatformConfigService {

    private static final String SECRET_MASK = "******";
    private static final String ALIPAY_ITEM_FINENESS_CONFIG_KEY = "alipay.item.fineness";
    private static final String ALIPAY_ITEM_FINENESS_GRADE_CONFIG_KEY = "alipay.item.fineness-grade";
    private static final String ITEM_FINENESS_WHOLE_NEW = "wholeNew";
    private static final String ITEM_FINENESS_SECOND_HAND = "secondHand";
    private static final List<String> ITEM_FINENESS_GRADES =
            Collections.unmodifiableList(Arrays.asList("99new", "95new", "90new", "80new", "70new"));

    private final PlatformConfigMapper configMapper;

    /**
     * 创建中台通用配置服务。
     *
     * @param configMapper 配置 Mapper
     */
    public PlatformConfigService(PlatformConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 查询配置列表。
     *
     * @param query 查询参数
     * @return 配置列表
     */
    public List<PlatformConfig> list(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        List<PlatformConfig> rows = configMapper.selectList(buildListWrapper(safeQuery));
        for (PlatformConfig row : rows) {
            maskSecretValue(row);
        }
        return rows;
    }

    /**
     * 分页查询配置列表。
     *
     * @param query 查询参数
     * @return 分页配置列表
     */
    public Page<PlatformConfig> page(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        Page<PlatformConfig> page = configMapper.selectPage(
                new Page<>(safePage(safeQuery), safePageSize(safeQuery)),
                buildListWrapper(safeQuery));
        for (PlatformConfig row : page.getRecords()) {
            maskSecretValue(row);
        }
        return page;
    }

    private QueryWrapper<PlatformConfig> buildListWrapper(PlatformQuery safeQuery) {
        QueryWrapper<PlatformConfig> wrapper = new QueryWrapper<PlatformConfig>()
                .orderByAsc("system_code", "config_group", "sort", "id");
        if (StringUtils.hasText(safeQuery.getSystemCode())) {
            wrapper.eq("system_code", safeQuery.getSystemCode().trim());
        }
        if (StringUtils.hasText(safeQuery.getGroup())) {
            wrapper.eq("config_group", safeQuery.getGroup().trim());
        }
        if (StringUtils.hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(w -> w.like("config_key", keyword)
                    .or().like("display_name", keyword)
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
     * 按业务运行时读取配置原值。
     * <p>
     * 该方法供业务服务内部接口和中台自身运行逻辑使用，不做敏感值脱敏。
     * 先按指定系统读取，非 global 系统未命中时再读取 global 配置。
     * </p>
     *
     * @param systemCode 系统编码，为空时按 global 读取
     * @param configKey 配置键
     * @return 配置值，未配置或未启用时返回 null
     */
    public String getRuntimeValue(String systemCode, String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new IllegalArgumentException("配置键不能为空");
        }
        String normalizedSystem = StringUtils.hasText(systemCode) ? systemCode.trim() : "global";
        PlatformConfig config = selectEnabledConfig(normalizedSystem, configKey.trim());
        if (config == null && !"global".equals(normalizedSystem)) {
            config = selectEnabledConfig("global", configKey.trim());
        }
        return config == null ? null : config.getConfigValue();
    }

    /**
     * 按分组读取已启用的公开配置值。
     *
     * @param systemCode 系统编码
     * @param configGroup 配置分组
     * @param configKeys 配置键白名单
     * @return 配置键和值 Map
     */
    public Map<String, String> getEnabledValues(String systemCode, String configGroup, List<String> configKeys) {
        if (!StringUtils.hasText(configGroup)) {
            throw new IllegalArgumentException("配置分组不能为空");
        }
        String normalizedSystem = StringUtils.hasText(systemCode) ? systemCode.trim() : "global";
        QueryWrapper<PlatformConfig> wrapper = new QueryWrapper<PlatformConfig>()
                .eq("enabled", 1)
                .eq("system_code", normalizedSystem)
                .eq("config_group", configGroup.trim())
                .orderByAsc("sort", "id");
        if (configKeys != null && !configKeys.isEmpty()) {
            wrapper.in("config_key", configKeys);
        }
        List<PlatformConfig> rows = configMapper.selectList(wrapper);
        Map<String, String> values = new LinkedHashMap<>();
        for (PlatformConfig row : rows) {
            values.put(row.getConfigKey(), row.getConfigValue());
        }
        return values;
    }

    /**
     * 新增或更新配置。
     *
     * @param config 配置实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(PlatformConfig config) {
        validate(config);
        Date now = new Date();
        applyDefaults(config);
        PlatformConfig exist = findExisting(config);
        if (shouldPreserveSecretValue(config) && exist != null) {
            config.setConfigValue(exist.getConfigValue());
        }
        config.setUpdatedAt(now);
        if (config.getId() == null && exist != null) {
            config.setId(exist.getId());
            config.setCreatedAt(exist.getCreatedAt());
            configMapper.updateById(config);
            return;
        }
        if (config.getId() == null) {
            config.setCreatedAt(now);
            configMapper.insert(config);
            return;
        }
        configMapper.updateById(config);
    }

    private void applyDefaults(PlatformConfig config) {
        if (!StringUtils.hasText(config.getSystemCode())) {
            config.setSystemCode("global");
        }
        if (!StringUtils.hasText(config.getConfigGroup())) {
            config.setConfigGroup("business");
        }
        if (!StringUtils.hasText(config.getValueType())) {
            config.setValueType("string");
        }
        if (config.getSecretFlag() == null) {
            config.setSecretFlag(0);
        }
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        if (config.getSort() == null) {
            config.setSort(0);
        }
    }

    /**
     * 删除配置。
     *
     * @param id 配置 ID
     */
    public void delete(Long id) {
        if (id != null) {
            configMapper.deleteById(id);
        }
    }

    private void validate(PlatformConfig config) {
        if (config == null || !StringUtils.hasText(config.getConfigKey())) {
            throw new IllegalArgumentException("配置键不能为空");
        }
        validateAlipayItemFineness(config);
        validateAlipayItemFinenessGrade(config);
    }

    private void validateAlipayItemFineness(PlatformConfig config) {
        if (!ALIPAY_ITEM_FINENESS_CONFIG_KEY.equals(config.getConfigKey().trim())) {
            return;
        }
        if (!StringUtils.hasText(config.getConfigValue())) {
            throw new IllegalArgumentException("支付宝商品成色不能为空");
        }
        String value = config.getConfigValue().trim();
        if (!ITEM_FINENESS_WHOLE_NEW.equals(value) && !ITEM_FINENESS_SECOND_HAND.equals(value)) {
            throw new IllegalArgumentException("支付宝商品成色仅支持 wholeNew 或 secondHand");
        }
        config.setConfigValue(value);
    }

    private void validateAlipayItemFinenessGrade(PlatformConfig config) {
        if (!ALIPAY_ITEM_FINENESS_GRADE_CONFIG_KEY.equals(config.getConfigKey().trim())) {
            return;
        }
        if (!StringUtils.hasText(config.getConfigValue())) {
            throw new IllegalArgumentException("支付宝商品成色等级不能为空");
        }
        String value = config.getConfigValue().trim();
        if (!ITEM_FINENESS_GRADES.contains(value)) {
            throw new IllegalArgumentException("支付宝商品成色等级仅支持 99new、95new、90new、80new、70new");
        }
        config.setConfigValue(value);
    }

    private PlatformConfig findExisting(PlatformConfig config) {
        if (config.getId() != null) {
            PlatformConfig exist = configMapper.selectById(config.getId());
            if (exist != null) {
                return exist;
            }
        }
        return configMapper.selectOne(new QueryWrapper<PlatformConfig>()
                .eq("system_code", config.getSystemCode())
                .eq("config_key", config.getConfigKey())
                .last("limit 1"));
    }

    private PlatformConfig selectEnabledConfig(String systemCode, String configKey) {
        return configMapper.selectOne(new QueryWrapper<PlatformConfig>()
                .eq("enabled", 1)
                .eq("system_code", systemCode)
                .eq("config_key", configKey)
                .last("limit 1"));
    }

    private boolean shouldPreserveSecretValue(PlatformConfig config) {
        return isSecret(config) && (!StringUtils.hasText(config.getConfigValue())
                || SECRET_MASK.equals(config.getConfigValue().trim()));
    }

    private void maskSecretValue(PlatformConfig config) {
        if (isSecret(config) && StringUtils.hasText(config.getConfigValue())) {
            config.setConfigValue(SECRET_MASK);
        }
    }

    private boolean isSecret(PlatformConfig config) {
        return Integer.valueOf(1).equals(config.getSecretFlag())
                || "secret".equalsIgnoreCase(config.getValueType());
    }
}
