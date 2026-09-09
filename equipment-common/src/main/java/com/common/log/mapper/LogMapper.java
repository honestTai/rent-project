package com.common.log.mapper;

import com.common.login.BaseLog;
import com.common.log.entity.Log;
import com.common.log.vo.LogListVo;

import java.util.List;

/**
 * 通用 log 表 Mapper（按 froms 区分租赁/二手等系统）
 */
public interface LogMapper {

    int addLogInfo(Log log);

    int userAccessCount();

    /** 访问日志：type=0 或 1 */
    List<LogListVo> getRequestLogList(BaseLog query);

    /** 操作日志：type 非 0、1 */
    List<LogListVo> getOperationLogList(BaseLog query);

    int deleteLog(BaseLog query);

    int deleteAllLog();
}
