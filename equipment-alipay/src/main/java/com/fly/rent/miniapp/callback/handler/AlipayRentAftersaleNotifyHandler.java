package com.fly.rent.miniapp.callback.handler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fly.rent.aftersale.model.RentAftersaleNotifyContext;
import com.fly.rent.aftersale.service.RentDepositDeductService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.miniapp.callback.AlipayNotifyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝售后通知处理器。
 *
 * 这个处理器只做“通知报文 -> 领域上下文”的装配，
 * 真正的账务更新交给扣减领域服务，保证通知层和业务层职责分离。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayRentAftersaleNotifyHandler implements AlipayNotifyHandler {

    private final RentDepositDeductService rentDepositDeductService;

    @Override
    public boolean supports(Map<String, String> params) {
        return AlipayRentConstants.AFTERSALE_NOTIFY_METHOD.equals(params.get("msg_method"));
    }

    @Override
    public String handle(Map<String, String> params) {
        String bizContentStr = params.get("biz_content");
        if (bizContentStr == null || bizContentStr.isEmpty()) {
            log.warn("售后通知缺少 biz_content");
            return "failure";
        }

        JSONObject bizContent = JSONUtil.parseObj(bizContentStr);
        RentAftersaleNotifyContext context = new RentAftersaleNotifyContext();
        context.setNotifyId(params.get("notify_id"));
        context.setMsgMethod(params.get("msg_method"));
        context.setPayloadJson(bizContentStr);
        context.setOrderId(bizContent.getStr("order_id"));
        context.setOutOrderId(bizContent.getStr("out_order_id"));
        context.setAftersaleType(bizContent.getStr("aftersale_type"));
        context.setAftersaleId(bizContent.getStr("aftersale_id"));
        context.setOutAftersaleId(bizContent.getStr("out_aftersale_id"));
        context.setAftersaleStatus(bizContent.getStr("aftersale_status"));
        context.setSourceType(bizContent.getStr("source_type"));
        context.setOperationType(bizContent.getStr("operation_type"));
        context.setNeedOperation(bizContent.getStr("need_operation"));
        context.setBuyerId(bizContent.getStr("buyer_id"));
        context.setBuyerOpenId(bizContent.getStr("buyer_open_id"));

        rentDepositDeductService.handleAftersaleNotify(context);
        return "success";
    }
}
