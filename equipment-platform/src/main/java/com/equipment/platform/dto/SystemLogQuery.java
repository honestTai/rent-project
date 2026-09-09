package com.equipment.platform.dto;

/**
 * 系统日志查询参数。
 * <p>
 * 该参数只描述服务端白名单日志文件的读取条件，不承载任何文件路径，避免前端传入路径造成任意文件读取风险。
 * </p>
 */
public class SystemLogQuery {

    /**
     * 系统编码：platform、alipay、gateway、eureka。
     */
    private String systemCode;

    /**
     * 日志级别：info、warn、error。
     */
    private String level;

    /**
     * 服务端枚举出的日志文件标识，支持当前文件和归档文件。
     */
    private String fileKey;

    /**
     * 关键字，服务端按行过滤。
     */
    private String keyword;

    /**
     * 读取最后多少行。
     */
    private Integer tail;

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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getTail() {
        return tail;
    }

    public void setTail(Integer tail) {
        this.tail = tail;
    }
}
