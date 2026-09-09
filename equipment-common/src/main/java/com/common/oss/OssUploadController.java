package com.common.oss;

import com.aop.LoginToken.UserLoginToken;
import com.common.Entity.ReturnResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.common.Constant.constant.FAIL;
import static com.common.Constant.constant.SUCCESS;

/**
 * 全项目唯一文件上传入口（与 apilyapp UtilController.multiUpload 同一套 OSS 规则）。
 * <p>
 * <b>统一参数（multipart）：</b>{@code file} 必填；{@code path} 可选，业务目录如 {@code images/}、{@code good/}，与参考项目一致。
 * <p>
 * <b>统一成功 data：</b>{@code url} 完整访问地址；{@code path} 不含 {@code uploads/} 的相对路径。
 * <p>
 * <b>URL：</b>默认 {@code POST /api/oss/upload}；alipay（经网关 /api/web）配置
 * {@code equipment.oss.api-base=/api/web/oss} 即 {@code POST /api/web/oss/upload}。
 * <p>
 * 鉴权：local-auth 服务校验 {@code token}；alipay 上 {@code /api/web/oss/**} 走网关用户头（与 /api/web/** 一致）。
 */
@RestController
@RequestMapping("${equipment.oss.api-base:/api/oss}")
@CrossOrigin
public class OssUploadController {

    private final OssFileStorage ossFileStorage;

    public OssUploadController(OssFileStorage ossFileStorage) {
        this.ossFileStorage = ossFileStorage;
    }

    @UserLoginToken(required = true)
    @PostMapping("/upload")
    public ReturnResult<Map<String, String>> upload(
            @RequestParam(value = "path", required = false, defaultValue = "") String path,
            @RequestParam("file") MultipartFile file) {
        try {
            OssUploadResult r = ossFileStorage.uploadWithMeta(file, path);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("url", r.getUrl());
            data.put("path", r.getRelativePath());
            return new ReturnResult<>(SUCCESS, "上传成功", data);
        } catch (Exception e) {
            return new ReturnResult<>(FAIL, e.getMessage() != null ? e.getMessage() : "上传失败", null);
        }
    }
}
