package com.fly.rent.legacy.dto.apilyRentUtil.vto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 芝麻信用拓展信息。
 */
public class CreditExtInfo {
    /**
     * 是否启用租安盾计费风控模型。
     */
    public Boolean feeRiskModel;
    /**
     * 支付宝文档原始字段名。
     */
    @JsonProperty("fee_risk_model")
    public Boolean feeRiskModelSnake;

    public static CreditExtInfo rentShield(boolean enabled) {
        CreditExtInfo info = new CreditExtInfo();
        info.feeRiskModel = enabled;
        info.feeRiskModelSnake = enabled;
        return info;
    }
}
