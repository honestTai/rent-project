package com.equipment.platform.vo;

/**
 * 系统日志文件展示对象。
 * <p>
 * 文件标识由服务端枚举生成，前端只回传 fileKey，避免自行拼接服务器路径。
 * </p>
 */
public class SystemLogFileVo {

    /**
     * 服务端生成的日志文件标识。
     */
    private String fileKey;

    /**
     * 页面展示名称。
     */
    private String fileName;

    /**
     * 日志级别或类型。
     */
    private String level;

    /**
     * 是否为归档文件。
     */
    private boolean archive;

    /**
     * 文件大小，单位字节。
     */
    private long sizeBytes;

    /**
     * 最后修改时间。
     */
    private String lastModified;

    public SystemLogFileVo() {
    }

    public SystemLogFileVo(String fileKey, String fileName, String level, boolean archive, long sizeBytes, String lastModified) {
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.level = level;
        this.archive = archive;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public boolean isArchive() {
        return archive;
    }

    public void setArchive(boolean archive) {
        this.archive = archive;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
}
