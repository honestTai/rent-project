package com.fly.rent.capability.withhold;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.capability.billing.InstallmentPaymentSyncService;
import com.fly.rent.config.RedisClient;
import com.fly.rent.entity.InstallmentBill;
import com.fly.rent.entity.WithholdAgreement;
import com.fly.rent.mapper.InstallmentBillMapper;
import com.fly.rent.mapper.WithholdAgreementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithholdDeductScheduler {

    private final InstallmentBillMapper billMapper;
    private final WithholdAgreementMapper agreementMapper;
    private final CapabilityConfigService configService;
    private final AlipayWithholdAdapter alipayWithholdAdapter;
    private final InstallmentPaymentSyncService paymentSyncService;
    private final RedisClient redisClient;

    @Scheduled(cron = "0 */10 * * * ?")
    public void executeDueDeductions() {
        if (!"true".equalsIgnoreCase(configService.value("withhold.scheduler.enabled", "true"))) {
            return;
        }
        if (!withinDeductWindow()) {
            return;
        }
        List<InstallmentBill> bills = billMapper.selectList(new QueryWrapper<InstallmentBill>()
                .in("status", Arrays.asList(CapabilityConstants.BILL_WAIT_PAY, CapabilityConstants.BILL_OVERDUE))
                .le("due_date", new Date())
                .last("LIMIT 50"));
        if (bills == null || bills.isEmpty()) {
            return;
        }
        for (InstallmentBill bill : bills) {
            executeOne(bill);
        }
    }

    private void executeOne(InstallmentBill bill) {
        String lockKey = "withhold_deduct:" + bill.getId();
        if (!redisClient.tryLock(lockKey, String.valueOf(bill.getId()), 5, TimeUnit.MINUTES)) {
            return;
        }
        try {
            WithholdAgreement agreement = agreementMapper.selectOne(new QueryWrapper<WithholdAgreement>()
                    .eq("order_id", bill.getOrderId())
                    .eq("status", CapabilityConstants.WITHHOLD_SIGNED)
                    .orderByDesc("id")
                    .last("LIMIT 1"));
            if (agreement == null) {
                return;
            }
            if (!StringUtils.hasText(bill.getOutTradeNo()) || !bill.getOutTradeNo().startsWith("EMSDED")) {
                bill.setOutTradeNo("EMSDED" + bill.getId());
            }
            bill.setStatus(CapabilityConstants.BILL_PAYING);
            bill.setUpdatedAt(new Date());
            billMapper.updateById(bill);
            String tradeNo = alipayWithholdAdapter.executeDeduct(bill, agreement);
            paymentSyncService.markBillPaid(bill, tradeNo, new Date());
        } catch (Exception ex) {
            bill.setStatus(CapabilityConstants.BILL_OVERDUE);
            bill.setLastError(ex.getMessage());
            bill.setUpdatedAt(new Date());
            billMapper.updateById(bill);
            log.warn("账单自动扣款失败: billId={}, error={}", bill.getId(), ex.getMessage());
        } finally {
            redisClient.releaseLock(lockKey, String.valueOf(bill.getId()));
        }
    }

    private boolean withinDeductWindow() {
        int startHour = configService.intValue("withhold.scheduler.start-hour", 7);
        int endHour = configService.intValue("withhold.scheduler.end-hour", 22);
        int hour = LocalTime.now(ZoneId.of("Asia/Shanghai")).getHour();
        return hour >= startHour && hour < endHour;
    }

}
