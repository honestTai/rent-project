package com.common.oss;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地 uploads 目录文件存储，用于客户服务器通过本地域名直接展示图片。
 */
@Slf4j
@RequiredArgsConstructor
public class LocalOssFileStorage implements OssFileStorage {

    private final OssProperties ossProperties;

    @Override
    public String upload(MultipartFile file, String directory) {
        return uploadWithMeta(file, directory).getUrl();
    }

    @Override
    public String upload(InputStream inputStream, String originalFilename, String directory) {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream 不能为空");
        }
        String relative = OssUploadNaming.relativePath(directory, originalFilename);
        save(inputStream, relative);
        String url = ossProperties.buildLocalPublicUrl(relative);
        log.info("本地图片上传成功 relative={} url={}", relative, url);
        return url;
    }

    @Override
    public OssUploadResult uploadWithMeta(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }
        String relative = OssUploadNaming.relativePath(directory, file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            save(inputStream, relative);
        } catch (Exception e) {
            throw new RuntimeException("本地图片上传失败: " + e.getMessage(), e);
        }
        String url = ossProperties.buildLocalPublicUrl(relative);
        log.info("本地图片上传成功 relative={} url={}", relative, url);
        return new OssUploadResult(url, relative);
    }

    private void save(InputStream inputStream, String relativePath) {
        try {
            Path root = Paths.get(ossProperties.getLocalRootPath()).toAbsolutePath().normalize();
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("非法文件路径");
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("写入本地文件失败: " + e.getMessage(), e);
        }
    }
}
