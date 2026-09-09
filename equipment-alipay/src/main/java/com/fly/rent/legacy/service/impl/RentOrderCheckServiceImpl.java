package com.fly.rent.legacy.service.impl;

import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.config.NoUseException;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.legacy.dto.apilyRentUtil.BaseApily;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.AdvanceRiskCtrlParam;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.ApiResponse;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.BizParamData;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.CostInfo;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.CreditExtInfo;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.ServiceProtocol;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.StagePayPlan;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderCheckService;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.fly.rent.support.util.OrderUtil;
import com.fly.rent.support.util.RentInstallmentPlanSupport;
import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayCommerceRentRiskConsultModel;
import com.alipay.api.request.AlipayCommerceRentRiskConsultRequest;
import com.alipay.api.response.AlipayCommerceRentRiskConsultResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 租赁订单预检查服务实现。
 * 负责输出下单前的商品、价格、协议和风控咨询参数。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Slf4j
@Service
public class RentOrderCheckServiceImpl implements RentOrderCheckService {
    private static final String CONFIG_FEE_RISK_MODEL = "alipay.rent-risk.fee-risk-model";

    @Autowired
    private BaseApily baseApily;

    @Autowired
    private AlipayClientService alipayClientService;
    @Autowired
    private CapabilityConfigService configService;
    @Autowired
    private AlipayPlatformConfigService alipayPlatformConfigService;

    /**
     * 检查订单
     * @param outSkuId 商家SKU ID
     * @return 检查结果
     */
    @Override
    public ApiResponse check(String outSkuId) {
        return check(outSkuId, null);
    }

    @Override
    public ApiResponse check(String outSkuId, Integer installmentCount) {
        return check(outSkuId, installmentCount, null);
    }

    @Override
    public ApiResponse check(String outSkuId, Integer installmentCount, Integer duration) {
        ApiResponse resp = new ApiResponse();
        List<Attr> attrs = queryAttrs(outSkuId);

        if (attrs == null || attrs.isEmpty()) {
            resp.success = false;
            resp.errorCode = AlipayRentConstants.ERROR_CODE_OTHER;
            resp.errorMsg = "商品不存在";
            return resp;
        }

        Attr attr = attrs.get(0);
        Good good = attr.getGoodId() == null ? null : baseApily.getGoodMapper().selectById(attr.getGoodId());
        int periodTotal;
        try {
            periodTotal = RentInstallmentPlanSupport.resolveRequestedPeriod(installmentCount, attr);
        } catch (NoUseException ex) {
            resp.success = false;
            resp.errorCode = AlipayRentConstants.ERROR_CODE_OTHER;
            resp.errorMsg = ex.getMessage();
            return resp;
        }

        BigDecimal totalDepositPrice = OrderUtil.convertCentToYuan(attr.getAttrDeposit());
        int totalAmount = RentInstallmentPlanSupport.calculateTotalRentAmount(
                attr.getAttrAmount(),
                1,
                duration
        );
        BigDecimal totalAmountPrice = OrderUtil.convertCentToYuan(totalAmount);

        ServiceProtocol protocol = new ServiceProtocol();
        protocol.protocolUrl = alipayPlatformConfigService.protocolPath();
        protocol.protocolName = alipayPlatformConfigService.protocolName();

        Date planPayTime = new Date(System.currentTimeMillis() + 10 * 60 * 1000L);
        StagePayPlan stagePayPlan = new StagePayPlan();
        stagePayPlan.stagePayPlanInfos = RentInstallmentPlanSupport.buildStagePayPlanInfos(
                totalAmount,
                periodTotal,
                planPayTime,
                resolveBillingCycle(periodTotal, attr)
        );

        CostInfo costInfo = new CostInfo();
        costInfo.originalPrice = totalAmountPrice.toString();
        costInfo.discountedPrice = AlipayRentConstants.ZERO_PRICE;
        costInfo.totalRent = totalAmountPrice.toString();
        costInfo.deposit = totalDepositPrice.toString();
        costInfo.stagePayPlan = stagePayPlan;

        AdvanceRiskCtrlParam advanceRiskCtrlParam = new AdvanceRiskCtrlParam();
        advanceRiskCtrlParam.serviceId = alipayPlatformConfigService.zmServiceId();
        advanceRiskCtrlParam.categoryId = resolveRentCategoryId(good);
        advanceRiskCtrlParam.maxDeductAmount = totalDepositPrice.toString();
        boolean feeRiskModelEnabled = configService.booleanValue(CONFIG_FEE_RISK_MODEL, true);
        advanceRiskCtrlParam.feeRiskModel = feeRiskModelEnabled;
        advanceRiskCtrlParam.feeRiskModelSnake = feeRiskModelEnabled;

        BizParamData bizParamData = new BizParamData();
        bizParamData.serviceProtocolList = Collections.singletonList(protocol);
        bizParamData.costInfo = costInfo;
        bizParamData.advanceRiskCtrlParam = advanceRiskCtrlParam;
        bizParamData.creditExtInfo = CreditExtInfo.rentShield(feeRiskModelEnabled);

        resp.success = true;
        resp.needEmail = false;
        resp.needFaceValidateFlag = true;
        resp.bizParamData = bizParamData;
        return resp;
    }

    private List<Attr> queryAttrs(String outSkuId) {
        List<Attr> attrs = baseApily.getAttrMapper().selectList(
                new QueryWrapper<Attr>().eq("alipay_sku_id", outSkuId));
        if (attrs != null && !attrs.isEmpty()) {
            return attrs;
        }
        if (outSkuId != null && outSkuId.matches("\\d+") && outSkuId.length() <= 9) {
            Attr attr = baseApily.getAttrMapper().selectById(Integer.valueOf(outSkuId));
            if (attr != null) {
                List<Attr> result = new ArrayList<>();
                result.add(attr);
                return result;
            }
        }
        return attrs;
    }

    private Integer resolveBillingCycle(int periodTotal, Attr attr) {
        if (periodTotal <= 1) {
            return 1;
        }
        if (attr != null && attr.getAttrTradeType() != null && attr.getAttrTradeType() > 1) {
            return attr.getAttrTradeType();
        }
        return 2;
    }

    private String resolveRentCategoryId(Good good) {
        if (good != null && StringUtils.hasText(good.getAlipayRentCategoryId())) {
            return good.getAlipayRentCategoryId().trim();
        }
        return alipayPlatformConfigService.rentCategoryId();
    }

    /**
     * 查询租赁行业风险咨询。
     *
     * 官方文档说明 consult_risk_types 不传时：
     * 未签约默认共租风险免费版，签约后默认共租风险专业版、综合风险等级和发货建议。
     */
    @Override
    public Object queryUserRisk(Order order) {
        return queryUserRisk(order, configuredConsultRiskTypes());
    }

    @Override
    public Object queryUserRisk(Order order, List<String> consultRiskTypes) {
        try {
            AlipayCommerceRentRiskConsultRequest request = new AlipayCommerceRentRiskConsultRequest();
            AlipayCommerceRentRiskConsultModel model = new AlipayCommerceRentRiskConsultModel();

            AlipayUserIdentityUtil.applyRiskConsultIdentity(model, order.getUserUuid());
            model.setRiskBizScene(configService.value("alipay.rent-risk.risk-biz-scene", "RENT_ORDER"));
            model.setOutBizNo(order.getOutOrderId());
            if (consultRiskTypes != null && !consultRiskTypes.isEmpty()) {
                model.setConsultRiskTypes(consultRiskTypes);
            }

            request.setBizModel(model);
            AlipayCommerceRentRiskConsultResponse response = alipayClientService.execute(request);
            return response;
        } catch (AlipayApiException e) {
            log.error("租赁行业风险咨询失败: {}", e.getMessage(), e);
            throw new RuntimeException("租赁行业风险咨询失败: " + e.getMessage(), e);
        }
    }

    private List<String> configuredConsultRiskTypes() {
        String value = configService.value("alipay.rent-risk.consult-risk-types", "");
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return result;
        }
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim().toUpperCase());
            }
        }
        return result;
    }

}
