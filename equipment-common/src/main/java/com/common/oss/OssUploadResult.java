package com.common.oss;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 上传结果：完整 URL + 与参考项目一致的相对路径（不含 uploads/ 前缀） */
@Getter
@AllArgsConstructor
public class OssUploadResult {
    private final String url;
    private final String relativePath;
}
