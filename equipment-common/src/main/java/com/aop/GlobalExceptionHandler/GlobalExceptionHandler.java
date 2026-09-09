package com.aop.GlobalExceptionHandler;

import com.common.Entity.ReturnResult;
import com.common.log.LogEvent;
import com.common.log.LogEventPublisher;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.util.Date;

import static com.common.Constant.constant.*;

/**
 * 拦截所有接口返回
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int LOG_TYPE_OTHER = 7;

    @Resource
    private LogEventPublisher logEventPublisher;

    /**
     * 发布带完整堆栈的异常日志事件，便于在操作日志中直接看到报错位置（接口仍返回系统错误）
     */
    private void publishExceptionLog(String contentMsg, Throwable e) {
        try {
            HttpServletRequest request = null;
            if (RequestContextHolder.getRequestAttributes() != null) {
                request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            }
            String stackTrace = ExceptionUtils.getStackTrace(e);
            LogEvent event = new LogEvent(this);
            event.setLogSource(LogEvent.LogSource.LEGACY);
            event.setContent(contentMsg + "\n堆栈:\n" + stackTrace);
            event.setException(stackTrace);
            event.setResult("失败");
            event.setType(LOG_TYPE_OTHER);
            event.setDateTime(new Date());
            if (request != null) {
                event.setRequestUrl(request.getRequestURI());
                event.setRequestMethod(request.getMethod());
            }
            logEventPublisher.publish(event);
        } catch (Exception ex) {
            // 避免日志发布失败影响接口返回
        }
    }

    /**
     * 自定义的错误，不用添加日志
     *
     * @param e
     * @return
     */
    @ResponseBody
    public ReturnResult handleException(ReturnResult e) {
        return new ReturnResult(e.getCode(), e.getMsg(), e.getData());
    }

    /**
     * 空指针错误
     */
    @ExceptionHandler(value = NullPointerException.class)
    @ResponseBody
    public ReturnResult exceptionHandler(NullPointerException e) {
        publishExceptionLog("空指针", e);
        return new ReturnResult(FAIL, "空指针", e.getStackTrace()[0].getClassName() + "第" + e.getStackTrace()[0].getLineNumber() + "行");
    }

    /**
     * 时间错误
     */
    @ExceptionHandler(value = ParseException.class)
    @ResponseBody
    public ReturnResult parseException(ParseException e) {
        return new ReturnResult(FAIL, "抛出异常", e.getStackTrace()[0].getClassName() + "第" + e.getStackTrace()[0].getLineNumber() + "行");
    }

    /**
     * 其他错误，加入日志
     *
     * @param e
     * @return
     */
    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public ReturnResult exceptionHandler(Exception e) {
        String msg = e.getMessage();
        String contentMsg = (msg != null && !msg.isEmpty()) ? msg : "系统错误";
        publishExceptionLog(contentMsg, e);
        if (msg != null && (msg.equals(NO_TOKEN) || msg.equals(TOKEN_OVERDUE))) {
            return new ReturnResult(LOGIN_OVERDUE, msg, e.getStackTrace()[0].getClassName() + "第" + e.getStackTrace()[0].getLineNumber() + "行");
        } else {
            return new ReturnResult(FAIL, msg, e.getStackTrace()[0].getClassName() + "第" + e.getStackTrace()[0].getLineNumber() + "行");
        }
    }
}
