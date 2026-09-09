package com.fly.rent.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个请求建立可安全记录的 traceId，并记录不包含请求体的接口耗时。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String START_NANOS_ATTRIBUTE = TraceIdFilter.class.getName() + ".startNanos";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        long startedAt = System.nanoTime();
        request.setAttribute(START_NANOS_ATTRIBUTE, startedAt);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("request_completed method={} path={} status={} elapsedMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(String candidate) {
        if (StringUtils.hasText(candidate) && SAFE_TRACE_ID.matcher(candidate.trim()).matches()) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
