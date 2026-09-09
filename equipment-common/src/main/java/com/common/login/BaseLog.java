package com.common.login;

import com.common.Entity.Page;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 访问参数接受类,基类（公共DTO，两个业务模块共享）
 */
@Data
public class BaseLog extends Page {
    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 状态
     */
    private Integer type;

    /**
     * 日志内容
     */
    private String logMsg;

    /**
     * 操作用户 ID。
     */
    private Integer userId;

    /**
     * 操作用户名。
     */
    private String userName;

    /**
     * 时间区间
     */
    private List<Date> dateList;

    /**
     * 结果
     */
    private String result;

    /**
     * 批量删除
     */
    private List<Integer> logIdList;

    /**
     * 来源系统
     */
    private String froms;
}
