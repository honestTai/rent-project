package com.fly.rent.capability.contract;

import com.alibaba.fastjson.JSON;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.capability.CapabilityConstants;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.OrderContract;
import com.fly.rent.miniapp.order.RentContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsignContractAdapter {

    private static final String OAUTH2_MODE = "oauth2";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final CapabilityConfigService configService;
    private final RentContractService rentContractService;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile AccessToken cachedToken;

    public OrderContract createSignFlow(Order order) {
        requireConfigured("esign.base-url");
        try {
            if (useOfficialOAuth2()) {
                return createOfficialSignFlow(order);
            }
            return createGatewaySignFlow(order);
        } catch (Exception ex) {
            throw new IllegalStateException("电子签署流程创建失败: " + ex.getMessage(), ex);
        }
    }

    public OrderContract queryStatus(OrderContract contract) {
        if (useOfficialOAuth2()) {
            return queryOfficialStatus(contract);
        }
        return queryGatewayStatus(contract);
    }

    public OrderContract downloadSignedFile(OrderContract contract) {
        if (useOfficialOAuth2()) {
            return downloadOfficialSignedFile(contract);
        }
        return downloadGatewaySignedFile(contract);
    }

    public void handleCallback(Map<String, Object> payload) {
        log.info("收到电子合同回调: {}", JSON.toJSONString(maskCallback(payload)));
    }

    private OrderContract createOfficialSignFlow(Order order) throws Exception {
        requireConfigured("esign.app-id");
        requireConfigured("esign.app-secret");

        RentViews.RentalContractView localPdf = rentContractService.generateSignedContractNow(order.getOrderId(), "电子合同签署流程");
        validateSigner(localPdf);
        byte[] pdfBytes = rentContractService.renderContractPdf(localPdf);
        String signerAccountId = getOrCreatePersonalAccount(order, localPdf);
        String fileName = localPdf.getContractNo() + ".pdf";
        String fileId = uploadOfficialFile(fileName, pdfBytes);
        String flowId = createOfficialFlow(localPdf);
        addOfficialFlowDocument(flowId, fileId, fileName);
        addOfficialPlatformSignIfNeeded(flowId, fileId);
        addOfficialBuyerHandSign(flowId, fileId, signerAccountId, platformSignEnabled());
        startOfficialFlow(flowId);
        String signUrl = getOfficialExecuteUrl(flowId, signerAccountId);

        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("flowId", flowId);
        raw.put("signerId", signerAccountId);
        raw.put("fileId", fileId);
        raw.put("signUrl", signUrl);
        raw.put("mode", "oauth2");

        Date now = new Date();
        return new OrderContract()
                .setOrderId(order.getOrderId())
                .setProvider(CapabilityConstants.PROVIDER_ESIGN)
                .setContractNo(localPdf.getContractNo())
                .setContractName(localPdf.getContractTitle())
                .setFileId(fileId)
                .setFlowId(flowId)
                .setSignerId(signerAccountId)
                .setSignUrl(signUrl)
                .setStatus(CapabilityConstants.CONTRACT_SIGNING)
                .setPdfUrl(localPdf.getContractPdfUrl())
                .setRawResponse(JSON.toJSONString(raw))
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private OrderContract createGatewaySignFlow(Order order) throws Exception {
        requireConfigured("esign.auth-token");
        RentViews.RentalContractView localPdf = rentContractService.generateSignedContractNow(order.getOrderId(), "电子合同签署流程");
        String fileId = uploadGatewayFile(localPdf);
        Map<String, Object> flow = createGatewayFlow(order, localPdf, fileId);
        Date now = new Date();
        return new OrderContract()
                .setOrderId(order.getOrderId())
                .setProvider(CapabilityConstants.PROVIDER_ESIGN)
                .setContractNo(localPdf.getContractNo())
                .setContractName(localPdf.getContractTitle())
                .setFileId(fileId)
                .setFlowId(text(flow.get("flowId")))
                .setSignerId(text(flow.get("signerId")))
                .setSignUrl(text(flow.get("signUrl")))
                .setStatus(CapabilityConstants.CONTRACT_SIGNING)
                .setPdfUrl(localPdf.getContractPdfUrl())
                .setRawResponse(JSON.toJSONString(flow))
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private OrderContract queryOfficialStatus(OrderContract contract) {
        if (!StringUtils.hasText(contract.getFlowId())) {
            return contract;
        }
        Map<String, Object> response = officialRequest(HttpMethod.GET,
                "/v1/signflows/" + encodePath(contract.getFlowId()), null);
        contract.setRawResponse(JSON.toJSONString(response));
        contract.setStatus(mapOfficialFlowStatus(response.get("flowStatus")));
        contract.setUpdatedAt(new Date());
        return contract;
    }

    private OrderContract queryGatewayStatus(OrderContract contract) {
        String path = configService.value("esign.query-flow-path", "");
        if (!StringUtils.hasText(path) || !StringUtils.hasText(contract.getFlowId())) {
            return contract;
        }
        Map<String, Object> response = gatewayRequest(HttpMethod.GET, path.replace("{flowId}", contract.getFlowId()), null);
        contract.setRawResponse(JSON.toJSONString(response));
        String status = text(response.get("status"));
        if (StringUtils.hasText(status)) {
            contract.setStatus(mapGatewayStatus(status));
        }
        contract.setUpdatedAt(new Date());
        return contract;
    }

    @SuppressWarnings("unchecked")
    private OrderContract downloadOfficialSignedFile(OrderContract contract) {
        if (!StringUtils.hasText(contract.getFlowId())) {
            return contract;
        }
        Map<String, Object> response = officialRequest(HttpMethod.GET,
                "/v1/signflows/" + encodePath(contract.getFlowId()) + "/documents", null);
        Object docsValue = response.get("docs");
        if (docsValue instanceof List && !((List<?>) docsValue).isEmpty()) {
            Object first = ((List<?>) docsValue).get(0);
            if (first instanceof Map) {
                Map<String, Object> firstDoc = (Map<String, Object>) first;
                String fileUrl = text(firstDoc.get("fileUrl"));
                contract.setDownloadUrl(fileUrl);
                contract.setViewUrl(fileUrl);
            }
        }
        contract.setRawResponse(JSON.toJSONString(response));
        contract.setUpdatedAt(new Date());
        return contract;
    }

    private OrderContract downloadGatewaySignedFile(OrderContract contract) {
        String path = configService.value("esign.download-path", "");
        if (!StringUtils.hasText(path) || !StringUtils.hasText(contract.getFlowId())) {
            return contract;
        }
        Map<String, Object> response = gatewayRequest(HttpMethod.GET, path.replace("{flowId}", contract.getFlowId()), null);
        contract.setDownloadUrl(text(response.get("downloadUrl")));
        contract.setViewUrl(text(response.get("viewUrl")));
        contract.setRawResponse(JSON.toJSONString(response));
        contract.setUpdatedAt(new Date());
        return contract;
    }

    private String getOrCreatePersonalAccount(Order order, RentViews.RentalContractView localPdf) {
        String thirdPartyUserId = buildThirdPartyUserId(order, localPdf);
        try {
            Map<String, Object> queried = officialRequest(HttpMethod.GET,
                    "/v1/accounts/getByThirdId?thirdPartyUserId=" + encodeQuery(thirdPartyUserId), null);
            String accountId = text(queried.get("accountId"));
            if (StringUtils.hasText(accountId)) {
                return accountId;
            }
        } catch (Exception ex) {
            log.info("e签宝个人账号按 thirdPartyUserId 未命中，将创建: orderId={}, msg={}",
                    order.getOrderId(), sanitizeMessage(ex.getMessage()));
        }

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("thirdPartyUserId", thirdPartyUserId);
        body.put("name", localPdf.getBuyerName());
        body.put("idType", configService.value("esign.signer.id-type", "CRED_PSN_CH_IDCARD"));
        body.put("idNumber", localPdf.getBuyerIdCard());
        body.put("mobile", localPdf.getBuyerPhone());
        try {
            Map<String, Object> created = officialRequest(HttpMethod.POST, "/v1/accounts/createByThirdPartyUserId", body);
            String accountId = text(created.get("accountId"));
            if (StringUtils.hasText(accountId)) {
                return accountId;
            }
        } catch (Exception ex) {
            if (!String.valueOf(ex.getMessage()).contains("53000000")) {
                throw ex;
            }
            Map<String, Object> queried = officialRequest(HttpMethod.GET,
                    "/v1/accounts/getByThirdId?thirdPartyUserId=" + encodeQuery(thirdPartyUserId), null);
            String accountId = text(queried.get("accountId"));
            if (StringUtils.hasText(accountId)) {
                return accountId;
            }
        }
        throw new IllegalStateException("e签宝未返回签署人 accountId");
    }

    private String uploadOfficialFile(String fileName, byte[] pdfBytes) throws Exception {
        String contentMd5 = md5Base64(pdfBytes);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("contentMd5", contentMd5);
        body.put("contentType", PDF_CONTENT_TYPE);
        body.put("convert2Pdf", false);
        body.put("fileName", fileName);
        body.put("fileSize", pdfBytes.length);

        Map<String, Object> upload = officialRequest(HttpMethod.POST, "/v1/files/getUploadUrl", body);
        String fileId = text(upload.get("fileId"));
        String uploadUrl = text(upload.get("uploadUrl"));
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(uploadUrl)) {
            throw new IllegalStateException("e签宝文件上传授权未返回 fileId/uploadUrl");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(PDF_CONTENT_TYPE));
        headers.setContentLength(pdfBytes.length);
        headers.set("Content-MD5", contentMd5);
        restTemplate.exchange(URI.create(uploadUrl), HttpMethod.PUT, new HttpEntity<byte[]>(pdfBytes, headers), String.class);
        return fileId;
    }

    private String createOfficialFlow(RentViews.RentalContractView localPdf) {
        Map<String, Object> configInfo = new LinkedHashMap<String, Object>();
        String notifyUrl = configService.value("esign.notify-url", "");
        if (StringUtils.hasText(notifyUrl)) {
            configInfo.put("noticeDeveloperUrl", notifyUrl);
        }
        configInfo.put("noticeType", configService.value("esign.notice-type", ""));
        configInfo.put("signPlatform", configService.value("esign.sign-platform", "2"));

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("autoArchive", configService.booleanValue("esign.auto-archive", true));
        body.put("businessScene", StringUtils.hasText(localPdf.getContractTitle())
                ? localPdf.getContractTitle()
                : "设备租赁协议");
        body.put("configInfo", configInfo);

        Map<String, Object> response = officialRequest(HttpMethod.POST, "/v1/signflows", body);
        String flowId = text(response.get("flowId"));
        if (!StringUtils.hasText(flowId)) {
            throw new IllegalStateException("e签宝创建签署流程未返回 flowId");
        }
        return flowId;
    }

    private void addOfficialFlowDocument(String flowId, String fileId, String fileName) {
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("fileId", fileId);
        doc.put("fileName", fileName);
        doc.put("encryption", 0);
        doc.put("filePassword", "");

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("docs", Collections.singletonList(doc));
        officialRequest(HttpMethod.POST, "/v1/signflows/" + encodePath(flowId) + "/documents", body);
    }

    private void addOfficialBuyerHandSign(String flowId, String fileId, String signerAccountId, boolean hasPlatformSign) {
        Map<String, Object> signfield = new LinkedHashMap<String, Object>();
        signfield.put("fileId", fileId);
        signfield.put("signerAccountId", signerAccountId);
        signfield.put("order", configService.intValue("esign.signer.order", hasPlatformSign ? 2 : 1));
        signfield.put("sealType", configService.value("esign.signer.seal-type", "0"));
        signfield.put("signType", configService.intValue("esign.signer.sign-type", 0));
        boolean assignedPos = configService.booleanValue("esign.signer.assigned-pos", false);
        signfield.put("assignedPosbean", assignedPos);
        if (assignedPos) {
            signfield.put("posBean", signPosition("esign.signer"));
        }

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("signfields", Collections.singletonList(signfield));
        officialRequest(HttpMethod.POST, "/v1/signflows/" + encodePath(flowId) + "/signfields/handSign", body);
    }

    private void addOfficialPlatformSignIfNeeded(String flowId, String fileId) {
        if (!platformSignEnabled()) {
            return;
        }
        Map<String, Object> signfield = new LinkedHashMap<String, Object>();
        signfield.put("fileId", fileId);
        signfield.put("order", configService.intValue("esign.platform-sign.order", 1));
        signfield.put("posBean", signPosition("esign.platform-sign"));
        signfield.put("signType", configService.intValue("esign.platform-sign.sign-type", 1));
        String sealId = configService.value("esign.seal-id", "");
        if (StringUtils.hasText(sealId)) {
            signfield.put("sealId", sealId);
        }

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("signfields", Collections.singletonList(signfield));
        officialRequest(HttpMethod.POST, "/v1/signflows/" + encodePath(flowId) + "/signfields/platformSign", body);
    }

    private void startOfficialFlow(String flowId) {
        officialRequest(HttpMethod.PUT, "/v1/signflows/" + encodePath(flowId) + "/start", null);
    }

    private String getOfficialExecuteUrl(String flowId, String signerAccountId) {
        try {
            Map<String, Object> response = officialRequest(HttpMethod.GET,
                    "/v1/signflows/" + encodePath(flowId) + "/executeUrl?accountId=" + encodeQuery(signerAccountId), null);
            String shortUrl = text(response.get("shortUrl"));
            return StringUtils.hasText(shortUrl) ? shortUrl : text(response.get("url"));
        } catch (Exception ex) {
            log.info("e签宝签署 H5 地址获取失败，将只返回小程序签署参数: flowId={}, msg={}",
                    flowId, sanitizeMessage(ex.getMessage()));
            return null;
        }
    }

    private String uploadGatewayFile(RentViews.RentalContractView localPdf) {
        String path = configService.value("esign.upload-path", "");
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("中台配置缺失: esign.upload-path");
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("contentMd5", "");
        body.put("fileName", localPdf.getContractNo() + ".pdf");
        body.put("fileUrl", localPdf.getContractPdfUrl());
        Map<String, Object> response = gatewayRequest(HttpMethod.POST, path, body);
        String fileId = text(response.get("fileId"));
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalStateException("电子合同上传文件未返回 fileId");
        }
        return fileId;
    }

    private Map<String, Object> createGatewayFlow(Order order, RentViews.RentalContractView localPdf, String fileId) {
        String path = configService.value("esign.sign-flow-path", "");
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("中台配置缺失: esign.sign-flow-path");
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("orderId", order.getOrderId());
        body.put("contractNo", localPdf.getContractNo());
        body.put("contractName", localPdf.getContractTitle());
        body.put("fileId", fileId);
        body.put("buyerName", localPdf.getBuyerName());
        body.put("buyerPhone", localPdf.getBuyerPhone());
        body.put("buyerIdCard", localPdf.getBuyerIdCard());
        body.put("notifyUrl", configService.value("esign.notify-url", ""));
        Map<String, Object> response = gatewayRequest(HttpMethod.POST, path, body);
        if (!StringUtils.hasText(text(response.get("flowId")))) {
            throw new IllegalStateException("电子合同签署流程未返回 flowId");
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> officialRequest(HttpMethod method, String path, Map<String, Object> body) {
        String url = buildUrl(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X-Tsign-Open-App-Id", configService.value("esign.app-id", ""));
        headers.set("X-Tsign-Open-Token", accessToken());
        ResponseEntity<Map> response = restTemplate.exchange(url, method, new HttpEntity<Map<String, Object>>(body, headers), Map.class);
        return unwrapOfficialResponse(response.getBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> gatewayRequest(HttpMethod method, String path, Map<String, Object> body) {
        String url = buildUrl(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", configService.value("esign.auth-token", ""));
        String appId = configService.value("esign.app-id", "");
        if (StringUtils.hasText(appId)) {
            headers.set("X-Tsign-Open-App-Id", appId);
        }
        ResponseEntity<Map> response = restTemplate.exchange(url, method, new HttpEntity<Map<String, Object>>(body, headers), Map.class);
        Map<String, Object> result = response.getBody();
        if (result == null) {
            throw new IllegalStateException("电子签署接口返回为空");
        }
        Object data = result.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapOfficialResponse(Map response) {
        if (response == null) {
            throw new IllegalStateException("e签宝接口返回为空");
        }
        Object code = response.get("code");
        if (code != null && !"0".equals(String.valueOf(code))) {
            throw new IllegalStateException("e签宝接口失败: code=" + code + ", message=" + text(response.get("message")));
        }
        Object data = response.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        Map<String, Object> empty = new LinkedHashMap<String, Object>();
        if (data != null) {
            empty.put("data", data);
        }
        return empty;
    }

    @SuppressWarnings("unchecked")
    private String accessToken() {
        long now = System.currentTimeMillis();
        AccessToken current = cachedToken;
        if (current != null && StringUtils.hasText(current.token) && current.expiresAt > now) {
            return current.token;
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && StringUtils.hasText(current.token) && current.expiresAt > System.currentTimeMillis()) {
                return current.token;
            }
            String appId = configService.value("esign.app-id", "");
            String appSecret = configService.value("esign.app-secret", "");
            String url = buildUrl("/v1/oauth2/access_token")
                    + "?appId=" + encodeQuery(appId)
                    + "&secret=" + encodeQuery(appSecret)
                    + "&grantType=client_credentials";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), Map.class);
            Map<String, Object> result = response.getBody();
            if (result == null) {
                throw new IllegalStateException("e签宝 OAuthToken 返回为空");
            }
            Object success = result.get("success");
            if (success != null && !Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("e签宝 OAuthToken 获取失败: " + text(result.get("message")));
            }
            Object data = result.get("data");
            if (!(data instanceof Map)) {
                throw new IllegalStateException("e签宝 OAuthToken 未返回 data");
            }
            Map<String, Object> tokenData = (Map<String, Object>) data;
            String token = text(tokenData.get("token"));
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("e签宝 OAuthToken 未返回 token");
            }
            cachedToken = new AccessToken(token, resolveTokenExpiresAt(tokenData.get("expiresIn")));
            return token;
        }
    }

    private String buildUrl(String path) {
        String baseUrl = configService.value("esign.base-url", "");
        return baseUrl.replaceAll("/$", "") + "/" + path.replaceAll("^/", "");
    }

    private boolean useOfficialOAuth2() {
        String mode = configService.value("esign.auth-mode", "");
        return OAUTH2_MODE.equalsIgnoreCase(mode) || StringUtils.hasText(configService.value("esign.app-secret", ""));
    }

    private boolean platformSignEnabled() {
        return configService.booleanValue("esign.platform-sign.enabled", false);
    }

    private Map<String, Object> signPosition(String prefix) {
        Map<String, Object> posBean = new LinkedHashMap<String, Object>();
        posBean.put("posPage", configService.value(prefix + ".pos-page", "1"));
        posBean.put("posX", doubleValue(prefix + ".pos-x", 420D));
        posBean.put("posY", doubleValue(prefix + ".pos-y", 680D));
        String width = configService.value(prefix + ".width", "");
        if (StringUtils.hasText(width)) {
            posBean.put("width", doubleValue(prefix + ".width", 120D));
        }
        return posBean;
    }

    private void validateSigner(RentViews.RentalContractView localPdf) {
        List<String> missing = new ArrayList<String>();
        if (!StringUtils.hasText(localPdf.getBuyerName())) {
            missing.add("姓名");
        }
        if (!StringUtils.hasText(localPdf.getBuyerPhone())) {
            missing.add("手机号");
        }
        if (!StringUtils.hasText(localPdf.getBuyerIdCard())) {
            missing.add("身份证号");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("电子合同签署人信息缺失: " + String.join("、", missing));
        }
    }

    private String buildThirdPartyUserId(Order order, RentViews.RentalContractView localPdf) {
        String prefix = configService.value("esign.account.third-party-prefix", "rent-user-");
        String source = order == null ? null : order.getUserUuid();
        if (!StringUtils.hasText(source)) {
            source = localPdf.getBuyerPhone();
        }
        if (!StringUtils.hasText(source)) {
            source = localPdf.getBuyerIdCard();
        }
        if (!StringUtils.hasText(source) && order != null && order.getOrderId() != null) {
            source = String.valueOf(order.getOrderId());
        }
        return prefix + source;
    }

    private String mapOfficialFlowStatus(Object flowStatus) {
        String value = text(flowStatus);
        if ("2".equals(value)) {
            return CapabilityConstants.CONTRACT_COMPLETED;
        }
        if ("3".equals(value) || "4".equals(value) || "5".equals(value) || "7".equals(value)) {
            return CapabilityConstants.CONTRACT_FAILED;
        }
        return CapabilityConstants.CONTRACT_SIGNING;
    }

    private String mapGatewayStatus(String status) {
        String value = status == null ? "" : status.toUpperCase();
        if (value.contains("COMPLETE") || value.contains("FINISH")) {
            return CapabilityConstants.CONTRACT_COMPLETED;
        }
        if (value.contains("SIGN")) {
            return CapabilityConstants.CONTRACT_SIGNING;
        }
        return value;
    }

    private String md5Base64(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return Base64.getEncoder().encodeToString(digest.digest(bytes));
    }

    private long resolveTokenExpiresAt(Object expiresInValue) {
        long now = System.currentTimeMillis();
        long fallback = now + 90L * 60L * 1000L;
        if (expiresInValue == null) {
            return fallback;
        }
        try {
            long raw = Long.parseLong(String.valueOf(expiresInValue));
            long expiresAt = raw > 100000000000L ? raw : now + raw * 1000L;
            return Math.max(now + 60L * 1000L, expiresAt - 5L * 60L * 1000L);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double doubleValue(String key, double defaultValue) {
        String value = configService.value(key, "");
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void requireConfigured(String key) {
        if (!StringUtils.hasText(configService.value(key, ""))) {
            throw new IllegalStateException("中台配置缺失: " + key);
        }
    }

    private String encodeQuery(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ex) {
            return value == null ? "" : value;
        }
    }

    private String encodePath(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("secret=[^&\\s]+", "secret=***")
                .replaceAll("X-Tsign-Open-Token[:=][^,\\s]+", "X-Tsign-Open-Token=***");
    }

    private Map<String, Object> maskCallback(Map<String, Object> payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<String, Object>(payload);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class AccessToken {
        private final String token;
        private final long expiresAt;

        private AccessToken(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
