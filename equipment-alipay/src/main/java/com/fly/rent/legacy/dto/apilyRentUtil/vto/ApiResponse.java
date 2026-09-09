package com.fly.rent.legacy.dto.apilyRentUtil.vto;

/**
 * API响应结果
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public class ApiResponse {
    /**
     * 成功标志，true表示成功，false表示失败
     */
    public boolean success;
    /**
     * 是否需要邮箱
     */
    public boolean needEmail;
    /**
     * 是否需要人脸验证
     */
    public boolean needFaceValidateFlag;
    /**
     * 业务参数数据，包含服务协议列表、费用信息、风险控制参数等
     */
    public BizParamData bizParamData;
    /**
     * 错误代码，如 "OTHER"
     */
    public String errorCode;
    /**
     * 错误消息，如 "商品不存在"
     */
    public String errorMsg;
}
