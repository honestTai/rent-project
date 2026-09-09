package com.fly.rent.config;

import com.fly.rent.common.order.RentOrderStatus;

import java.math.BigDecimal;

/**
 * 支付宝租赁相关常量
 * 
 * @author HonestTat
 * @since 2026-03-11
 */
public final class AlipayRentConstants {
    
    /**
     * 私有构造函数，防止实例化
     */
    private AlipayRentConstants() {
        // 工具类，禁止实例化
    }
    
    // ==================== 支付相关常量 ====================
    /**
     * 支付方式：JSAPI
     */
    public static final String PAY_METHOD_JSAPI = "JSAPI";
    /**
     * 支付方式：预授权转支付
     */
    public static final String PAY_METHOD_PRE_AUTH = "PRE_AUTH";
    /**
     * 支付项目类型：租赁
     */
    public static final String PAY_ITEM_TYPE_RENT = "RENT";
    /**
     * 支付项目类型：买断。
     */
    public static final String PAY_ITEM_TYPE_BUYOUT = "BUYOUT";
    /**
     * 减免金额
     */
    public static final String REDUCTION_AMOUNT = "0.00";
    // ==================== 订单类型常量 ====================
    /**
     * 订单类型：租赁
     */
    public static final String ORDER_TYPE_RENT = "RENT";
    /**
     * 订单类型：续租
     */
    public static final String ORDER_TYPE_RELET = "RELET";
    /**
     * 归还类型：线下
     */
    public static final String RENT_RETURN_TYPE_OFFLINE = "off";
    
    // ==================== 订单状态常量 ====================
    /**
     * 状态：已支付
     */
    public static final String STATUS_PAID = RentOrderStatus.PAID.getCode();
    /**
     * 状态：已创建
     */
    public static final String STATUS_CREATED = RentOrderStatus.CREATED.getCode();
    /**
     * 状态：已审核
     */
    public static final String STATUS_APPROVED = RentOrderStatus.APPROVED.getCode();
    /**
     * 状态：已关闭
     */
    public static final String STATUS_CLOSED = RentOrderStatus.CLOSED.getCode();
    /**
     * 状态：已发货
     */
    public static final String STATUS_DELIVERED = RentOrderStatus.DELIVERED.getCode();
    /**
     * 状态：已寄回
     */
    public static final String STATUS_RETURN_DELIVERED = RentOrderStatus.RETURN_DELIVERED.getCode();
    /**
     * 状态：已收货
     */
    public static final String STATUS_RECEIVED = RentOrderStatus.RECEIVED.getCode();
    /**
     * 状态：已收到归还
     */
    public static final String STATUS_RETURN_RECEIVED = RentOrderStatus.RETURN_RECEIVED.getCode();
    /**
     * 状态：已完成
     */
    public static final String STATUS_FINISHED = RentOrderStatus.FINISHED.getCode();
    /**
     * 状态：退款申请中
     */
    // 常量名继续兼容旧代码，常量值改成文档要求的正确状态。
    public static final String STATUS_PENDING_CANCLE = RentOrderStatus.PENDING_CANCEL.getCode();
    /**
     * 状态：已签约
     */
    public static final String STATUS_SIGNED = RentOrderStatus.SIGNED.getCode();

    // ==================== 售后常量 ====================
    public static final String AFTERSALE_TYPE_COMPENSATION = "COMPENSATION";
    public static final String AFTERSALE_TYPE_ORDER_CANCEL = "ORDER_CANCEL";
    public static final String AFTERSALE_OPERATION_MERCHANT_APPROVE = "MERCHANT_APPROVE";
    public static final String AFTERSALE_OPERATION_MERCHANT_REJECT = "MERCHANT_REJECT";
    public static final String AFTERSALE_OPERATION_APPROVE_WITH_USER_PAY = "APPROVE_WITH_USER_PAY";
    public static final String AFTERSALE_OPERATION_USER_CANCEL_APPLY = "USER_CANCEL_APPLY";
    public static final String AFTERSALE_OPERATION_AFTERSALE_FINISH = "AFTERSALE_FINISH";
    /**
     * 本地私域售后专用动作：先发起押金转支付，支付成功后再自动完结售后。
     * 该值不会直接传给支付宝，仅用于后台按钮和服务层路由。
     */
    public static final String AFTERSALE_OPERATION_PAY_COMPENSATION = "PAY_COMPENSATION";
    public static final String AFTERSALE_OPERATION_USER_APPLY = "USER_APPLY";
    public static final String AFTERSALE_OPERATION_ORDER_CANCEL = "ORDER_CANCEL";
    public static final String AFTERSALE_FEE_TYPE_INDEMNITY = "INDEMNITY";
    public static final String AFTERSALE_FEE_TYPE_LATE_FEE = "LATE_FEE";
    public static final String AFTERSALE_REASON_ITEM_DAMAGED = "ITEM_DAMAGED";
    public static final String AFTERSALE_REASON_ITEM_REPAIR = "ITEM_REPAIR";
    public static final String AFTERSALE_REASON_ITEM_LOST = "ITEM_LOST";
    public static final String AFTERSALE_REASON_ITEM_DEPRECIATION = "ITEM_DEPRECIATION";
    public static final String AFTERSALE_REASON_RETURN_EARLY = "RETURN_EARLY";
    public static final String AFTERSALE_REASON_RETURN_OVERDUE = "RETURN_OVERDUE";
    public static final String AFTERSALE_REASON_GOODS_DELIVERED = "GOODS_DELIVERED";
    public static final String AFTERSALE_REASON_BUYER_AGREED = "BUYER_AGREED";
    public static final String AFTERSALE_REASON_OTHER = "OTHER";
    public static final String AFTERSALE_NOTIFY_METHOD = "alipay.commerce.rent.order.aftersale.notify";
    public static final String ORDER_PAY_NOTIFY_METHOD = "alipay.commerce.rent.order.pay.notify";
    /** 支付宝小程序商品状态变更通知方法名。 */
    public static final String ITEM_STATUS_NOTIFY_METHOD = "alipay.open.app.item.status.notify";
    /** 私域售后来源：商家自行创建售后单。 */
    public static final String AFTERSALE_SOURCE_MERCHANT = "MERCHANT";
    /** 公域售后来源：芝麻租赁阵地发起。 */
    public static final String AFTERSALE_SOURCE_ZHIMA_RENT = "ZHIMA_RENT";
    public static final String NOTIFY_TYPE_FUND_AUTH_FREEZE = "fund_auth_freeze";
    public static final String AFTERSALE_STATUS_APPROVING = "APPROVING";
    public static final String AFTERSALE_STATUS_SUCCESS = "SUCCESS";
    public static final String AFTERSALE_STATUS_FAIL = "FAIL";
    
    
    // ==================== 错误码常量 ====================
    /**
     * 错误码：其他
     */
    public static final String ERROR_CODE_OTHER = "OTHER";
    
    // ==================== 订单状态码常量 ====================
    /**
     * 分期状态：未支付
     */
    public static final int INSTALLMENT_STATUS_UNPAID = 0;
    /**
     * 分期状态：已支付
     */
    public static final int INSTALLMENT_STATUS_PAID = 1;
    
    // ==================== 金额转换常量 ====================
    /**
     * 分转元除数
     */
    public static final BigDecimal CENT_TO_YUAN_DIVISOR = BigDecimal.valueOf(100);
    /**
     * 小数位数
     */
    public static final int DECIMAL_SCALE = 2;
    
    // ==================== 价格相关常量 ====================
    /**
     * 零价格（合法金额格式，整数部分仅一位 0，避免支付宝租赁接口判定为参数非法）
     */
    public static final String ZERO_PRICE = "0.00";
    
    // ==================== 时间相关常量 ====================
    /**
     * 每天毫秒数
     */
    public static final long MILLIS_PER_DAY = 1000L * 60 * 60 * 24;
    /**
     * 每分钟毫秒数
     */
    public static final long MILLIS_PER_MINUTE = 1000L * 60;
}
