package com.common.oss;

import org.apache.commons.lang3.RandomUtils;
import org.springframework.util.StringUtils;

/**
 * 与参考项目 apilyapp 一致的文件命名规则。
 * <ul>
 *   <li>{@link #nextOrderNo()} 对应 {@code CommonUtil.getNo()}：当前毫秒时间戳 + 两位 0~9 随机数</li>
 *   <li>{@link #fileExtension(String)} 对应 {@code FileUtil.getFix}：从原始文件名取后缀（含点）</li>
 * </ul>
 */
public final class OssUploadNaming {

    private OssUploadNaming() {
    }

    /** 与 apilyapp CommonUtil.getNo() 一致 */
    public static String nextOrderNo() {
        return System.currentTimeMillis() + String.valueOf(RandomUtils.nextInt(0, 10))
                + String.valueOf(RandomUtils.nextInt(0, 10));
    }

    /** 与 apilyapp FileUtil.getFix 一致；无后缀时返回空串 */
    public static String fileExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }

    /**
     * 业务相对路径：path + orderNo + ext（不含 uploads/ 前缀），与 UtilController 返回的 filePath 一致。
     */
    public static String relativePath(String pathParam, String originalFilename) {
        String dir = normalizePath(pathParam);
        return dir + nextOrderNo() + fileExtension(originalFilename);
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String p = path.trim().replace("\\", "/");
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (!p.isEmpty() && !p.endsWith("/")) {
            p = p + "/";
        }
        return p;
    }
}
