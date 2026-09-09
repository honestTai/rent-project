package com.fly.rent.web.banner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.entity.MiniappBanner;
import com.fly.rent.entity.Result;
import com.fly.rent.mapper.MiniappBannerMapper;
import com.fly.rent.miniapp.catalog.MiniappCatalogService;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.web.support.WebResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 后台小程序首页轮播图配置服务。
 */
@Service
@RequiredArgsConstructor
public class WebMiniappBannerService {

    private final MiniappBannerMapper bannerMapper;
    private final MiniappCatalogService catalogService;

    public Result pageBanners(WebRequest req) {
        Page<MiniappBanner> mpPage = new Page<>(req.page(), req.limit());
        QueryWrapper<MiniappBanner> wrapper = new QueryWrapper<>();
        if (req.hasText("title")) {
            wrapper.like("title", req.text("title").trim());
        }
        Integer status = req.integer("status");
        if (status != null && status >= 0) {
            wrapper.eq("status", normalizeStatus(status));
        }
        wrapper.orderByDesc("sort_order").orderByDesc("banner_id");
        bannerMapper.selectPage(mpPage, wrapper);
        return WebResponseUtil.page(mpPage.getRecords(), mpPage.getTotal());
    }

    public Result createBanner(WebRequest req) {
        String title = requiredTrimmed(req.text("title"), "轮播标题不能为空");
        String image = requiredTrimmed(req.text("image"), "轮播图片不能为空");
        MiniappBanner banner = new MiniappBanner();
        Date now = new Date();
        banner.setTitle(title);
        banner.setDescription(trimToEmpty(req.text("description")));
        banner.setBadge(trimToEmpty(req.text("badge")));
        banner.setImage(image);
        banner.setLinkType(normalizeLinkType(req.text("linkType")));
        banner.setLinkValue(trimToEmpty(req.text("linkValue")));
        banner.setSortOrder(resolveSortOrder(req.integer("sortOrder")));
        banner.setStatus(normalizeStatus(req.integer("status"), 1));
        banner.setCreatedAt(now);
        banner.setUpdatedAt(now);
        bannerMapper.insert(banner);
        evictBanners();
        return WebResponseUtil.success(banner);
    }

    public Result updateBanner(WebRequest req) {
        Integer bannerId = req.integer("bannerId");
        if (bannerId == null) {
            return WebResponseUtil.error(1, "轮播图ID不能为空");
        }
        MiniappBanner banner = bannerMapper.selectById(bannerId);
        if (banner == null) {
            return WebResponseUtil.error(1, "轮播图不存在");
        }
        if (req.raw().containsKey("title")) {
            banner.setTitle(requiredTrimmed(req.text("title"), "轮播标题不能为空"));
        }
        if (req.raw().containsKey("description")) {
            banner.setDescription(trimToEmpty(req.text("description")));
        }
        if (req.raw().containsKey("badge")) {
            banner.setBadge(trimToEmpty(req.text("badge")));
        }
        if (req.raw().containsKey("image")) {
            banner.setImage(requiredTrimmed(req.text("image"), "轮播图片不能为空"));
        }
        if (req.raw().containsKey("linkType")) {
            banner.setLinkType(normalizeLinkType(req.text("linkType")));
        }
        if (req.raw().containsKey("linkValue")) {
            banner.setLinkValue(trimToEmpty(req.text("linkValue")));
        }
        if (req.raw().containsKey("sortOrder")) {
            banner.setSortOrder(resolveSortOrder(req.integer("sortOrder")));
        }
        if (req.raw().containsKey("status")) {
            banner.setStatus(normalizeStatus(req.integer("status")));
        }
        banner.setUpdatedAt(new Date());
        bannerMapper.updateById(banner);
        evictBanners();
        return WebResponseUtil.success(banner);
    }

    public Result deleteBanner(WebRequest req) {
        Integer bannerId = req.integer("bannerId");
        if (bannerId != null) {
            bannerMapper.deleteById(bannerId);
            evictBanners();
            return WebResponseUtil.success();
        }
        if (req.hasText("bannerIds")) {
            String[] ids = req.text("bannerIds").split(",");
            int deletedCount = 0;
            for (String id : ids) {
                Integer currentId = parseInteger(id);
                if (currentId == null) {
                    continue;
                }
                deletedCount += bannerMapper.deleteById(currentId);
            }
            if (deletedCount == 0) {
                return WebResponseUtil.error(1, "未找到可删除的轮播图");
            }
            evictBanners();
            return WebResponseUtil.success();
        }
        return WebResponseUtil.error(1, "轮播图ID不能为空");
    }

    public Result updateStatus(WebRequest req) {
        Integer bannerId = req.integer("bannerId");
        Integer status = req.integer("status");
        if (bannerId == null) {
            return WebResponseUtil.error(1, "轮播图ID不能为空");
        }
        if (status == null) {
            return WebResponseUtil.error(1, "状态不能为空");
        }
        MiniappBanner banner = bannerMapper.selectById(bannerId);
        if (banner == null) {
            return WebResponseUtil.error(1, "轮播图不存在");
        }
        banner.setStatus(normalizeStatus(status));
        banner.setUpdatedAt(new Date());
        bannerMapper.updateById(banner);
        evictBanners();
        return WebResponseUtil.success();
    }

    public Result sortTop(WebRequest req) {
        Integer bannerId = req.integer("bannerId");
        if (bannerId == null) {
            return WebResponseUtil.error(1, "轮播图ID不能为空");
        }
        MiniappBanner banner = bannerMapper.selectById(bannerId);
        if (banner == null) {
            return WebResponseUtil.error(1, "轮播图不存在");
        }
        banner.setSortOrder(resolveSortOrder(null));
        banner.setUpdatedAt(new Date());
        bannerMapper.updateById(banner);
        evictBanners();
        return WebResponseUtil.success();
    }

    private void evictBanners() {
        catalogService.evictBannersCache();
    }

    private String requiredTrimmed(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new RentApiException(1, message);
        }
        return value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeLinkType(String value) {
        String linkType = StringUtils.hasText(value) ? value.trim() : "none";
        if ("goods".equals(linkType) || "path".equals(linkType) || "url".equals(linkType)) {
            return linkType;
        }
        return "none";
    }

    private Integer normalizeStatus(Integer value) {
        return value != null && value > 0 ? 1 : 0;
    }

    private Integer normalizeStatus(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue > 0 ? 1 : 0;
        }
        return normalizeStatus(value);
    }

    private Integer resolveSortOrder(Integer value) {
        if (value != null) {
            return value;
        }
        long seconds = System.currentTimeMillis() / 1000L;
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
