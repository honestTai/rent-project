package com.common.oss;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 通用文件存储（阿里云 OSS）上传接口。
 */
public interface OssFileStorage {

    /**
     * 上传 MultipartFile，返回可访问的完整 URL。
     *
     * @param file  上传文件
     * @param directory 目录前缀，如 "images/" 或 "goods/"，可为空
     */
    String upload(MultipartFile file, String directory);

    /**
     * 上传字节流。
     *
     * @param inputStream 文件流
     * @param originalFilename 原始文件名（用于取扩展名），可为空
     * @param directory 目录前缀
     */
    String upload(InputStream inputStream, String originalFilename, String directory);

    /**
     * 一次生成文件名并上传，返回 URL 与相对路径（与 apilyapp filePath 一致）。
     */
    OssUploadResult uploadWithMeta(MultipartFile file, String directory);
}
