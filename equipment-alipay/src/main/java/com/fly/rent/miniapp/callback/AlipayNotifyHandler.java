package com.fly.rent.miniapp.callback;

import java.util.Map;

/**
 * 支付宝异步通知处理器策略接口。
 *
 * 这里使用策略模式的目的很直接：
 * - 控制器只负责拿到表单参数；
 * - 分发器只负责验签和路由；
 * - 每类通知都由独立处理器负责，避免一个控制器里塞满 if/else。
 */
public interface AlipayNotifyHandler {

    boolean supports(Map<String, String> params);

    String handle(Map<String, String> params);
}
