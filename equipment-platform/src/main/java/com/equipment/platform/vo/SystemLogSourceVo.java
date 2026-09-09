package com.equipment.platform.vo;

import java.util.List;

/**
 * 系统日志来源展示对象。
 */
public class SystemLogSourceVo {

    /**
     * 中台系统编码。
     */
    private String systemCode;

    /**
     * 页面展示名称。
     */
    private String systemName;

    /**
     * Docker 部署下的日志目录名。
     */
    private String logDirectory;

    /**
     * 当前存在的日志级别。
     */
    private List<String> levels;

    /**
     * 服务端枚举到的当前和归档日志文件。
     */
    private List<SystemLogFileVo> files;

    public SystemLogSourceVo() {
    }

    public SystemLogSourceVo(String systemCode, String systemName, String logDirectory, List<String> levels) {
        this.systemCode = systemCode;
        this.systemName = systemName;
        this.logDirectory = logDirectory;
        this.levels = levels;
    }

    public SystemLogSourceVo(String systemCode, String systemName, String logDirectory, List<String> levels, List<SystemLogFileVo> files) {
        this.systemCode = systemCode;
        this.systemName = systemName;
        this.logDirectory = logDirectory;
        this.levels = levels;
        this.files = files;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getLogDirectory() {
        return logDirectory;
    }

    public void setLogDirectory(String logDirectory) {
        this.logDirectory = logDirectory;
    }

    public List<String> getLevels() {
        return levels;
    }

    public void setLevels(List<String> levels) {
        this.levels = levels;
    }

    public List<SystemLogFileVo> getFiles() {
        return files;
    }

    public void setFiles(List<SystemLogFileVo> files) {
        this.files = files;
    }
}
