package com.fly.rent.legacy.dto.apilyRentUtil;

import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.GoodMapper;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.common.zhongtai.config.ZhongtaiConfigService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 支付宝基础配置类
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
@Component
public class BaseApily {

    @Autowired
    private ZhongtaiConfigService zhongtaiConfigService;

    public AlipayClient alipayClient;

    @Autowired
    public AttrMapper attrMapper;

    @Autowired
    public  GoodMapper goodMapper;

    /**
     * 初始化支付宝客户端。
     * <p>
     * 支付宝网关、appId、私钥、公钥统一从中台配置读取。
     * </p>
     */
    @PostConstruct
    public void init() {
        alipayClient = new DefaultAlipayClient(
                config("alipay.openapi.gateway"),
                config("alipay.appid"),
                config("alipay.private-key"),
                "json",
                "GBK",
                config("alipay.public-key"),
                "RSA2");
    }

    /**
     * 读取中台配置。
     *
     * @param key 配置键
     * @return 配置值
     */
    private String config(String key) {
        return zhongtaiConfigService.getString("alipay", key);
    }
}

