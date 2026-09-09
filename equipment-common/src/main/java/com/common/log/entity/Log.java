package com.common.log.entity;

import lombok.Data;

import java.util.Date;

/**
 * 通用 log 表实体（访问日志、操作日志统一写入此表，通过 froms 区分系统）
 */
@Data
public class Log {
    private Integer id;
    private Date dateTime;
    private String content;
    private Integer userId;
    private Integer type;
    private String userIp;
    private String requestMethod;
    private String result;
    private String logMsg;
    private String userAddress;
    private String userBrowser;
    private String userSystem;
    private String requestUrl;
    /** 请求参数（与 second 操作日志一致） */
    private String param;
    /** 老数据 */
    private String oldData;
    /** 新数据 */
    private String newData;
    private String froms;
    private String resultData;
}
