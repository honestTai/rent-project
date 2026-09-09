package com.fly.rent.support.util;

import com.fly.rent.entity.Order;
import com.alipay.api.domain.RentInstallmentInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.fly.rent.config.AlipayRentConstants.CENT_TO_YUAN_DIVISOR;
import static com.fly.rent.config.AlipayRentConstants.DECIMAL_SCALE;
import static java.math.RoundingMode.HALF_UP;

/**
 * 订单工具类
 * 
 * @author HonestTat
 * @since 2026-03-11
 */
public class OrderUtil {
    
    private OrderUtil() {
        // 工具类，禁止实例化
    }
    
    /**
     * 时间戳转换为日期
     * @param ts 时间戳
     * @return 日期
     */
    public static Date toDate(Long ts) {
        if (ts == null) {
            return null;
        }
        if (ts < 1_000_000_000_000L) {
            return Date.from(Instant.ofEpochSecond(ts));
        } else {
            return Date.from(Instant.ofEpochMilli(ts));
        }
    }
    
    /**
     * 分转元
     * @param cent 分
     * @return 元
     */
    public static BigDecimal convertCentToYuan(Integer cent) {
        if (cent == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(cent.toString())
                .divide(CENT_TO_YUAN_DIVISOR, DECIMAL_SCALE, HALF_UP);
    }

    /**
     * 元转分
     * @param yuan 金额（元）
     * @return 金额（分）
     */
    public static int convertYuanToCent(String yuan) {
        if (yuan == null || yuan.trim().isEmpty()) {
            throw new IllegalArgumentException("金额不能为空");
        }
        try {
            return new BigDecimal(yuan.trim())
                    .multiply(CENT_TO_YUAN_DIVISOR)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("金额格式不正确: " + yuan);
        }
    }
    
    /**
     * 获取租赁分期信息
     * @param order 订单
     * @param totalYuan 总金额（元）
     * @param totalDepositPrice 总押金（元）
     * @return 分期信息列表
     */
    public static List<RentInstallmentInfo> getRentInstallmentInfos(Order order, BigDecimal totalYuan, BigDecimal totalDepositPrice) {
        List<RentInstallmentInfo> installments = new ArrayList<>();
        
        // 默认1期：在订单开始时间支付全部租金
        RentInstallmentInfo installment0 = new RentInstallmentInfo();
        installment0.setPlanPayTime(toDate(order.getOrderStart()));
        installment0.setInstallmentPrice(totalYuan.toString());
        installment0.setInstallmentNo(1L);
        installment0.setBuyoutPrice(totalDepositPrice.toString());
        installments.add(installment0);
        
        return installments;
    }
}

