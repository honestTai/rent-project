package com.common.log.service.impl;

import com.common.login.BaseLog;
import com.common.log.entity.Log;
import com.common.log.mapper.LogMapper;
import com.common.log.service.LogService;
import com.common.log.vo.LogListVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogServiceImpl implements LogService {

    @Autowired
    private LogMapper logMapper;

    @Override
    public void addLogInfo(Log log) {
        if (log != null) {
            logMapper.addLogInfo(log);
        }
    }

    @Override
    public List<LogListVo> getRequestLogList(BaseLog query) {
        return logMapper.getRequestLogList(query);
    }

    @Override
    public List<LogListVo> getOperationLogList(BaseLog query) {
        return logMapper.getOperationLogList(query);
    }

    @Override
    public void deleteLog(BaseLog query) {
        if (query != null && query.getLogIdList() != null && !query.getLogIdList().isEmpty()) {
            logMapper.deleteLog(query);
        }
    }

    @Override
    public void deleteAllLog() {
        logMapper.deleteAllLog();
    }
}
