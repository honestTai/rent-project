package com.fly.rent.capability.contract;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.capability.CallbackLogService;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.OrderContract;
import com.fly.rent.mapper.OrderContractMapper;
import com.fly.rent.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderContractService {

    private final OrderMapper orderMapper;
    private final OrderContractMapper contractMapper;
    private final RentCurrentUserService currentUserService;
    private final CapabilityConfigService configService;
    private final EsignContractAdapter esignContractAdapter;
    private final CallbackLogService callbackLogService;
    private final RentExtensionStore rentExtensionStore;
    private static final String ESIGN_ENABLED_KEY = "esign.enabled";
    private static final String DEFAULT_ESIGN_MINIAPP_APP_ID = "2019042964339413";
    private static final String DEFAULT_ESIGN_MINIAPP_PATH = "pages/startup/index";
    private static final String DEFAULT_ESIGN_MINIAPP_SIGN_PAGE = "sign";

    public Map<String, Object> getContract(Integer orderId) {
        Order order = requireOwnOrder(orderId);
        return getContractForOrder(order);
    }

    public Map<String, Object> getContractForAdmin(Integer orderId) {
        return getContractForOrder(requireOrder(orderId));
    }

    public OrderContract getSignedContractFileForAdmin(Integer orderId) {
        Order order = requireOrder(orderId);
        OrderContract contract = latest(order.getOrderId());
        if (contract == null) {
            throw new IllegalStateException("电子合同尚未生成");
        }
        refreshContract(contract);
        if (!CapabilityConstants.CONTRACT_COMPLETED.equals(contract.getStatus())) {
            throw new IllegalStateException("电子合同尚未完成签署");
        }
        esignContractAdapter.downloadSignedFile(contract);
        contractMapper.updateById(contract);
        if (!StringUtils.hasText(contract.getDownloadUrl())
                && !StringUtils.hasText(contract.getViewUrl())
                && !StringUtils.hasText(contract.getPdfUrl())) {
            throw new IllegalStateException("电子合同签署文件暂不可用");
        }
        return contract;
    }

    private Map<String, Object> getContractForOrder(Order order) {
        if (!isOrderEsignRequired(order)) {
            return disabledContract(false);
        }
        if (!isEsignEnabled()) {
            return disabledContract(true);
        }
        OrderContract contract = latest(order.getOrderId());
        if (contract == null) {
            contract = new OrderContract()
                    .setOrderId(order.getOrderId())
                    .setProvider(CapabilityConstants.PROVIDER_ESIGN)
                    .setStatus(CapabilityConstants.CONTRACT_NONE)
                    .setContractName("电子合同");
        } else {
            refreshContract(contract);
        }
        return wrap(contract);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startSign(Integer orderId) {
        Order order = requireOwnOrder(orderId);
        return startSignForOrder(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startSignForAdmin(Integer orderId) {
        return startSignForOrder(requireOrder(orderId));
    }

    private Map<String, Object> startSignForOrder(Order order) {
        if (!isOrderEsignRequired(order)) {
            throw new IllegalStateException("当前订单未要求电子合同签署");
        }
        if (!isEsignEnabled()) {
            throw new IllegalStateException("电子合同未开启");
        }
        OrderContract exists = latest(order.getOrderId());
        if (exists != null && activeContract(exists)) {
            return wrap(exists);
        }
        OrderContract contract = esignContractAdapter.createSignFlow(order);
        Date now = new Date();
        if (contract.getCreatedAt() == null) {
            contract.setCreatedAt(now);
        }
        contract.setUpdatedAt(now);
        contractMapper.insert(contract);
        return wrap(contract);
    }

    public void requireReceiptSignReady(Order order) {
        if (!isOrderEsignRequired(order)) {
            return;
        }
        if (order == null || order.getOrderId() == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!isEsignEnabled()) {
            throw new IllegalStateException("电子合同未开启，请联系商家处理");
        }
        OrderContract contract = latest(order.getOrderId());
        if (contract != null) {
            refreshContract(contract);
        }
        if (contract == null || !CapabilityConstants.CONTRACT_COMPLETED.equals(contract.getStatus())) {
            throw new IllegalStateException("请先完成电子合同签署");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(Map<String, Object> payload) {
        callbackLogService.log(CapabilityConstants.PROVIDER_ESIGN, "contract", String.valueOf(payload.get("flowId")), payload, true);
        esignContractAdapter.handleCallback(payload);
        Object flowId = payload.get("flowId");
        if (flowId == null) {
            return;
        }
        OrderContract contract = contractMapper.selectOne(new QueryWrapper<OrderContract>()
                .eq("flow_id", String.valueOf(flowId))
                .last("LIMIT 1"));
        if (contract == null) {
            return;
        }
        String status = String.valueOf(payload.getOrDefault("status", ""));
        if (StringUtils.hasText(status)) {
            contract.setStatus(status.toUpperCase().contains("COMPLETE")
                    ? CapabilityConstants.CONTRACT_COMPLETED
                    : status.toUpperCase());
        }
        contract.setRawResponse(JSON.toJSONString(payload));
        contract.setUpdatedAt(new Date());
        if (CapabilityConstants.CONTRACT_COMPLETED.equals(contract.getStatus())) {
            contract.setSignedAt(new Date());
            try {
                esignContractAdapter.downloadSignedFile(contract);
            } catch (Exception ex) {
                log.warn("电子合同回调后下载合同失败: flowId={}, error={}", contract.getFlowId(), ex.getMessage());
            }
        }
        contractMapper.updateById(contract);
    }

    private void refreshContract(OrderContract contract) {
        if (contract == null || !activeContract(contract)) {
            return;
        }
        try {
            OrderContract refreshed = esignContractAdapter.queryStatus(contract);
            if (CapabilityConstants.CONTRACT_COMPLETED.equals(refreshed.getStatus())) {
                if (refreshed.getSignedAt() == null) {
                    refreshed.setSignedAt(new Date());
                }
                esignContractAdapter.downloadSignedFile(refreshed);
            }
            contractMapper.updateById(refreshed);
        } catch (Exception ex) {
            log.warn("刷新电子合同失败: contractId={}, flowId={}, error={}",
                    contract.getId(), contract.getFlowId(), ex.getMessage());
        }
    }

    private Order requireOwnOrder(Integer orderId) {
        String userUuid = currentUserService.requireUserUuid();
        Order order = requireOrder(orderId);
        if (!userUuid.equals(order.getUserUuid())) {
            throw new IllegalArgumentException("该订单不属于当前用户");
        }
        return order;
    }

    private Order requireOrder(Integer orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private OrderContract latest(Integer orderId) {
        return contractMapper.selectOne(new QueryWrapper<OrderContract>()
                .eq("order_id", orderId)
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    private boolean activeContract(OrderContract contract) {
        String status = contract.getStatus();
        return CapabilityConstants.CONTRACT_SIGNING.equals(status)
                || CapabilityConstants.CONTRACT_COMPLETED.equals(status);
    }

    private Map<String, Object> wrap(OrderContract contract) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", true);
        result.put("required", true);
        result.put("mode", "esign");
        result.put("contract", toView(contract));
        result.put("signUrl", contract.getSignUrl());
        return result;
    }

    private Map<String, Object> disabledContract(boolean required) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", false);
        result.put("required", required);
        result.put("mode", "pdf");
        result.put("contract", null);
        result.put("signUrl", null);
        return result;
    }

    public boolean isEsignCapabilityEnabled() {
        return isEsignEnabled();
    }

    private boolean isEsignEnabled() {
        return configService.booleanValue(ESIGN_ENABLED_KEY, false);
    }

    private boolean isOrderEsignRequired(Order order) {
        if (order == null || !StringUtils.hasText(order.getOrderNo())) {
            return false;
        }
        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        return extension != null && Boolean.TRUE.equals(extension.getEsignRequired());
    }

    private Map<String, Object> toView(OrderContract contract) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("contractId", contract.getId() == null ? null : String.valueOf(contract.getId()));
        view.put("flowId", contract.getFlowId());
        view.put("signerId", contract.getSignerId());
        view.put("fileId", contract.getFileId());
        view.put("contractNo", contract.getContractNo());
        view.put("contractName", contract.getContractName());
        view.put("status", contract.getStatus());
        view.put("statusText", contractStatusText(contract.getStatus()));
        view.put("signUrl", contract.getSignUrl());
        view.put("viewUrl", contract.getViewUrl());
        view.put("downloadUrl", contract.getDownloadUrl());
        view.put("pdfUrl", contract.getPdfUrl());
        view.put("signedAt", contract.getSignedAt());
        view.put("expireAt", contract.getExpireAt());
        Map<String, Object> mini = new LinkedHashMap<>();
        String env = configService.value("esign.miniapp.env", "prod");
        String miniappPath = configService.value("esign.miniapp.path", DEFAULT_ESIGN_MINIAPP_PATH);
        String signPage = configService.value("esign.miniapp.page", DEFAULT_ESIGN_MINIAPP_SIGN_PAGE);
        String forwardHome = configService.value("esign.miniapp.forward-home", "true");
        String pluginPage = configService.value("esign.miniapp.plugin-page", "esign");
        String pluginUrl = buildPluginUrl(pluginPage, contract.getFlowId(), contract.getSignerId(), env);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("env", env);
        query.put("page", signPage);
        query.put("flowId", contract.getFlowId());
        query.put("signerId", contract.getSignerId());
        query.put("forwardHome", forwardHome);
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("page", miniappPath);
        extraData.put("query", query);
        mini.put("appId", configService.value("esign.miniapp.app-id", DEFAULT_ESIGN_MINIAPP_APP_ID));
        mini.put("path", miniappPath);
        mini.put("page", signPage);
        mini.put("flowId", contract.getFlowId());
        mini.put("signerId", contract.getSignerId());
        mini.put("env", env);
        mini.put("forwardHome", forwardHome);
        mini.put("openType", configService.value("esign.miniapp.open-type", "miniProgram"));
        mini.put("query", query);
        mini.put("extraData", extraData);
        mini.put("pluginPage", pluginPage);
        mini.put("pluginUrl", pluginUrl);
        mini.put("pluginPath", pluginUrl);
        mini.put("skipResult", configService.value("esign.miniapp.skip-result", "false"));
        mini.put("skipGuide", configService.value("esign.miniapp.skip-guide", "false"));
        view.put("signMiniProgram", mini);
        return view;
    }

    private String buildPluginUrl(String pluginPage, String flowId, String signerId, String env) {
        if (!StringUtils.hasText(flowId) || !StringUtils.hasText(signerId)) {
            return null;
        }
        String page = StringUtils.hasText(pluginPage) ? pluginPage : "esign";
        return "plugin://esign/" + page
                + "?env=" + encode(env)
                + "&flowId=" + encode(flowId)
                + "&signerId=" + encode(signerId);
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ex) {
            return value == null ? "" : value;
        }
    }

    private String contractStatusText(String status) {
        if (CapabilityConstants.CONTRACT_NONE.equals(status)) return "待生成";
        if (CapabilityConstants.CONTRACT_INIT.equals(status)) return "待发起";
        if (CapabilityConstants.CONTRACT_SIGNING.equals(status)) return "待签署";
        if (CapabilityConstants.CONTRACT_COMPLETED.equals(status)) return "已完成";
        if (CapabilityConstants.CONTRACT_FAILED.equals(status)) return "签署失败";
        return status;
    }
}
