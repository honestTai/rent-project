package com.aop.Log;

/**
 * 日志状态枚举
 * author sexTzt
 * time 2020-08-15
 */
public enum LogFromEnum {

    /**
     * 0
     */
    WRJ_RENT(0,"无人机租赁系统"),

    /**
     * 1
     */
    SECOND_HAND(1,"二手无人机购买系统");

    private Integer code;
    private String name;

    LogFromEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
