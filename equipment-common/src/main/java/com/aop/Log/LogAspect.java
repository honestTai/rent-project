package com.aop.Log;

import com.alibaba.fastjson.JSON;
import com.common.Entity.ReturnResult;
import com.common.Util.UserInfo.RedisUserInfo;
import com.common.log.LogEvent;
import com.common.log.LogEventPublisher;
import eu.bitwalker.useragentutils.UserAgent;
import cn.hutool.system.SystemUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;

import static com.common.Constant.constant.*;

/**
 * 日志切面实现（观察者模式重构）
 * 拦截带有 @Log 注解的方法，采集请求信息、执行结果及用户信息，
 * 并通过 LogEventPublisher 发布日志事件，实现日志记录与业务逻辑的解耦。
 * 
 * 主要功能：
 * 1. 自动解析请求上下文（IP、浏览器、操作系统）。
 * 2. 捕获方法执行结果（正常返回/异常抛出）。
 * 3. 兼容多种返回值类型（ReturnResult 封装体或直接对象）。
 * 4. 异步发布日志事件，由监听器（LegacyLogEventListener）处理持久化。
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    @Resource
    private LogEventPublisher logEventPublisher;

    @Resource
    private HttpServletRequest request;

    @Resource
    private RedisUserInfo redisUserInfo;

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 定义切点：拦截所有标记了 @Log 注解的方法
     */
    @Pointcut("@annotation(com.aop.Log.Log)")
    public void logPointCut() {
    }

    /**
     * 环绕通知：方法执行前抓老数据，执行后抓新数据，统一发布日志事件
     */
    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        com.aop.Log.Log operation = method.getAnnotation(com.aop.Log.Log.class);
        Object[] args = pjp.getArgs();

        // 方法执行前：按注解查询老数据
        String oldDataJson = "";
        if (operation != null && operation.needOldData() && operation.mapperClass() != Void.class && args != null && args.length > 0) {
            try {
                Object oldEntity = loadOldData(operation, args[0]);
                if (oldEntity != null) {
                    oldDataJson = truncate(JSON.toJSONString(oldEntity), 10000);
                }
            } catch (Exception e) {
                logger.warn("查询老数据失败: {}", e.getMessage());
            }
        }

        Object result;
        try {
            result = pjp.proceed();
            try {
                publishLog(pjp, result, null, oldDataJson);
            } catch (Exception logEx) {
                logger.error("操作日志记录失败，不影响主流程", logEx);
            }
            return result;
        } catch (Throwable ex) {
            try {
                publishLog(pjp, null, ex instanceof Exception ? (Exception) ex : new Exception(ex), oldDataJson);
            } catch (Exception logEx) {
                logger.error("异常日志记录失败，不影响主流程", logEx);
            }
            throw ex;
        }
    }

    /**
     * 根据注解从 Mapper 查询老数据：优先用 idGetter 取 id 再 selectById，否则用首参调 mapper 的单参方法（如 deviceById(ViewDevice)）
     */
    private Object loadOldData(com.aop.Log.Log operation, Object firstArg) throws Exception {
        if (firstArg == null) return null;
        Object mapper = applicationContext.getBean(operation.mapperClass());
        String idGetter = operation.idGetter();
        if (idGetter != null && !idGetter.isEmpty()) {
            Method getter = firstArg.getClass().getMethod(idGetter);
            Object id = getter.invoke(firstArg);
            if (id == null) return null;
            // Mapper 上找单参且参数类型兼容 id 的方法（如 selectById(Integer)、orderById(Integer)）
            for (Method m : operation.mapperClass().getMethods()) {
                if (m.getParameterCount() != 1 || m.getReturnType() == void.class) continue;
                if (!m.getParameterTypes()[0].isAssignableFrom(id.getClass())) continue;
                return m.invoke(mapper, id);
            }
        } else {
            // 用首参调 mapper 的单参方法，如 deviceById(ViewDevice)、orderById(ViewOrder)
            Class<?> argClass = firstArg.getClass();
            for (Method m : operation.mapperClass().getMethods()) {
                if (m.getParameterCount() != 1 || m.getReturnType() == void.class) continue;
                if (!m.getParameterTypes()[0].isAssignableFrom(argClass)) continue;
                return m.invoke(mapper, firstArg);
            }
        }
        return null;
    }

    /**
     * 构建并发布日志事件
     * @param joinPoint 切入点
     * @param result 执行结果（可能为 null）
     * @param exception 异常信息（可能为 null）
     * @param oldDataJson 已在 @Around 中查询好的老数据 JSON，可为 ""
     */
    private void publishLog(ProceedingJoinPoint joinPoint, Object result, Exception exception, String oldDataJson) {
        LogEvent event = new LogEvent(this);
        event.setLogSource(LogEvent.LogSource.LEGACY);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        com.aop.Log.Log operation = method.getAnnotation(com.aop.Log.Log.class);

        if (operation != null) {
            event.setContent(operation.value().isEmpty() ? "未定义" : operation.value());
            event.setType(operation.logType().getCode());
            event.setFromSystem(operation.logFrom().getName());
        }

        String className = joinPoint.getTarget().getClass().getName();
        event.setRequestMethod(className + "." + method.getName());
        event.setRequestUrl(request != null ? request.getRequestURI() : "");
        event.setDateTime(new Date());

        try {
            event.setParams(args != null && args.length > 0 ? truncate(JSON.toJSONString(args), 2000) : "");
        } catch (Exception e) {
            event.setParams("");
        }

        event.setOldData(oldDataJson != null ? oldDataJson : "");

        // 新数据：优先用返回体中的 data，为空则用请求体（首参），保证老数据/新数据都能上去
        try {
            if (exception == null) {
                Object data = (result instanceof ReturnResult) ? ((ReturnResult<?>) result).getData() : result;
                if (data != null) {
                    event.setNewData(truncate(JSON.toJSONString(data), 10000));
                } else if (args != null && args.length > 0 && args[0] != null) {
                    event.setNewData(truncate(JSON.toJSONString(args[0]), 10000));
                } else {
                    event.setNewData("");
                }
            } else {
                event.setNewData("");
            }
        } catch (Exception e) {
            event.setNewData("");
        }

        // 3. 处理操作结果 - 兼容 ReturnResult 和普通返回值
        if (exception != null) {
            event.setResult(ERR_LOG);
            event.setLogMsg(exception.getMessage());
        } else if (result instanceof ReturnResult) {
            ReturnResult<?> returnResult = (ReturnResult<?>) result;
            event.setResult(returnResult.getCode() != null && returnResult.getCode().equals(SUCCESS) ? RIGHT_LOG : ERR_LOG);
            event.setLogMsg(returnResult.getMsg());
            Object data = returnResult.getData();
            event.setResultData(data != null ? JSON.toJSONString(data) : "");
        } else {
            event.setResult(RIGHT_LOG);
            event.setLogMsg("操作成功");
            event.setResultData(result != null ? JSON.toJSONString(result) : null);
        }

        // 4. 获取当前用户信息（从 Token 或 Redis 中解析）
        if (request != null) {
            try {
                if (request.getAttribute("token") != null) {
                    event.setUserId(String.valueOf(redisUserInfo.userId()));
                } else if (request.getHeader("token") != null) {
                    event.setUserId(String.valueOf(redisUserInfo.userIdLoginNow()));
                }
            } catch (Exception e) {
                event.setUserId(null);
            }
        }

        // 5. 解析客户端环境（IP、浏览器、操作系统、操作地点）
        String clientIp = resolveClientIp(request);
        event.setUserIp(clientIp != null ? clientIp : SystemUtil.getHostInfo().getAddress());
        event.setUserAddress(clientIp);
        String uaHeader = request != null ? request.getHeader("User-Agent") : null;
        if (uaHeader != null && !uaHeader.isEmpty()) {
            try {
                final UserAgent userAgent = UserAgent.parseUserAgentString(uaHeader);
                event.setUserBrowser(userAgent.getBrowser().getName());
                event.setUserSystem(userAgent.getOperatingSystem().getName());
            } catch (Exception ignored) {
            }
        }

        // 6. 发布事件
        logEventPublisher.publish(event);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /** 截断字符串，避免日志字段过长 */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
