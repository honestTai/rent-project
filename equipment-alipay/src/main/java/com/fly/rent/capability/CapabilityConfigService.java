package com.fly.rent.capability;

import com.common.zhongtai.config.ZhongtaiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CapabilityConfigService {

    private static final String SYSTEM_CODE = "alipay";

    private final ZhongtaiConfigService zhongtaiConfigService;

    public String optional(String key) {
        try {
            return zhongtaiConfigService.getOptionalString(SYSTEM_CODE, key);
        } catch (Exception ex) {
            return null;
        }
    }

    public String value(String key, String defaultValue) {
        String value = optional(key);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    public List<String> csv(String key, String defaultValue) {
        String value = value(key, defaultValue);
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return result;
        }
        for (String item : Arrays.asList(value.split(","))) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim().toLowerCase());
            }
        }
        return result;
    }

    public int intValue(String key, int defaultValue) {
        String value = optional(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean booleanValue(String key, boolean defaultValue) {
        String value = optional(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim();
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized);
    }
}
