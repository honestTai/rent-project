package com.equipment.platform.vo;

/**
 * 系统日志行展示对象。
 */
public class SystemLogLineVo {

    /**
     * 行号，仅在本次读取结果内递增。
     */
    private int index;

    /**
     * 日志内容。
     */
    private String content;

    /**
     * 当前行推断出的日志级别。
     */
    private String level;

    public SystemLogLineVo() {
    }

    public SystemLogLineVo(int index, String content, String level) {
        this.index = index;
        this.content = content;
        this.level = level;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}
