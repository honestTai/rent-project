package com.fly.rent.web.support;

import com.fly.rent.entity.Order;

/**
 * Web 兼容层订单操作类型枚举。
 * 定义后台订单相关操作对应的日志类型（operType）、中文描述（description），以及部分操作执行后的
 * 支付宝状态（alipayStatus）与内部状态（orderStatus），用于操作日志与状态回写。
 */
public enum WebOrderOperation {

    UPDATE_BY_NO("SYNC", "按订单号更新订单"),
    SYNC_TRADE_AUTH("SYNC", "同步交易授权"),
    SYNC_DEPOSIT("SYNC", "同步押金信息"),
    AFTERSALE_SYNC("AFTERSALE_SYNC", "同步支付宝售后单"),
    DEDUCT_DEPOSIT("DEDUCT_DEPOSIT", "扣减订单押金"),
    UPDATE_COURIER("SHIP", "更新物流信息"),
    REFUND_AND_CLOSE("REFUND", "退款并关闭订单", "CLOSED", 6),
    REFUND_APPROVE("REFUND", "同意退款", "CLOSED", 6),
    REFUND_REJECT("REFUND", "拒绝退款", "PAID", null),
    RETURN_UNFREEZE("RETURN", "归还押金并完结订单", "FINISHED", 7),
    UPDATE_REMARK("REMARK", "更新订单备注"),
    FIRST_TRADE("PAY", "创建首笔支付记录"),
    CLOSE_CREDIT("SYNC", "关闭信用授权"),
    COMPLETE_ORDER("COMPLETE", "完结订单", "FINISHED", 7),
    MERCHANT_CONFIRM_APPROVE("MERCHANT_CONFIRM", "商家审核通过", "APPROVED", null),
    MERCHANT_CONFIRM_REJECT("MERCHANT_CONFIRM", "商家审核拒绝", "CLOSED", null),
    IDENTITY_PHOTO_REQUIRED("IDENTITY_PHOTO", "要求用户上传身份证照片"),
    IDENTITY_PHOTO_APPROVE("IDENTITY_PHOTO", "身份证照片审核通过", "APPROVED", null),
    IDENTITY_PHOTO_REJECT("IDENTITY_PHOTO", "身份证照片审核不通过"),
    RENT_SEND("SHIP", "租流订单发货"),
    RENT_CONFIRM_RECEIVE("CONFIRM_RECEIVE", "确认收货"),
    RENT_COMPLETE("COMPLETE", "租流订单完结", "FINISHED", 7),
    RENT_REFUND_APPROVE("REFUND", "租流退款同意", "CLOSED", null),
    RENT_REFUND_REJECT("REFUND", "租流退款拒绝", "PAID", null),
    CLOSE_RENT_ORDER("CLOSE", "关闭租流订单", "CLOSED", null),
    /** 系统：确认收货后把租赁合同 PDF 回传到支付宝租赁交易订单 */
    CONTRACT_ALIPAY_SYNC("CONTRACT_SYNC", "回传租赁合同到支付宝"),
    /** 小程序：用户申请取消/退款 */
    MINIAPP_REFUND_APPLY("REFUND", "用户申请取消/退款"),
    /** 小程序：用户确认收货 */
    MINIAPP_CONFIRM_RECEIVE("CONFIRM_RECEIVE", "用户确认收货"),
    /** 小程序：用户归还发货 */
    MINIAPP_RETURN_SEND("SHIP", "用户归还发货"),
    /** 支付宝回调：订单状态同步 */
    CALLBACK_STATUS("SYNC", "支付宝订单状态回调");

    /** 操作类型，对应 _order_oper_log.oper_type */
    private final String operType;
    /** 中文操作描述 */
    private final String description;
    /** 操作后支付宝侧状态（可选） */
    private final String alipayStatus;
    /** 操作后内部订单状态（可选） */
    private final Integer orderStatus;

    /**
     * 无状态变更的操作类型构造。
     *
     * @param operType    操作类型标识
     * @param description 中文描述
     */
    WebOrderOperation(String operType, String description) {
        this(operType, description, null, null);
    }

    /**
     * 带状态变更的操作类型构造。
     *
     * @param operType     操作类型标识
     * @param description  中文描述
     * @param alipayStatus 操作后支付宝状态（可选）
     * @param orderStatus  操作后内部订单状态（可选）
     */
    WebOrderOperation(String operType, String description, String alipayStatus, Integer orderStatus) {
        this.operType = operType;
        this.description = description;
        this.alipayStatus = alipayStatus;
        this.orderStatus = orderStatus;
    }

    /** 获取操作类型标识。 */
    public String getOperType() {
        return operType;
    }

    /** 获取中文操作描述。 */
    public String getDescription() {
        return description;
    }

    /** 获取操作后支付宝状态，无变更时返回 null。 */
    public String getAlipayStatus() {
        return alipayStatus;
    }

    /** 获取操作后内部订单状态，无变更时返回 null。 */
    public Integer getOrderStatus() {
        return orderStatus;
    }

    /**
     * 将本操作定义的状态变更应用到订单实体上。
     * 仅在 alipayStatus / orderStatus 非 null 时设置对应字段。
     *
     * @param order 待更新的订单
     */
    public void applyState(Order order) {
        if (alipayStatus != null) {
            order.setAlipayStatus(alipayStatus);
        }
        if (orderStatus != null) {
            order.setStatus(orderStatus);
        }
    }
}
