package com.common.oss;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 按中台配置选择图片上传方式。
 */
public class ConfigurableOssFileStorage implements OssFileStorage {

    private final OssProperties ossProperties;
    private final OssFileStorage aliyunStorage;
    private final OssFileStorage localStorage;

    public ConfigurableOssFileStorage(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.aliyunStorage = new AliyunOssFileStorage(ossProperties);
        this.localStorage = new LocalOssFileStorage(ossProperties);
    }

    @Override
    public String upload(MultipartFile file, String directory) {
        return delegate().upload(file, directory);
    }

    @Override
    public String upload(InputStream inputStream, String originalFilename, String directory) {
        return delegate().upload(inputStream, originalFilename, directory);
    }

    @Override
    public OssUploadResult uploadWithMeta(MultipartFile file, String directory) {
        return delegate().uploadWithMeta(file, directory);
    }

    private OssFileStorage delegate() {
        String storageType = ossProperties.getStorageType();
        if ("local".equals(storageType)) {
            return localStorage;
        }
        if ("aliyun".equals(storageType) || "oss".equals(storageType)) {
            return aliyunStorage;
        }
        throw new IllegalStateException("不支持的图片上传方式: " + storageType + "，请在中台配置 oss.storage-type 为 aliyun 或 local");
    }
}
