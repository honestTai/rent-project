package com.common.log.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 日志列表 VO（含 left join user 的 userName）
 */
@Data
public class LogListVo {
    private Integer id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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
    private String param;
    private String oldData;
    private String newData;
    private String froms;
    private String resultData;
    private String userName;
}
