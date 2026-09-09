package com.fly.rent.support.util;

import com.alipay.api.domain.RentInstallmentInfo;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.NoUseException;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Order;
import com.fly.rent.legacy.dto.apilyRentUtil.OrderRentParams;
import com.fly.rent.legacy.dto.apilyRentUtil.RentOrderCreateParam;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.StagePayPlanInfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分期计划工具，统一处理商品可选期数、金额拆分和支付宝租赁分期结构。
 */
public final class RentInstallmentPlanSupport {

    public static final int MIN_PERIOD = 1;
    public static final int MAX_PERIOD = 24;

    private RentInstallmentPlanSupport() {
    }

    public static int resolveRequestedPeriod(RentOrderCreateParam param, Attr attr) {
        Integer requested = null;
        if (param != null) {
            requested = firstPositive(param.getInstallmentCount(), param.getPeriodTotal());
            OrderRentParams rentParams = param.getOrderRentParams();
            if (requested == null && rentParams != null) {
                requested = firstPositive(rentParams.getInstallmentCount(), rentParams.getPeriodTotal());
            }
        }
        return resolveRequestedPeriod(requested, attr);
    }

    public static int resolveRequestedPeriod(Integer requested, Attr attr) {
        int period = requested == null || requested <= 0 ? MIN_PERIOD : requested;
        if (period < MIN_PERIOD || period > MAX_PERIOD) {
            throw new NoUseException("分期期数必须在1-24之间");
        }
        if (period == MIN_PERIOD) {
            return MIN_PERIOD;
        }
        if (attr == null || attr.getInstallmentEnabled() == null || attr.getInstallmentEnabled() != 1) {
            throw new NoUseException("该规格未开启分期");
        }
        if (!availablePaymentPeriods(attr).contains(period)) {
            throw new NoUseException("该规格不支持" + period + "期");
        }
        return period;
    }

    public static String normalizePeriodCsv(String rawValue, Integer enabled) {
        if (enabled == null || enabled != 1) {
            return String.valueOf(MIN_PERIOD);
        }
        List<Integer> periods = parsePeriods(rawValue);
        if (!periods.contains(MIN_PERIOD)) {
            periods.add(MIN_PERIOD);
        }
        Collections.sort(periods);
        return join(periods);
    }

    public static List<Integer> availablePaymentPeriods(Attr attr) {
        if (attr == null || attr.getInstallmentEnabled() == null || attr.getInstallmentEnabled() != 1) {
            return Collections.singletonList(MIN_PERIOD);
        }
        List<Integer> periods = parsePeriods(attr.getInstallmentPeriods());
        if (!periods.contains(MIN_PERIOD)) {
            periods.add(MIN_PERIOD);
        }
        Collections.sort(periods);
        return periods;
    }

    public static List<Integer> splitAmountCents(Integer totalAmount, int periodTotal) {
        int safeTotal = totalAmount == null ? 0 : Math.max(totalAmount, 0);
        int safePeriods = Math.max(MIN_PERIOD, Math.min(periodTotal, MAX_PERIOD));
        List<Integer> result = new ArrayList<>();
        int base = safeTotal / safePeriods;
        int remainder = safeTotal % safePeriods;
        for (int i = 1; i <= safePeriods; i++) {
            int amount = base;
            if (i == safePeriods) {
                amount += remainder;
            }
            result.add(amount);
        }
        return result;
    }

    public static int calculateTotalRentAmount(Integer dailyAmount, Integer quantity, Integer duration) {
        int safeDailyAmount = dailyAmount == null ? 0 : Math.max(dailyAmount, 0);
        int safeQuantity = quantity == null || quantity <= 0 ? 1 : quantity;
        int safeDuration = duration == null || duration <= 0 ? 1 : duration;
        long total = (long) safeDailyAmount * safeQuantity * safeDuration;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public static List<StagePayPlanInfo> buildStagePayPlanInfos(int totalAmount, int periodTotal, Date firstPayTime, Integer billingCycle) {
        List<Integer> amounts = splitAmountCents(totalAmount, periodTotal);
        List<StagePayPlanInfo> result = new ArrayList<>();
        Date safeFirstPayTime = firstPayTime == null ? new Date(System.currentTimeMillis() + 10 * 60 * 1000L) : firstPayTime;
        for (int i = 0; i < amounts.size(); i++) {
            BigDecimal amountYuan = OrderUtil.convertCentToYuan(amounts.get(i));
            StagePayPlanInfo payPlanInfo = new StagePayPlanInfo();
            payPlanInfo.planPayNo = i + 1;
            payPlanInfo.planPayTime = TimeUtil.get_SFD_Time1(addCycle(safeFirstPayTime, i, billingCycle));
            payPlanInfo.originalPrice = amountYuan.toString();
            payPlanInfo.discountedPrice = AlipayRentConstants.ZERO_PRICE;
            payPlanInfo.planPayPrice = amountYuan.toString();
            result.add(payPlanInfo);
        }
        return result;
    }

    public static List<RentInstallmentInfo> buildRentInstallmentInfos(Order order) {
        return buildRentInstallmentInfos(order, OrderUtil.convertCentToYuan(order == null ? 0 : order.getOrderDeposit()).toString());
    }

    public static List<RentInstallmentInfo> buildRentInstallmentInfos(Order order, String buyoutPrice) {
        int periodTotal = order == null || order.getOrderTotalRentPeriods() == null
                ? MIN_PERIOD
                : order.getOrderTotalRentPeriods();
        periodTotal = Math.max(MIN_PERIOD, Math.min(periodTotal, MAX_PERIOD));
        List<Integer> amounts = splitAmountCents(order == null ? 0 : order.getOrderTotal(), periodTotal);
        // buyoutPrice 始终是 > 0 的合法金额（由调用方保证，最小 1 元兜底），无需判空
        String safeBuyoutPrice = (buyoutPrice == null || buyoutPrice.trim().isEmpty())
                ? AlipayRentConstants.ZERO_PRICE
                : buyoutPrice;
        Date firstPayTime = resolveFirstPayTime(order);
        List<RentInstallmentInfo> installments = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            RentInstallmentInfo installment = new RentInstallmentInfo();
            installment.setPlanPayTime(addCycle(firstPayTime, i, order == null ? null : order.getOrderTradeType()));
            installment.setInstallmentPrice(OrderUtil.convertCentToYuan(amounts.get(i)).toString());
            installment.setInstallmentNo((long) i + 1L);
            installment.setBuyoutPrice(safeBuyoutPrice);
            installments.add(installment);
        }
        return installments;
    }

    public static void applyOrderPeriodFields(Order order, int periodTotal) {
        if (order == null) {
            return;
        }
        int safePeriodTotal = Math.max(MIN_PERIOD, Math.min(periodTotal, MAX_PERIOD));
        List<Integer> amounts = splitAmountCents(order.getOrderTotal(), safePeriodTotal);
        order.setOrderTotalRentPeriods(safePeriodTotal);
        order.setOrderRentPeriods(1);
        order.setOrderFirstAmount(amounts.isEmpty() ? 0 : amounts.get(0));
        order.setOrderEveryAmount(amounts.isEmpty() ? 0 : amounts.get(0));
        order.setOrderEndAmount(amounts.isEmpty() ? 0 : amounts.get(amounts.size() - 1));
    }

    private static Integer firstPositive(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private static List<Integer> parsePeriods(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return new ArrayList<>(Collections.singletonList(MIN_PERIOD));
        }
        Set<Integer> result = new LinkedHashSet<>();
        for (String item : Arrays.asList(rawValue.split(","))) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            try {
                int period = Integer.parseInt(item.trim());
                if (period >= MIN_PERIOD && period <= MAX_PERIOD) {
                    result.add(period);
                }
            } catch (NumberFormatException ignored) {
                // 忽略历史脏值。
            }
        }
        if (result.isEmpty()) {
            result.add(MIN_PERIOD);
        }
        return new ArrayList<>(result);
    }

    private static String join(List<Integer> periods) {
        StringBuilder builder = new StringBuilder();
        for (Integer period : periods) {
            if (period == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(period);
        }
        return builder.length() == 0 ? String.valueOf(MIN_PERIOD) : builder.toString();
    }

    private static Date resolveFirstPayTime(Order order) {
        if (order != null && order.getOrderStart() != null && order.getOrderStart() > 0) {
            return OrderUtil.toDate(order.getOrderStart());
        }
        return new Date(System.currentTimeMillis() + 10 * 60 * 1000L);
    }

    private static Date addCycle(Date baseDate, int offset, Integer billingCycle) {
        if (offset <= 0) {
            return baseDate;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseDate);
        int cycle = billingCycle == null ? 2 : billingCycle;
        if (cycle == 3) {
            calendar.add(Calendar.YEAR, offset);
        } else if (cycle == 1) {
            calendar.add(Calendar.DATE, offset);
        } else {
            calendar.add(Calendar.MONTH, offset);
        }
        return calendar.getTime();
    }
}
