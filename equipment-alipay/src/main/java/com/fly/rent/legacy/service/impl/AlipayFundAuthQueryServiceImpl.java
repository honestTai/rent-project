package com.fly.rent.legacy.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayFundAuthOperationDetailQueryModel;
import com.alipay.api.request.AlipayFundAuthOperationDetailQueryRequest;
import com.alipay.api.response.AlipayFundAuthOperationDetailQueryResponse;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.AlipayFundAuthQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付宝资金授权操作查询实现。
 * 使用项目统一的 AlipayClient（BaseApily），不单独 new DefaultAlipayClient。
 *
 * @author HonestTat
 * @since 2026-03-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayFundAuthQueryServiceImpl implements AlipayFundAuthQueryService {

    private final AlipayClientService alipayClientService;

    @Override
    public AlipayFundAuthOperationDetailQueryResponse queryDetail(String orderNo, String orderRequestNo) throws AlipayApiException {
        if (!StringUtils.hasText(orderNo) || !StringUtils.hasText(orderRequestNo)) {
            log.warn("资金授权查询参数不完整: orderNo={}, orderRequestNo={}", orderNo, orderRequestNo);
            return null;
        }
        AlipayFundAuthOperationDetailQueryRequest request = new AlipayFundAuthOperationDetailQueryRequest();
        AlipayFundAuthOperationDetailQueryModel model = new AlipayFundAuthOperationDetailQueryModel();
        model.setOutOrderNo(orderNo.trim());
        model.setOutRequestNo(orderRequestNo.trim());
        request.setBizModel(model);
        AlipayFundAuthOperationDetailQueryResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            log.warn("资金授权操作查询失败: orderNo={}, subCode={}, subMsg={}", orderNo, response.getSubCode(), response.getSubMsg());
        }
        return response;
    }
}
