package com.fly.rent.legacy.dto.apilyRentUtil.vto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 风险控制参数
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public class AdvanceRiskCtrlParam {
    /**
     * 服务ID，如 "2021071100000000000090709200"
     */
    public String serviceId;
    /**
     * 类别ID，如 "RENT_CAMERA"
     */
    public String categoryId;
    /**
     * 最大免押金额，为总押金
     */
    public String maxDeductAmount;
    /**
     * 风险方案ID
     */
    public String riskSchemeId;
    /**
     * 是否启用租安盾计费风控模型。兼容商品详情页组件回传参数。
     */
    public Boolean feeRiskModel;
    /**
     * 支付宝接口文档中的原始字段名，部分组件透传时读取 snake_case。
     */
    @JsonProperty("fee_risk_model")
    public Boolean feeRiskModelSnake;
}
