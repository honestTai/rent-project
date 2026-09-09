package com.fly.rent.legacy.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.response.AlipayFundAuthOperationDetailQueryResponse;

/**
 * 支付宝资金授权操作查询。
 * 用于查询单笔授权操作的详细信息（信用金额、资金金额、操作类型等），可同步押金/预授权状态。
 *
 * @author HonestTat
 * @since 2026-03-15
 */
public interface AlipayFundAuthQueryService {

    /**
     * 根据商户订单号与商户请求号查询资金授权操作详情。
     *
     * @param orderNo         商户订单号（out_order_no）
     * @param orderRequestNo  商户请求号（out_request_no）
     * @return 支付宝返回的详情，含 amount、credit_amount、fund_amount、operation_type 等；失败时仍返回 response，需调用方判断 isSuccess()
     * @throws AlipayApiException 支付宝接口异常
     */
    AlipayFundAuthOperationDetailQueryResponse queryDetail(String orderNo, String orderRequestNo) throws AlipayApiException;
}
