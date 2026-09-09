package com.common.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 阿里云 OSS 上传，与参考项目 apilyapp {@code UtilController.multiUpload} 一致：
 * <pre>
 *   objectKey = "uploads/" + path + getNo() + getFix(文件名)
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunOssFileStorage implements OssFileStorage {

    private final OssProperties ossProperties;

    @Override
    public String upload(MultipartFile file, String path) {
        return uploadWithMeta(file, path).getUrl();
    }

    @Override
    public OssUploadResult uploadWithMeta(MultipartFile file, String path) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }
        String relative = OssUploadNaming.relativePath(path, file.getOriginalFilename());
        String objectKey = "uploads/" + relative;
        if (!ossProperties.isConfigured()) {
            throw new IllegalStateException("OSS 未配置完整");
        }
        OSS client = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
        try {
            try (java.io.InputStream in = file.getInputStream()) {
                client.putObject(ossProperties.getBucketName(), objectKey, in);
            }
        } catch (Exception e) {
            throw new RuntimeException("OSS 上传失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
        String url = ossProperties.buildAliyunPublicUrl(relative);
        log.info("OSS 上传成功 objectKey={} url={}", objectKey, url);
        return new OssUploadResult(url, relative);
    }

    @Override
    public String upload(InputStream inputStream, String originalFilename, String path) {
        if (!ossProperties.isConfigured()) {
            throw new IllegalStateException("OSS 未配置完整");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream 不能为空");
        }

        String relative = OssUploadNaming.relativePath(path, originalFilename);
        String objectKey = "uploads/" + relative;

        OSS client = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
        try {
            client.putObject(ossProperties.getBucketName(), objectKey, inputStream);
        } finally {
            client.shutdown();
        }

        String url = ossProperties.buildAliyunPublicUrl(relative);
        log.info("OSS 上传成功 objectKey={} url={}", objectKey, url);
        return url;
    }
}
