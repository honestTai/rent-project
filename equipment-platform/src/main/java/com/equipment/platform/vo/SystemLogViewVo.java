package com.equipment.platform.vo;

import java.util.List;

/**
 * 系统日志查询结果。
 */
public class SystemLogViewVo {

    /**
     * 系统编码。
     */
    private String systemCode;

    /**
     * 日志级别。
     */
    private String level;

    /**
     * 本次读取的日志文件标识。
     */
    private String fileKey;

    /**
     * 页面展示文件名。
     */
    private String fileName;

    /**
     * 是否读取归档日志。
     */
    private boolean archive;

    /**
     * 被读取的日志文件路径。
     */
    private String filePath;

    /**
     * 文件是否存在。
     */
    private boolean exists;

    /**
     * 当前返回的日志行。
     */
    private List<SystemLogLineVo> lines;

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
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

    public boolean isArchive() {
        return archive;
    }

    public void setArchive(boolean archive) {
        this.archive = archive;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public List<SystemLogLineVo> getLines() {
        return lines;
    }

    public void setLines(List<SystemLogLineVo> lines) {
        this.lines = lines;
    }
}
