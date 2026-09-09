package com.equipment.platform.service;

import com.common.Util.UserInfo.UserContextHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 系统日志中心访问控制服务。
 * <p>
 * 登录态由网关统一校验，本服务只做日志中心自己的可选白名单：用户白名单和 IP 白名单。
 * 配置为空时表示不额外限制，继续沿用网关后台管理员权限。
 * </p>
 */
@Service
public class SystemLogAuthService {

    private static final String SYSTEM_CODE = "platform";
    private static final String CONFIG_ALLOWED_USERS = "system-log.allowed-users";
    private static final String CONFIG_ALLOWED_IPS = "system-log.allowed-ips";

    private final PlatformConfigService configService;

    /**
     * 创建系统日志访问控制服务。
     *
     * @param configService 中台配置服务
     */
    public SystemLogAuthService(PlatformConfigService configService) {
        this.configService = configService;
    }

    /**
     * 校验当前请求是否允许查看系统日志。
     *
     * @param request 当前请求
     */
    public void checkAccess(HttpServletRequest request) {
        String userName = request.getHeader(UserContextHeaders.USER_NAME);
        if (!StringUtils.hasText(userName)) {
            throw new IllegalArgumentException("未获取到登录用户，请从网关访问中台日志中心");
        }
        String allowedUsers = configService.getRuntimeValue(SYSTEM_CODE, CONFIG_ALLOWED_USERS);
        if (StringUtils.hasText(allowedUsers) && !containsToken(allowedUsers, userName)) {
            throw new IllegalArgumentException("当前用户不在系统日志白名单中");
        }

        String clientIp = getClientIp(request);
        String allowedIps = configService.getRuntimeValue(SYSTEM_CODE, CONFIG_ALLOWED_IPS);
        if (StringUtils.hasText(allowedIps) && !isIpAllowed(clientIp, allowedIps)) {
            throw new IllegalArgumentException("当前 IP 不在系统日志白名单中");
        }
    }

    private boolean containsToken(String csv, String expected) {
        String[] values = csv.split(",");
        for (String value : values) {
            if (expected.equals(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpAllowed(String clientIp, String rules) {
        String[] values = rules.split(",");
        for (String rule : values) {
            String trimmedRule = rule.trim();
            if (!StringUtils.hasText(trimmedRule)) {
                continue;
            }
            if (trimmedRule.contains("/")) {
                if (isInCidr(clientIp, trimmedRule)) {
                    return true;
                }
            } else if (trimmedRule.equals(clientIp)) {
                return true;
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
                return (networkBytes[fullBytes] & mask) == (ipBytes[fullBytes] & mask);
            }
            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
