package com.fly.rent.capability;

import com.alibaba.fastjson.JSON;
import com.fly.rent.entity.ExternalCallbackLog;
import com.fly.rent.mapper.ExternalCallbackLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class CallbackLogService {

    private final ExternalCallbackLogMapper callbackLogMapper;

    public void log(String provider, String eventType, String bizId, Object payload, boolean handled) {
        ExternalCallbackLog log = new ExternalCallbackLog()
                .setProvider(provider)
                .setEventType(eventType)
                .setBizId(bizId)
                .setPayload(JSON.toJSONString(payload))
                .setHandled(handled)
                .setCreatedAt(new Date());
        callbackLogMapper.insert(log);
    }
}
