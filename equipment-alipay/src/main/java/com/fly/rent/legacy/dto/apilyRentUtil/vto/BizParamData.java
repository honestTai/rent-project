package com.fly.rent.legacy.dto.apilyRentUtil.vto;

import java.util.List;

/**
 * 业务参数数据
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public class BizParamData {
    /**
     * 服务协议列表
     */
    public List<ServiceProtocol> serviceProtocolList;
    /**
     * 租金类，包含订单原始总租金、优惠、总租金、押金、分期支付方案等信息
     */
    public CostInfo costInfo;
    /**
     * 风险控制参数，包含服务ID、类别ID、最大免押金额等
     */
    public AdvanceRiskCtrlParam advanceRiskCtrlParam;
    /**
     * 芝麻信用拓展信息，包含租安盾计费风控开关。
     */
    public CreditExtInfo creditExtInfo;
}
