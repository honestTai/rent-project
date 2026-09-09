package com.common.log;

import lombok.Data;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

/**
 * 统一日志事件（观察者模式）
 * 两套日志系统（LogAspect 和 SystemLogAspect）通过发布此事件来解耦日志记录
 * 各监听器根据 source 类型决定是否处理
 */
@Data
public class LogEvent extends ApplicationEvent {

    /**
     * 日志来源
     */
    public enum LogSource {
        /** 无人机租赁系统的 @Log 切面 */
        LEGACY,
        /** 二手设备系统的 @SystemLog 切面 */
        SYSTEM
    }

    private LogSource logSource;
    private String content;
    private Integer type;
    private String requestUrl;
    private String requestMethod;
    private String result;
    private String exception;
    private String userId;
    /** 操作用户名，写日志时直接保存，不做关联查询 */
    private String userName;
    private String params;
    private String oldData;
    private String newData;
    private String resultData;
    private String logMsg;
    private String fromSystem;
    private String userIp;
    private String userAddress;
    private String userBrowser;
    private String userSystem;
    private Date dateTime;

    public LogEvent(Object source) {
        super(source);
        this.dateTime = new Date();
    }
}
