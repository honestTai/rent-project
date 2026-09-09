package com.fly.rent.support.util;

import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 时间工具类
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Component
public class TimeUtil {
    /**
     * 格式化日期输出
     *
     * @param data 日期
     * @return 格式化后的字符串 (yyyy-MM-dd)
     */
    public static String get_SFD_Time(Date data) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(data);
    }

    /**
     * 格式化日期输出
     * @param data 日期
     * @return 格式化后的字符串 (yyyy-MM-dd HH:mm:ss)
     */
    public static String get_SFD_Time1(Date data) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(data);
    }

    /**
     * 格式化时间戳
     * @param orderStart 时间戳
     * @return 格式化后的字符串 (yyyyMMddHHmmss)
     */
    public static String yyyyMMddHHmmss(Long orderStart) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf.format(orderStart);
    }

    /**
     * 获取今天的时间戳（不含时分秒）
     * @return 时间戳
     */
    public static Long getNowDay() {
        Long curr = System.currentTimeMillis();
        Long aDay = 24 * 60 * 60 * 1000L;
        Long var1 = 60 * 60 * 8 * 1000L;
        return curr - (curr % aDay) - var1;
    }

    /**
     * 功能描述: <br>
     * 〈计算日期差〉首尾也算的。
     *
     * @param start 开始时间戳
     * @param end 结束时间戳
     * @return 天数差
     */
    public static int getDiff(Long start, Long end) {
        Long day = 24 * 60 * 60 * 1000L;
        start = (start / 1000) * 1000;
        end = (end / 1000) * 1000;
        return (int) ((end - start) / day + 1);
    }

    /**
     * 字符串转日期
     * @param startTime 时间字符串
     * @return 日期对象
     */
    public static Date strToDate(String startTime) {
        if (null != startTime && startTime.length() == 13) {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            Date date;
            try {
                date = df.parse(df.format(Long.parseLong(startTime)));
            } catch (ParseException e) {
                return null;
            }
            return date;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date;
        try {
            date = sdf.parse(startTime);
        } catch (ParseException e) {
            return null;
        }
        return date;
    }

    /**
     * 获取今天的时间戳
     * @return 时间戳
     */
    public static Long getToDayStemp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        return calendar.getTimeInMillis()/1000*1000;
    }


    /**
     * 判断两个日期大小、若d1<d2 返回true 否则返回false
     *
     * @param d1 日期1
     * @param d2 日期2
     * @return 比较结果
     */
    public boolean boolTime(Date d1, Date d2) {
        String[] temp;
        temp = this.getYMD(d1).split("-");
        int date1 = new Integer(temp[0] + temp[1] + temp[2]);
        temp = this.getYMD(d2).split("-");
        int date2 = new Integer(temp[0] + temp[1] + temp[2]);
        if (date1 < date2) {
            return true;
        }
        return false;
    }

    /**
     * 获取年月日字符串
     * @param date 日期
     * @return 字符串
     */
    public String getYMD(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String str = sdf.format(date);
        return str.split(" ")[0];
    }

    /**
     * 获得n个月后的日期
     *
     * @param now 当前日期
     * @param num 月数
     * @return n个月后的日期
     */
    public Date getAfterDate(Date now, int num) {
        //创建一个日期实例
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MONTH, num);//在日历的月份上增加6个月
        return calendar.getTime();
    }

    /**
     * 将前端转过来的格式化后的日期转回Date
     *
     * @param strDate 日期字符串
     * @return 日期对象
     */
    public Date strToDateLong(String strDate) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ParsePosition pos = new ParsePosition(0);
        Date strtodate = formatter.parse(strDate, pos);
        return strtodate;
    }
}

