package com.fly.rent.miniapp.upload;

import com.common.oss.OssFileStorage;
import com.common.oss.OssUploadResult;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.Result;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序专用 OSS 上传入口。
 * 路径挂在 /api/rent/v1/miniapp 下，由小程序 JWT 拦截器校验 token，避免复用后台 /api/web/oss/upload 导致鉴权失败。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rent/v1/miniapp/oss")
public class MiniappOssUploadController {

    private final OssFileStorage ossFileStorage;
    private final RentCurrentUserService currentUserService;

    /**
     * 上传小程序图片文件，返回完整 URL 和 OSS 相对路径。
     *
     * @param path 业务目录，如 return/、identity-card/
     * @param file 小程序上传的图片文件
     * @return code/msg/data 小程序响应信封
     */
    @PostMapping("/upload")
    public Result upload(
            @RequestParam(value = "path", required = false, defaultValue = "") String path,
            @RequestParam("file") MultipartFile file
    ) {
        currentUserService.requireUserUuid();
        try {
            OssUploadResult result = ossFileStorage.uploadWithMeta(file, path);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("url", result.getUrl());
            data.put("path", result.getRelativePath());
            return ResultUtil.success(data);
        } catch (Exception e) {
            return ResultUtil.error(-1, e.getMessage() != null ? e.getMessage() : "上传失败");
        }
    }
}
