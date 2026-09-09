package com.fly.rent.miniapp.callback;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝异步回调统一入口。
 *
 * 当前入口统一承接三类通知：
 * 1. 资金授权冻结通知；
 * 2. 租赁支付通知；
 * 3. 租赁售后通知。
 *
 * 控制器只负责读取表单参数和交给分发器，具体业务逻辑由各自处理器负责。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notify")
public class AlipayFreezeNotifyController {

    private final AlipayNotifyDispatcher notifyDispatcher;

    @PostMapping("/freezeNotify")
    public String freezeNotify(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        log.info("收到支付回调: params={}", JSONUtil.toJsonStr(params));
        return notifyDispatcher.dispatch(params);
    }

    // ==================== 工具方法 ====================

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String[] values = entry.getValue();
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                valueStr.append(i == values.length - 1 ? values[i] : values[i] + ",");
            }
            result.put(entry.getKey(), valueStr.toString());
        }
        return result;
    }

}
