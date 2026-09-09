package com.fly.rent.miniapp.order;

import com.alipay.api.AlipayObject;
import com.alipay.api.AlipayRequest;
import com.alipay.api.internal.util.AlipayHashMap;

import java.util.Map;

/**
 * 支付宝 SDK 当前版本缺少 alipay.commerce.rent.additional.upload 的生成类。
 * 这里按 SDK 生成类风格保留最小封装，仅透传 biz_content。
 */
public class AlipayCommerceRentAdditionalUploadRequest
        implements AlipayRequest<AlipayCommerceRentAdditionalUploadResponse> {

    private AlipayHashMap udfParams;
    private String apiVersion = "1.0";
    /** 支付宝接口入参，只透传 JSON 字符串，不额外建 SDK 领域模型。 */
    private String bizContent;
    private String terminalType;
    private String terminalInfo;
    private String prodCode;
    private String notifyUrl;
    private String returnUrl;
    private boolean needEncrypt = false;
    private AlipayObject bizModel = null;

    public String getBizContent() {
        return bizContent;
    }

    public void setBizContent(String bizContent) {
        this.bizContent = bizContent;
    }

    @Override
    public String getApiMethodName() {
        return "alipay.commerce.rent.additional.upload";
    }

    /**
     * 当前 SDK 没有生成类，本地封装只把 biz_content 放进文本参数。
     */
    @Override
    public Map<String, String> getTextParams() {
        AlipayHashMap txtParams = new AlipayHashMap();
        txtParams.put("biz_content", this.bizContent);
        if (udfParams != null) {
            txtParams.putAll(this.udfParams);
        }
        return txtParams;
    }

    public void putOtherTextParam(String key, String value) {
        if (this.udfParams == null) {
            this.udfParams = new AlipayHashMap();
        }
        this.udfParams.put(key, value);
    }

    @Override
    public Class<AlipayCommerceRentAdditionalUploadResponse> getResponseClass() {
        return AlipayCommerceRentAdditionalUploadResponse.class;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    public String getTerminalType() {
        return terminalType;
    }

    @Override
    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
    }

    @Override
    public String getTerminalInfo() {
        return terminalInfo;
    }

    @Override
    public void setTerminalInfo(String terminalInfo) {
        this.terminalInfo = terminalInfo;
    }

    @Override
    public String getProdCode() {
        return prodCode;
    }

    @Override
    public void setProdCode(String prodCode) {
        this.prodCode = prodCode;
    }

    @Override
    public String getNotifyUrl() {
        return notifyUrl;
    }

    @Override
    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    @Override
    public String getReturnUrl() {
        return returnUrl;
    }

    @Override
    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    @Override
    public boolean isNeedEncrypt() {
        return needEncrypt;
    }

    @Override
    public void setNeedEncrypt(boolean needEncrypt) {
        this.needEncrypt = needEncrypt;
    }

    @Override
    public AlipayObject getBizModel() {
        return bizModel;
    }

    @Override
    public void setBizModel(AlipayObject bizModel) {
        this.bizModel = bizModel;
    }
}
