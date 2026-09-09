package com.common.log.service;

import com.common.login.BaseLog;
import com.common.log.entity.Log;
import com.common.log.vo.LogListVo;

import java.util.List;

/**
 * 通用 log 表服务（按 froms 区分系统：无人机租赁系统 / 二手无人机购买系统）
 */
public interface LogService {

    void addLogInfo(Log log);

    List<LogListVo> getRequestLogList(BaseLog query);

    List<LogListVo> getOperationLogList(BaseLog query);

    void deleteLog(BaseLog query);

    void deleteAllLog();
}
