package com.common.config;

import com.common.Encryption.Aes;
import com.common.Encryption.Md5;
import com.common.Entity.ReturnResult;
import com.common.Util.Redis.RedisClient;
import com.common.login.LoginParam;
import com.common.login.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.common.Constant.constant.SUCCESS;

/**
 * LogViewer 访问鉴权过滤器
 * 三层防护：IP白名单 → 登录失败限流(Redis持久化) → 账号密码认证 + Session超时
 * 所有路径从 log-viewer.url-mapping 配置中动态获取
 *
 * Redis key 说明（可在 Redis 中直接查看/删除来解锁）：
 *   logviewer:fail:{ip}  → 失败次数 (Integer)
 *   logviewer:lock:{ip}  → 锁定截止时间戳 (Long)
 */
@Component
public class LogViewerAuthFilter implements Filter {

    private static final String SESSION_KEY = "LOG_VIEWER_AUTHENTICATED";
    private static final String SESSION_USER = "LOG_VIEWER_USER";
    private static final String REDIS_FAIL_PREFIX = "logviewer:fail:";
    private static final String REDIS_LOCK_PREFIX = "logviewer:lock:";

    @Autowired
    private LoginService loginService;

    @Autowired
    private LogViewerProperties properties;

    @Autowired
    private RedisClient redisClient;

    private String basePath() {
        return properties.getBasePath();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();
        String base = basePath();

        if (!path.startsWith(base)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(req);

        // === 第一层：IP 白名单 ===
        if (!isIpAllowed(clientIp)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // === 第二层：检查该 IP 是否被锁定 ===
        if (isIpLocked(clientIp)) {
            renderLockedPage(resp, getRemainLockMinutes(clientIp));
            return;
        }

        HttpSession session = req.getSession(true);

        if ("POST".equalsIgnoreCase(req.getMethod()) && path.equals(base + "/auth")) {
            handleLogin(req, resp, session, clientIp);
            return;
        }

        if (path.equals(base + "/logout")) {
            session.removeAttribute(SESSION_KEY);
            session.removeAttribute(SESSION_USER);
            resp.sendRedirect(base);
            return;
        }

        // === 第三层：Session 认证检查 ===
        if (Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            chain.doFilter(request, response);
            return;
        }

        renderLoginPage(resp, null);
    }

    // ==================== IP 白名单 ====================

    private boolean isIpAllowed(String clientIp) {
        List<String> allowedIps = properties.getAllowedIps();
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }
        for (String rule : allowedIps) {
            if (rule.contains("/")) {
                if (isInCidr(clientIp, rule)) {
                    return true;
                }
            } else {
                if (rule.trim().equals(clientIp)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress network = InetAddress.getByName(parts[0].trim());
            int prefixLength = Integer.parseInt(parts[1].trim());

            byte[] networkBytes = network.getAddress();
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();

            if (networkBytes.length != ipBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != ipBytes[i]) {
                    return false;
                }
            }

            if (remainBits > 0 && fullBytes < networkBytes.length) {
                int mask = (0xFF << (8 - remainBits)) & 0xFF;
                if ((networkBytes[fullBytes] & mask) != (ipBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    // ==================== 登录失败限流（Redis 持久化） ====================

    private boolean isIpLocked(String ip) {
        Long lockUntil = redisClient.getCacheObject(REDIS_LOCK_PREFIX + ip);
        if (lockUntil == null) {
            return false;
        }
        if (System.currentTimeMillis() < lockUntil) {
            return true;
        }
        redisClient.deleteObject(REDIS_LOCK_PREFIX + ip);
        redisClient.deleteObject(REDIS_FAIL_PREFIX + ip);
        return false;
    }

    private long getRemainLockMinutes(String ip) {
        Long lockUntil = redisClient.getCacheObject(REDIS_LOCK_PREFIX + ip);
        if (lockUntil == null) {
            return 0;
        }
        return Math.max(1, (lockUntil - System.currentTimeMillis()) / 60000 + 1);
    }

    private void recordFailure(String ip) {
        Integer failCount = redisClient.getCacheObject(REDIS_FAIL_PREFIX + ip);
        int newCount = (failCount == null ? 0 : failCount) + 1;
        int lockMinutes = properties.getLockDurationMinutes();
        redisClient.setCacheObject(REDIS_FAIL_PREFIX + ip, newCount, lockMinutes, TimeUnit.MINUTES);

        if (newCount >= properties.getMaxLoginAttempts()) {
            long lockUntil = System.currentTimeMillis() + (long) lockMinutes * 60 * 1000;
            redisClient.setCacheObject(REDIS_LOCK_PREFIX + ip, lockUntil, lockMinutes, TimeUnit.MINUTES);
        }
    }

    private void clearFailure(String ip) {
        redisClient.deleteObject(REDIS_FAIL_PREFIX + ip);
        redisClient.deleteObject(REDIS_LOCK_PREFIX + ip);
    }

    // ==================== 登录处理 ====================

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp,
                             HttpSession session, String clientIp) throws IOException {
        if (isIpLocked(clientIp)) {
            renderLockedPage(resp, getRemainLockMinutes(clientIp));
            return;
        }

        String userName = req.getParameter("userName");
        String userPwd = req.getParameter("userPwd");

        if (userName == null || userName.trim().isEmpty() || userPwd == null || userPwd.trim().isEmpty()) {
            renderLoginPage(resp, "请输入账号和密码");
            return;
        }

        try {
            String encryptedPwd = Aes.encrypt(Md5.md5String(userPwd.trim()));
            ReturnResult<?> result = loginService.login(
                    new LoginParam(userName.trim(), encryptedPwd), req);

            if (SUCCESS.equals(result.getCode())) {
                clearFailure(clientIp);
                session.setMaxInactiveInterval(properties.getSessionTimeout() * 60);
                session.setAttribute(SESSION_KEY, true);
                session.setAttribute(SESSION_USER, userName.trim());
                resp.sendRedirect(basePath());
            } else {
                recordFailure(clientIp);
                Integer failCount = redisClient.getCacheObject(REDIS_FAIL_PREFIX + clientIp);
                int remaining = properties.getMaxLoginAttempts()
                        - (failCount == null ? 0 : failCount);
                String msg = result.getMsg();
                if (remaining > 0) {
                    msg += "（剩余 " + remaining + " 次尝试机会）";
                }
                renderLoginPage(resp, msg);
            }
        } catch (Exception e) {
            recordFailure(clientIp);
            renderLoginPage(resp, "登录失败：" + e.getMessage());
        }
    }

    // ==================== 获取真实客户端 IP ====================

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    // ==================== 页面渲染 ====================

    private void renderLockedPage(HttpServletResponse resp, long remainMinutes) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        PrintWriter out = resp.getWriter();
        out.write("<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>");
        out.write("<title>访问已锁定</title>");
        out.write("<style>");
        out.write("*{margin:0;padding:0;box-sizing:border-box}");
        out.write("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;");
        out.write("background:linear-gradient(135deg,#0f172a 0%,#1e293b 100%);");
        out.write("display:flex;justify-content:center;align-items:center;min-height:100vh;color:#e2e8f0}");
        out.write(".card{background:#1e293b;border:1px solid #7f1d1d;border-radius:16px;padding:40px;width:420px;max-width:90vw;");
        out.write("box-shadow:0 25px 50px rgba(0,0,0,.5);text-align:center}");
        out.write(".icon{font-size:48px;margin-bottom:20px}");
        out.write("h2{margin-bottom:12px;font-size:22px;color:#fca5a5}");
        out.write("p{color:#94a3b8;font-size:14px;line-height:1.6}");
        out.write(".time{color:#f87171;font-weight:600;font-size:18px;margin:16px 0}");
        out.write("</style></head><body>");
        out.write("<div class='card'>");
        out.write("<div class='icon'>&#128274;</div>");
        out.write("<h2>访问已锁定</h2>");
        out.write("<p>由于多次登录失败，该 IP 已被临时锁定</p>");
        out.write("<div class='time'>剩余锁定时间：约 " + remainMinutes + " 分钟</div>");
        out.write("<p>请稍后再试，或联系管理员解锁</p>");
        out.write("</div></body></html>");
        out.flush();
    }

    private void renderLoginPage(HttpServletResponse resp, String errorMsg) throws IOException {
        String base = basePath();
        resp.setContentType("text/html;charset=UTF-8");
        if (errorMsg != null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        PrintWriter out = resp.getWriter();
        out.write("<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>");
        out.write("<title>日志查看器 - 登录</title>");
        out.write("<style>");
        out.write("*{margin:0;padding:0;box-sizing:border-box}");
        out.write("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;");
        out.write("background:linear-gradient(135deg,#0f172a 0%,#1e293b 100%);");
        out.write("display:flex;justify-content:center;align-items:center;min-height:100vh;color:#e2e8f0}");
        out.write(".card{background:#1e293b;border:1px solid #334155;border-radius:16px;padding:40px;width:400px;max-width:90vw;");
        out.write("box-shadow:0 25px 50px rgba(0,0,0,.5)}");
        out.write(".logo{text-align:center;margin-bottom:24px;font-size:40px}");
        out.write("h2{text-align:center;margin-bottom:6px;font-size:22px;color:#f1f5f9;font-weight:600}");
        out.write(".subtitle{text-align:center;color:#64748b;margin-bottom:28px;font-size:13px}");
        out.write(".form-group{margin-bottom:18px}");
        out.write("label{display:block;margin-bottom:6px;font-size:13px;color:#94a3b8;font-weight:500}");
        out.write("input{width:100%;padding:11px 14px;border:1px solid #334155;border-radius:8px;");
        out.write("background:#0f172a;color:#e2e8f0;font-size:14px;outline:none;transition:border-color .2s}");
        out.write("input:focus{border-color:#3b82f6;box-shadow:0 0 0 3px rgba(59,130,246,.15)}");
        out.write("input::placeholder{color:#475569}");
        out.write("button{width:100%;padding:12px;background:#3b82f6;color:#fff;border:none;border-radius:8px;");
        out.write("font-size:15px;font-weight:500;cursor:pointer;margin-top:6px;transition:background .2s}");
        out.write("button:hover{background:#2563eb}");
        out.write("button:active{background:#1d4ed8}");
        out.write(".error{background:rgba(239,68,68,.12);color:#fca5a5;padding:10px 14px;border-radius:8px;");
        out.write("margin-bottom:18px;font-size:13px;text-align:center;border:1px solid rgba(239,68,68,.2)}");
        out.write("</style></head><body>");
        out.write("<div class='card'>");
        out.write("<div class='logo'>&#128220;</div>");
        out.write("<h2>LogViewer</h2>");
        out.write("<p class='subtitle'>请使用系统账号登录以查看日志</p>");
        if (errorMsg != null) {
            out.write("<div class='error'>" + errorMsg + "</div>");
        }
        out.write("<form method='POST' action='" + base + "/auth'>");
        out.write("<div class='form-group'><label for='userName'>账号</label>");
        out.write("<input type='text' id='userName' name='userName' placeholder='请输入登录账号' required autofocus></div>");
        out.write("<div class='form-group'><label for='userPwd'>密码</label>");
        out.write("<input type='password' id='userPwd' name='userPwd' placeholder='请输入登录密码' required></div>");
        out.write("<button type='submit'>登 录</button>");
        out.write("</form></div></body></html>");
        out.flush();
    }
}
