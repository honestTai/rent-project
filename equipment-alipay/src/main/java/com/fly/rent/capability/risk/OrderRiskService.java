package com.fly.rent.capability.risk;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayCloudTraasCloudriskRentriskQueryModel;
import com.alipay.api.domain.RentCustomerDetail;
import com.alipay.api.domain.RentDeliveryDetail;
import com.alipay.api.domain.RentItemDetail;
import com.alipay.api.domain.RentPriceDetail;
import com.alipay.api.request.AlipayCloudTraasCloudriskRentriskQueryRequest;
import com.alipay.api.response.AlipayCloudTraasCloudriskRentriskQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.User;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderCheckService;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.mapper.UserMapper;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderRiskService {

    private static final String CONFIG_RISK_PROVIDER = "alipay.risk.provider";
    private static final String PROVIDER_RENT_SHIELD = "rent-shield";
    private static final String PROVIDER_CLOUD_RENT_RISK = "cloud-rent-risk";
    private static final String DEFAULT_CLOUD_SOURCE = "ALIPAY";
    private static final String DEFAULT_CLOUD_USER_AUTHORIZATION = "1";
    private static final String SUB_CODE_ORDER_STATUS_NOT_SUPPORT = "ORDER_STATUS_NOT_SUPPORT";
    private static final List<String> SHIP_RISK_TYPES = Collections.singletonList("VERTICAL_RENT_RISK");

    private final RentOrderCheckService rentOrderCheckService;
    private final AlipayClientService alipayClientService;
    private final CapabilityConfigService configService;
    private final UserMapper userMapper;
    private final GoodMapper goodMapper;
    private final AttrMapper attrMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> assess(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("订单不能为空");
        }
        String provider = riskProvider();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskProvider", provider);
        result.put("riskProviderName", riskProviderName(provider));

        if (PROVIDER_CLOUD_RENT_RISK.equals(provider)) {
            Map<String, Object> cloudRisk = queryCloudRentRisk(order);
            result.put("risk", cloudRisk);
            result.put("cloudRentRisk", cloudRisk);
            result.put("alipayRisk", alipayRiskView(cloudRisk, "支付宝云智能租赁风控无返回"));
            return result;
        }

        Map<String, Object> auditRisk = queryCommerceRentRisk(order);
        Map<String, Object> shipRisk = queryCommerceRentRisk(order, SHIP_RISK_TYPES);
        Map<String, Object> rawRisk = mergeCommerceRentRisk(auditRisk, shipRisk);
        if (!rawRisk.isEmpty() && !Boolean.FALSE.equals(rawRisk.get("available"))) {
            result.putAll(rawRisk);
        }
        result.put("rentShieldRisk", rawRisk);
        result.put("risk", rawRisk);
        result.put("alipayRisk", alipayRiskView(rawRisk, "支付宝租赁行业风险咨询无返回"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryCommerceRentRisk(Order order) {
        return queryCommerceRentRisk(order, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryCommerceRentRisk(Order order, List<String> consultRiskTypes) {
        if (isTerminalRentOrder(order)) {
            return skippedTerminalRentRisk(order);
        }
        try {
            Object riskResult = consultRiskTypes == null
                    ? rentOrderCheckService.queryUserRisk(order)
                    : rentOrderCheckService.queryUserRisk(order, consultRiskTypes);
            Map<String, Object> raw = riskResult == null ? Collections.emptyMap() : objectMapper.convertValue(riskResult, Map.class);
            if (!isSuccess(raw)) {
                raw.put("available", false);
                raw.put("errorMessage", firstText(
                        value(raw, "subMsg"),
                        value(raw, "msg"),
                        "租赁行业风险咨询失败"
                ));
            }
            return raw;
        } catch (RuntimeException ex) {
            return unavailable(ex.getMessage());
        }
    }

    private Map<String, Object> mergeCommerceRentRisk(Map<String, Object> auditRisk, Map<String, Object> shipRisk) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (auditRisk != null) {
            merged.putAll(auditRisk);
        }
        if (shipRisk == null || shipRisk.isEmpty() || Boolean.FALSE.equals(shipRisk.get("available"))) {
            return merged;
        }
        copyIfPresent(merged, shipRisk, "productEdition", false);
        copyIfPresent(merged, shipRisk, "vamGroup", false);
        copyIfPresent(merged, shipRisk, "riskBasicInfo", false);
        copyIfPresent(merged, shipRisk, "comprehensiveRiskModels", false);
        copyIfPresent(merged, shipRisk, "extremelyLowRiskModels", false);
        copyIfPresent(merged, shipRisk, "highRiskModels", false);
        copyIfPresent(merged, shipRisk, "riskInfos", false);
        copyIfPresent(merged, shipRisk, "shipGoodsRiskModels", true);
        return merged;
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key, boolean override) {
        Object value = source.get(key);
        if (!hasValue(value)) {
            return;
        }
        if (override || !hasValue(target.get(key))) {
            target.put(key, value);
        }
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return StringUtils.hasText((String) value);
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    private boolean isTerminalRentOrder(Order order) {
        String status = trimToNull(order.getAlipayStatus());
        return AlipayRentConstants.STATUS_FINISHED.equals(status)
                || AlipayRentConstants.STATUS_CLOSED.equals(status)
                || AlipayRentConstants.STATUS_RETURN_RECEIVED.equals(status);
    }

    private Map<String, Object> skippedTerminalRentRisk(Order order) {
        Map<String, Object> view = new LinkedHashMap<>();
        String message = "订单已结束，不再请求租安盾风险咨询";
        view.put("available", false);
        view.put("success", false);
        view.put("skipped", true);
        view.put("subCode", SUB_CODE_ORDER_STATUS_NOT_SUPPORT);
        view.put("subMsg", message);
        view.put("errorMessage", message);
        view.put("orderStatus", trimToNull(order.getAlipayStatus()));
        return view;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryCloudRentRisk(Order order) {
        try {
            AlipayCloudTraasCloudriskRentriskQueryRequest request = new AlipayCloudTraasCloudriskRentriskQueryRequest();
            request.setBizModel(buildCloudRentRiskModel(order));
            AlipayCloudTraasCloudriskRentriskQueryResponse response = alipayClientService.execute(request);
            Map<String, Object> raw = response == null ? Collections.emptyMap() : objectMapper.convertValue(response, Map.class);
            Map<String, Object> view = new LinkedHashMap<>();
            boolean success = response != null && response.isSuccess();
            view.put("available", success);
            view.put("success", success);
            view.put("recordId", response == null ? null : response.getRecordId());
            view.put("riskName", response == null ? null : response.getRiskName());
            view.put("riskRank", response == null ? null : response.getRiskRank());
            view.put("riskDesc", response == null ? null : response.getRiskDesc());
            view.put("subRentRiskResult", response == null ? null : response.getSubRentRiskResult());
            view.put("subRiskResultList", response == null ? Collections.emptyList() : response.getSubRiskResultList());
            view.put("raw", raw);
            if (!success) {
                view.put("errorMessage", response == null
                        ? "支付宝云智能租赁风控无返回"
                        : firstText(response.getSubMsg(), response.getMsg(), "支付宝云智能租赁风控查询失败"));
                view.put("subCode", response == null ? null : response.getSubCode());
            }
            return view;
        } catch (AlipayApiException | RuntimeException ex) {
            return unavailable(ex.getMessage());
        }
    }

    private AlipayCloudTraasCloudriskRentriskQueryModel buildCloudRentRiskModel(Order order) {
        User user = loadUser(order);
        Good good = order.getGoodId() == null ? null : goodMapper.selectById(order.getGoodId());
        Attr attr = order.getAttrId() == null ? null : attrMapper.selectById(order.getAttrId());

        AlipayCloudTraasCloudriskRentriskQueryModel model = new AlipayCloudTraasCloudriskRentriskQueryModel();
        model.setOutBizNo(firstText(order.getOutOrderId(), order.getOrderNo()));
        model.setRiskBizScene(configService.value("alipay.cloud-rent-risk.risk-biz-scene", "RENT_ORDER"));
        model.setSource(configService.value("alipay.cloud-rent-risk.source", DEFAULT_CLOUD_SOURCE));
        model.setUserAuthorization(configService.value("alipay.cloud-rent-risk.user-authorization", DEFAULT_CLOUD_USER_AUTHORIZATION));

        String identity = firstText(order.getUserUuid(), user == null ? null : user.getUuid());
        String mobile = firstText(order.getUserTel(), user == null ? null : user.getUserTel());
        String certNo = user == null ? null : user.getIdCard();
        applyCloudCustomerIdentity(model, identity, mobile, certNo);
        model.setMobile(mobile);
        if (StringUtils.hasText(certNo)) {
            model.setCertNo(certNo.trim());
        }

        model.setCustomerDetail(buildCustomerDetail(identity, mobile, certNo));
        model.setDeliveryDetail(buildDeliveryDetail(order, mobile));
        model.setItemDetail(buildItemDetail(order, good, attr));
        model.setPriceDetail(buildPriceDetail(order));
        return model;
    }

    private void applyCloudCustomerIdentity(
            AlipayCloudTraasCloudriskRentriskQueryModel model,
            String identity,
            String mobile,
            String certNo
    ) {
        String configuredType = configService.value("alipay.cloud-rent-risk.customer-type", "MOBILE");
        String customerType = StringUtils.hasText(configuredType) ? configuredType.trim().toUpperCase() : "MOBILE";
        model.setCustomerType(customerType);
        if ("CERT_NO".equals(customerType)) {
            model.setCustomerId(trimToNull(certNo));
            return;
        }
        if ("ALIPAY_USER_ID".equals(customerType) || "USER_ID".equals(customerType)) {
            model.setCustomerId(AlipayUserIdentityUtil.isAlipayUserId(identity) ? identity.trim() : null);
            return;
        }
        if ("ALIPAY_OPEN_ID".equals(customerType) || "OPEN_ID".equals(customerType) || "OPENID".equals(customerType)) {
            String openId = AlipayUserIdentityUtil.isAlipayUserId(identity) ? null : trimToNull(identity);
            model.setCustomerOpenId(openId);
            model.setCustomerId(openId);
            return;
        }
        model.setCustomerType("MOBILE");
        model.setCustomerId(trimToNull(mobile));
    }

    private RentCustomerDetail buildCustomerDetail(String identity, String mobile, String certNo) {
        RentCustomerDetail customer = new RentCustomerDetail();
        if (AlipayUserIdentityUtil.isAlipayUserId(identity)) {
            customer.setAlipayUserId(identity.trim());
        } else if (StringUtils.hasText(identity)) {
            customer.setAlipayOpenId(identity.trim());
        }
        customer.setMobile(trimToNull(mobile));
        customer.setCertNo(trimToNull(certNo));
        return customer;
    }

    private RentDeliveryDetail buildDeliveryDetail(Order order, String mobile) {
        RentDeliveryDetail delivery = new RentDeliveryDetail();
        delivery.setDeliveryType(order.getOfflinePickup() != null && order.getOfflinePickup() == 0 ? "SELF_PICKUP" : "EXPRESS");
        delivery.setReceiverName(order.getUserTitle());
        delivery.setReceiverMobile(trimToNull(mobile));
        delivery.setReceiverAddress(firstText(order.getAddr(), order.getPickupAddr()));
        return delivery;
    }

    private RentItemDetail buildItemDetail(Order order, Good good, Attr attr) {
        RentItemDetail item = new RentItemDetail();
        item.setOutItemId(order.getGoodId() == null ? null : String.valueOf(order.getGoodId()));
        item.setOutSkuId(order.getAttrId() == null ? null : String.valueOf(order.getAttrId()));
        item.setItemName(firstText(order.getGoodTitle(), good == null ? null : good.getGoodTitle(), order.getAttrTitle()));
        item.setGoodsCategory(firstText(good == null ? null : good.getAlipayRentCategoryId(), configService.value("alipay.rent.category-id", "")));
        item.setQuantity(order.getAttrNum() == null ? null : order.getAttrNum().longValue());
        item.setUnitPrice(OrderUtil.convertCentToYuan(firstAmount(order.getAttrAmount(), attr == null ? null : attr.getAttrAmount())).toPlainString());
        return item;
    }

    private RentPriceDetail buildPriceDetail(Order order) {
        RentPriceDetail price = new RentPriceDetail();
        price.setDepositPrice(OrderUtil.convertCentToYuan(order.getOrderDeposit()).toPlainString());
        price.setRealPayAmount(OrderUtil.convertCentToYuan(firstAmount(order.getOrderFinalpay(), order.getOrderTotal())).toPlainString());
        price.setInitialRentPrice(OrderUtil.convertCentToYuan(firstAmount(order.getOrderFirstAmount(), order.getOrderTotal())).toPlainString());
        price.setPeriodRealRentPrice(OrderUtil.convertCentToYuan(firstAmount(order.getOrderEveryAmount(), order.getOrderTotal())).toPlainString());
        price.setPreAuthorizationAmount(OrderUtil.convertCentToYuan(order.getOrderDeposit()).toPlainString());
        price.setPeriodNum(order.getOrderTotalRentPeriods() == null ? null : order.getOrderTotalRentPeriods().longValue());
        price.setRentStartTime(OrderUtil.toDate(order.getOrderStart()));
        price.setRentEndTime(OrderUtil.toDate(order.getOrderEnd()));
        return price;
    }

    private User loadUser(Order order) {
        if (order.getUserId() != null) {
            return userMapper.selectById(order.getUserId());
        }
        String userUuid = trimToNull(order.getUserUuid());
        if (userUuid == null) {
            return null;
        }
        return userMapper.selectOne(new QueryWrapper<User>().eq("uuid", userUuid).last("LIMIT 1"));
    }

    private Map<String, Object> unavailable(String errorMessage) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("available", false);
        view.put("success", false);
        view.put("errorMessage", errorMessage);
        return view;
    }

    private Map<String, Object> alipayRiskView(Map<String, Object> rawRisk, String emptyMessage) {
        Map<String, Object> view = new LinkedHashMap<>();
        boolean available = rawRisk == null || !Boolean.FALSE.equals(rawRisk.get("available"));
        view.put("available", available);
        if (!available) {
            view.put("errorMessage", rawRisk == null ? emptyMessage : rawRisk.get("errorMessage"));
            view.put("subCode", rawRisk == null ? null : rawRisk.get("subCode"));
        }
        view.put("raw", rawRisk);
        return view;
    }

    private boolean isSuccess(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return true;
        }
        Object success = raw.get("success");
        if (success instanceof Boolean) {
            return (Boolean) success;
        }
        String code = value(raw, "code");
        return !StringUtils.hasText(code) || "10000".equals(code);
    }

    private String value(Map<String, Object> raw, String key) {
        Object value = raw == null ? null : raw.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String riskProvider() {
        String value = configService.value(CONFIG_RISK_PROVIDER, PROVIDER_RENT_SHIELD);
        if (!StringUtils.hasText(value)) {
            return PROVIDER_RENT_SHIELD;
        }
        String normalized = value.trim().toLowerCase().replace('_', '-');
        if (PROVIDER_CLOUD_RENT_RISK.equals(normalized)
                || "cloud".equals(normalized)
                || "cloudrisk".equals(normalized)
                || "cloud-rentrisk".equals(normalized)
                || "alipay.cloud.traas.cloudrisk.rentrisk.query".equals(normalized)) {
            return PROVIDER_CLOUD_RENT_RISK;
        }
        return PROVIDER_RENT_SHIELD;
    }

    private String riskProviderName(String provider) {
        return PROVIDER_CLOUD_RENT_RISK.equals(provider) ? "支付宝云智能租赁风控" : "租安盾";
    }

    private Integer firstAmount(Integer... values) {
        if (values == null) {
            return 0;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return 0;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
