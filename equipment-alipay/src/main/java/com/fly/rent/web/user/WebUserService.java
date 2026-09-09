package com.fly.rent.web.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.web.support.WebResponseUtil;
import com.fly.rent.entity.Result;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Web 兼容层用户管理业务服务。
 * 提供用户分页查询、免审状态管理、角色列表等能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebUserService {

    private final UserMapper userMapper;

    public Result pageUsers(WebRequest req) {
        Page<User> page = new Page<>(req.page(), req.limit());
        QueryWrapper<User> ew = new QueryWrapper<>();

        if (req.hasText("userUuid")) {
            String identity = req.text("userUuid");
            ew.and(wrapper -> wrapper
                    .eq("alipay_user_id", identity)
                    .or()
                    .eq("uuid", identity)
                    .or()
                    .eq("openid", identity));
        }
        if (req.hasText("userTitle")) {
            ew.like("nickname", req.text("userTitle"));
        }
        if (req.hasText("realName")) {
            ew.like("real_name", req.text("realName"));
        }
        if (req.hasText("idCard")) {
            ew.like("id_card", req.text("idCard"));
        }
        if (req.integer("isNoRequest") != null) {
            ew.eq("is_blocked", req.integer("isNoRequest"));
        }

        ew.orderByDesc("user_id");
        userMapper.selectPage(page, ew);
        List<User> records = page.getRecords();
        return WebResponseUtil.page(records, page.getTotal());
    }

    public Result resolveIdentity(WebRequest req) {
        String openid = firstText(req.text("openid"), req.text("alipayOpenId"), req.text("identity"));
        if (!StringUtils.hasText(openid)) {
            return WebResponseUtil.error(1, "openid 不能为空");
        }

        User user = userMapper.selectOne(new QueryWrapper<User>()
                .eq("openid", openid.trim())
                .last("LIMIT 1"));
        if (user == null) {
            return WebResponseUtil.error(1, "未找到对应用户");
        }

        return WebResponseUtil.success(userIdentityView(user));
    }

    public Result updateRequestStatus(WebRequest req) {
        String userUuid = req.requiredText("userUuid");
        Integer isNoRequest = req.integer("isNoRequest");
        if (isNoRequest == null) {
            return WebResponseUtil.error(1, "isNoRequest 不能为空");
        }

        QueryWrapper<User> ew = new QueryWrapper<>();
        ew.eq("uuid", userUuid);
        List<User> users = userMapper.selectList(ew);
        if (users.isEmpty()) {
            return WebResponseUtil.error(1, "用户不存在");
        }

        User user = users.get(0);
        user.setIsNoRequest(isNoRequest);
        userMapper.updateById(user);

        log.info("更新用户免审状态: uuid={}, isNoRequest={}", userUuid, isNoRequest);
        return WebResponseUtil.success();
    }

    public Result listRoles() {
        List<Map<String, Object>> roles = new ArrayList<>();

        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("roleId", 1);
        admin.put("roleName", "管理员");
        roles.add(admin);

        Map<String, Object> merchant = new LinkedHashMap<>();
        merchant.put("roleId", 2);
        merchant.put("roleName", "商户");
        roles.add(merchant);

        return WebResponseUtil.success(roles);
    }

    private Map<String, Object> userIdentityView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", user.getUserId());
        view.put("uuid", user.getUuid());
        view.put("openid", user.getOpenid());
        view.put("alipayUserId", user.getAlipayUserId());
        view.put("userTitle", user.getUserTitle());
        view.put("realName", user.getRealName());
        view.put("userTel", user.getUserTel());
        return view;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
