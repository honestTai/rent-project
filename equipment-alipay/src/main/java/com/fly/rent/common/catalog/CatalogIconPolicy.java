package com.fly.rent.common.catalog;

import com.fly.rent.common.support.RentApiException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 自有小程序目录允许使用的 antd-mini Icon 名称。 */
public final class CatalogIconPolicy {

    public static final Set<String> ALLOWED_ICON_NAMES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "CompassOutline",
            "VideoOutline",
            "SetOutline",
            "AppOutline",
            "AppstoreOutline",
            "TravelOutline",
            "CameraOutline",
            "PlayOutline",
            "LoopOutline",
            "AudioOutline",
            "CheckShieldOutline",
            "PictureOutline",
            "GiftOutline",
            "TagOutline"
    )));

    private CatalogIconPolicy() {
    }

    public static String requireAllowed(String value) {
        if (!StringUtils.hasText(value)) {
            throw new RentApiException(4001, "启用分类的 icon 不能为空");
        }
        String icon = value.trim();
        if (!ALLOWED_ICON_NAMES.contains(icon)) {
            throw new RentApiException(4001, "icon 必须从 antd-mini 分类图标白名单中选择");
        }
        return icon;
    }

    public static String validateOptional(String value) {
        if (!StringUtils.hasText(value)) return "";
        return requireAllowed(value);
    }
}
