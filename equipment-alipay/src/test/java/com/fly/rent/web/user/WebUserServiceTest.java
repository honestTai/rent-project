package com.fly.rent.web.user;

import com.fly.rent.entity.Result;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.UserMapper;
import com.fly.rent.web.support.WebRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebUserServiceTest {

    @Test
    void resolveIdentityReturnsUuidByOpenid() {
        UserMapper userMapper = mock(UserMapper.class);
        User user = new User();
        user.setUserId(3);
        user.setUuid("UUID-001");
        user.setOpenid("OPENID-001");
        user.setAlipayUserId("2088PID001");
        user.setUserTitle("honest");
        user.setRealName("Tester");
        user.setUserTel("15880152827");
        when(userMapper.selectOne(any())).thenReturn(user);

        Result result = new WebUserService(userMapper)
                .resolveIdentity(WebRequest.of(mapOf("openid", " OPENID-001 ")));

        assertEquals(0, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("UUID-001", data.get("uuid"));
        assertEquals("OPENID-001", data.get("openid"));
        assertEquals("2088PID001", data.get("alipayUserId"));
        verify(userMapper).selectOne(any());
    }

    @Test
    void resolveIdentityRejectsBlankOpenid() {
        UserMapper userMapper = mock(UserMapper.class);

        Result result = new WebUserService(userMapper)
                .resolveIdentity(WebRequest.of(mapOf("openid", " ")));

        assertEquals(1, result.getCode());
        assertEquals("openid 不能为空", result.getMsg());
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void resolveIdentityReturnsErrorWhenUserMissing() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectOne(any())).thenReturn(null);

        Result result = new WebUserService(userMapper)
                .resolveIdentity(WebRequest.of(mapOf("openid", "OPENID-MISSING")));

        assertEquals(1, result.getCode());
        assertEquals("未找到对应用户", result.getMsg());
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
