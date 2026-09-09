package com.aop.Log;

/**
 * 日志状态枚举
 * author sexTzt
 * time 2020-08-15
 */
public enum LogEnum {

    /**
     * 0
     */
    APP_LOGIN(0,"APP登录"),

    /**
     * 1
     */
    PC_LOGIN(1,"PC登录"),

    /**
     * 2
     */
    ADD(2,"添加"),

    /**
     * 3
     */
    UPDATE(3,"修改"),

    /**
     * 4
     */
    DELETE(4,"删除"),

    /**
     * 5
     */
    VIEW(5,"查看"),

    /**
     * 6
     */
    OTHER(6,"其他");

    private Integer code;
    private String name;

    LogEnum(Integer code, String name) {
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
