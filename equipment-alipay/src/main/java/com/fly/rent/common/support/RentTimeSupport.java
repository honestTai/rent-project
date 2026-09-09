package com.fly.rent.common.support;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 统一处理租赁接口涉及到的时间格式转换。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public final class RentTimeSupport {

    /**
     * 时区
     */
    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private RentTimeSupport() {
    }

    /**
     * 格式化时间
     * @param epochMilli 时间戳
     * @return 格式化后的时间字符串
     */
    public static String formatDateTime(Long epochMilli) {
        if (epochMilli == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMilli).atZone(ZONE_ID).toLocalDateTime());
    }

    /**
     * 格式化日期
     * @param epochMilli 时间戳
     * @return 格式化后的日期字符串
     */
    public static String formatDate(Long epochMilli) {
        if (epochMilli == null) {
            return null;
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMilli).atZone(ZONE_ID).toLocalDate());
    }

    /**
     * 解析日期
     * @param text 日期字符串
     * @return 时间戳
     */
    public static Long parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        LocalDate date = LocalDate.parse(text, DATE_FORMATTER);
        return date.atStartOfDay(ZONE_ID).toInstant().toEpochMilli();
    }

    /**
     * 判断是否为月租
     * @param rentUnit 租期单位
     * @return 是否为月租
     */
    public static boolean isMonthRent(String rentUnit) {
        if (!StringUtils.hasText(rentUnit)) {
            return false;
        }
        String normalized = rentUnit.trim().toLowerCase();
        return "month".equals(normalized) || "monthly".equals(normalized) || "月".equals(normalized);
    }

    /**
     * 统一把租期单位转换成前端更直观的展示值。
     * @param rentUnit 租期单位
     * @return 展示值
     */
    public static String normalizeRentUnit(String rentUnit) {
        return isMonthRent(rentUnit) ? "月" : "天";
    }

    /**
     * 身份证号掩码处理
     * @param idCard 身份证号
     * @return 掩码后的身份证号
     */
    public static String maskIdCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 8) {
            return "";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 获取身份证号后四位
     * @param idCard 身份证号
     * @return 后四位
     */
    public static String tailIdCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 4) {
            return "";
        }
        return idCard.substring(idCard.length() - 4);
    }
}
