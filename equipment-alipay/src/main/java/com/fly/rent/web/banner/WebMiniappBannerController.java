package com.fly.rent.web.banner;

import com.fly.rent.entity.Result;
import com.fly.rent.web.support.AbstractWebController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台小程序轮播图配置入口。
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebMiniappBannerController extends AbstractWebController {

    private final WebMiniappBannerService bannerService;

    @PostMapping("/miniapp-banners/page")
    public Result page(@RequestBody(required = false) Map<String, Object> body) {
        return bannerService.pageBanners(request(body));
    }

    @PostMapping("/miniapp-banners/create")
    public Result create(@RequestBody(required = false) Map<String, Object> body) {
        return bannerService.createBanner(request(body));
    }

    @PostMapping("/miniapp-banners/update")
    public Result update(@RequestBody(required = false) Map<String, Object> body) {
        return bannerService.updateBanner(request(body));
    }

    @PostMapping("/miniapp-banners/delete")
    public Result delete(@RequestBody(required = false) Map<String, Object> body) {
        return bannerService.deleteBanner(request(body));
    }

    @PostMapping("/miniapp-banners/status/update")
    public Result updateStatus(@RequestBody(required = false) Map<String, Object> body) {
        return bannerService.updateStatus(request(body));
    }

    @PostMapping("/miniapp-banners/sort-top")
    public Result sortTop(@RequestBody(required = false) Map<String, Object> body) {
        return bannerService.sortTop(request(body));
    }
}
