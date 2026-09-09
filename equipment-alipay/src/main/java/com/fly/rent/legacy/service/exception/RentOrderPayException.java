package com.fly.rent.legacy.service.exception;

import java.util.Locale;

/**
 * 租赁订单支付异常。
 *
 * 保留支付宝原始 subCode/subMsg，同时生成后台运营能直接理解的处理提示。
 */
public class RentOrderPayException extends IllegalArgumentException {

    private final String subCode;
    private final String subMsg;
    private final String operatorMessage;

    public RentOrderPayException(String subCode, String subMsg) {
        this(subCode, subMsg, buildOperatorMessage(subCode, subMsg));
    }

    private RentOrderPayException(String subCode, String subMsg, String operatorMessage) {
        super(operatorMessage);
        this.subCode = subCode;
        this.subMsg = subMsg;
        this.operatorMessage = operatorMessage;
    }

    public String getSubCode() {
        return subCode;
    }

    public String getSubMsg() {
        return subMsg;
    }

    public String getOperatorMessage() {
        return operatorMessage;
    }

    private static String buildOperatorMessage(String subCode, String subMsg) {
        String text = mergeText(subCode, subMsg);
        String advice = resolveOperatorAdvice(text);
        StringBuilder message = new StringBuilder(advice);
        if (hasText(subCode)) {
            message.append(" 错误码：").append(subCode.trim()).append("。");
        }
        if (hasText(subMsg)) {
            message.append("支付宝返回：").append(subMsg.trim()).append("。");
        }
        return message.toString();
    }

    private static String resolveOperatorAdvice(String text) {
        if (containsAny(text,
                "NO_PAYMENT_INSTRUMENTS_AVAILABLE",
                "没有可用的支付工具",
                "没有可用支付工具",
                "无可用支付工具")) {
            return "用户当前没有可用于本次自动扣款的支付宝支付工具，请联系用户补充可用银行卡/余额，或改走用户主动赔付、线下处理。";
        }
        if (containsAny(text,
                "BUYER_BALANCE_NOT_ENOUGH",
                "BUYER_BANKCARD_BALANCE_NOT_ENOUGH",
                "余额不足",
                "银行卡余额不足")) {
            return "用户支付渠道余额不足，请联系用户充值或更换银行卡后重试；仍失败时改走用户主动赔付、线下处理。";
        }
        if (containsAny(text,
                "AUTH_AMOUNT_NOT_ENOUGH",
                "授权资金不足",
                "预授权金额不足",
                "可用授权金额不足")) {
            return "预授权剩余可扣金额不足，不能继续自动扣押金，请核对支付宝资金授权详情，超出部分走用户主动赔付或线下处理。";
        }
        if (containsAny(text,
                "AUTH_ORDER_HAS_CLOSED",
                "AUTH_ORDER_CLOSED",
                "授权订单已关闭",
                "预授权已关闭",
                "预授权已解冻",
                "冻结已解冻")) {
            return "预授权已关闭或解冻，不能继续自动扣押金，请改走用户主动赔付或线下处理。";
        }
        if (containsAny(text,
                "OUT_TRADE_NO",
                "外部交易号已存在",
                "重复提交",
                "DUPLICATE")) {
            return "本次赔付支付请求号已被支付宝占用，请刷新扣减记录后重试，系统会自动换新的支付请求号。";
        }
        if (containsAny(text,
                "RISK",
                "风控",
                "账户限制",
                "支付受限")) {
            return "支付宝风控或账户限制拦截了本次扣款，请让用户在支付宝侧处理账户或更换付款方式，商家侧不要反复重试。";
        }
        if (containsAny(text,
                "PAYMENT_FAIL",
                "扣款失败")) {
            // PAYMENT_FAIL 是支付宝扣款阶段的兜底码，真实子原因（可扣额度不足/用户支付能力不足/授权状态异常）
            // 需要结合本次请求的 biz_content 与 response.body 日志核对，不能直接当成报文 bug。
            return "支付宝扣款失败（PAYMENT_FAIL 为兜底码，常见于可扣押金/预授权额度不足或用户支付能力不足）。"
                    + "请先核对该订单支付宝侧实际可扣额度与本次扣款金额，确认是否超出剩余押金；"
                    + "确属无法自动扣款时，改走用户主动赔付或线下处理，并可撤销本笔售后。";
        }
        return "支付宝扣款失败，请按错误码和原始返回核对；无法自动扣款时改走用户主动赔付或线下处理。";
    }

    private static boolean containsAny(String text, String... keywords) {
        if (!hasText(text)) {
            return false;
        }
        String upperText = text.toUpperCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (hasText(keyword) && upperText.contains(keyword.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String mergeText(String subCode, String subMsg) {
        StringBuilder text = new StringBuilder();
        if (hasText(subCode)) {
            text.append(subCode.trim());
        }
        if (hasText(subMsg)) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(subMsg.trim());
        }
        return text.toString();
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
