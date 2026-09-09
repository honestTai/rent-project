package com.fly.rent.capability;

public final class CapabilityConstants {

    private CapabilityConstants() {
    }

    public static final String PROVIDER_ALIPAY = "alipay";
    public static final String PROVIDER_ESIGN = "esign";

    public static final String CONTRACT_NONE = "NONE";
    public static final String CONTRACT_INIT = "INIT";
    public static final String CONTRACT_SIGNING = "SIGNING";
    public static final String CONTRACT_COMPLETED = "COMPLETED";
    public static final String CONTRACT_FAILED = "FAILED";

    public static final String BILL_WAIT_PAY = "WAIT_PAY";
    public static final String BILL_PAYING = "PAYING";
    public static final String BILL_PAID = "PAID";
    public static final String BILL_OVERDUE = "OVERDUE";
    public static final String BILL_CLOSED = "CLOSED";
    public static final String BILL_REFUNDED = "REFUNDED";

    public static final String WITHHOLD_UNSIGNED = "UNSIGNED";
    public static final String WITHHOLD_SIGNING = "SIGNING";
    public static final String WITHHOLD_SIGNED = "SIGNED";
    public static final String WITHHOLD_FAILED = "FAILED";
}
