package com.common.log.controller;

import com.aop.LoginToken.UserLoginToken;
import com.common.log.service.LogService;
import com.common.log.vo.LogListVo;
import com.common.login.BaseLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用 log 表接口（访问日志、操作日志）
 * 放入 common，由依赖 common 的服务扫描注册；二手前端通过 /api/platform/log 显式走中台库，
 * 租赁旧入口继续保留 /api/log；
 * 通过请求体中的 froms 区分系统，如：无人机租赁系统、二手无人机购买系统
 */
@RestController
@RequestMapping({"/api/log", "/api/platform/log"})
public class LogController {

    @Autowired
    private LogService logService;

    @PostMapping("/requestLogList")
    @UserLoginToken
    public List<LogListVo> requestLogList(@RequestBody BaseLog query) {
        if (query == null) {
            query = new BaseLog();
        }
        return logService.getRequestLogList(query);
    }

    @PostMapping("/operationLogList")
    @UserLoginToken
    public List<LogListVo> operationLogList(@RequestBody BaseLog query) {
        if (query == null) {
            query = new BaseLog();
        }
        return logService.getOperationLogList(query);
    }

    @PostMapping("/deleteLog")
    @UserLoginToken
    public void deleteLog(@RequestBody BaseLog query) {
        logService.deleteLog(query);
    }
}
