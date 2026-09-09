package com.fly.rent.miniapp.callback.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Good;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.miniapp.callback.AlipayNotifyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 支付宝小程序商品状态通知处理器。
 *
 * <p>处理 alipay.open.app.item.status.notify，把审核通过、驳回、冻结、售罄等状态写回商品表。
 * 支付宝通知里通常给 item_id/platform_item_id；如果带 out_item_id，则兼容裸本地 ID 和 good-{goodId} 形式。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayItemStatusNotifyHandler implements AlipayNotifyHandler {

    private final GoodMapper goodMapper;

    @Override
    public boolean supports(Map<String, String> params) {
        return AlipayRentConstants.ITEM_STATUS_NOTIFY_METHOD.equals(params.get("msg_method"));
    }

    @Override
    public String handle(Map<String, String> params) {
        String bizContentStr = params.get("biz_content");
        if (!StringUtils.hasText(bizContentStr)) {
            log.warn("支付宝商品状态通知缺少 biz_content: params={}", params);
            return "success";
        }

        try {
            JSONObject bizContent = JSONUtil.parseObj(bizContentStr);
            Good good = resolveGood(bizContent);
            if (good == null) {
                log.warn("支付宝商品状态通知未匹配本地商品: bizContent={}", bizContentStr);
                return "success";
            }

            String spuStatus = bizContent.getStr("spu_status");
            String auditStatus = resolveAuditStatus(bizContent.getStr("audit_status"), spuStatus, bizContent.getStr("type"));
            JSONArray reasons = bizContent.getJSONArray("reasons");
            Long timestamp = resolveEventTime(bizContent);

            if (StringUtils.hasText(auditStatus)) {
                good.setAlipayAuditStatus(auditStatus);
            }
            if (StringUtils.hasText(spuStatus)) {
                good.setAlipaySpuStatus(spuStatus);
            }
            good.setAlipayStatusReason(reasons == null || reasons.isEmpty() ? null : JSONUtil.toJsonStr(reasons));
            good.setAlipayStatusUpdatedAt(timestamp == null ? new Date() : new Date(timestamp));
            goodMapper.updateById(good);

            log.info("支付宝商品状态通知已处理: goodId={}, itemId={}, auditStatus={}, spuStatus={}",
                    good.getGoodId(), good.getAlipayGoodsId(), auditStatus, spuStatus);
            return "success";
        } catch (Exception ex) {
            log.error("支付宝商品状态通知处理失败: bizContent={}", bizContentStr, ex);
            return "failure";
        }
    }

    /**
     * 根据支付宝通知里的商品标识匹配本地商品。
     *
     * @param bizContent 支付宝通知业务内容
     * @return 匹配到的商品；未匹配时返回 null
     */
    private Good resolveGood(JSONObject bizContent) {
        String outItemId = bizContent.getStr("out_item_id");
        Good good = resolveGoodByOutItemId(outItemId);
        if (good != null) {
            return good;
        }

        String itemId = bizContent.getStr("item_id");
        String platformItemId = bizContent.getStr("platform_item_id");
        if (!StringUtils.hasText(itemId) && !StringUtils.hasText(platformItemId)) {
            return null;
        }
        QueryWrapper<Good> wrapper = new QueryWrapper<>();
        wrapper.and(query -> {
            boolean hasCondition = false;
            if (StringUtils.hasText(itemId)) {
                query.eq("alipay_goods_id", itemId);
                hasCondition = true;
            }
            if (StringUtils.hasText(platformItemId)) {
                if (hasCondition) {
                    query.or();
                }
                query.eq("alipay_goods_id", platformItemId);
            }
        });
        List<Good> goods = goodMapper.selectList(wrapper);
        return goods == null || goods.isEmpty() ? null : goods.get(0);
    }

    /**
     * 从商家侧 out_item_id 解析本地商品。
     *
     * @param outItemId 支付宝通知里的商家侧商品 ID，格式通常为裸本地 ID 或 good-{goodId}
     * @return 匹配到的商品；无法解析或未匹配时返回 null
     */
    private Good resolveGoodByOutItemId(String outItemId) {
        if (!StringUtils.hasText(outItemId)) {
            return null;
        }
        try {
            String localGoodId = outItemId.trim();
            if (localGoodId.startsWith("good-")) {
                localGoodId = localGoodId.substring("good-".length());
            }
            return goodMapper.selectById(Integer.valueOf(localGoodId));
        } catch (NumberFormatException ex) {
            log.warn("支付宝商品状态通知 out_item_id 无法解析: {}", outItemId);
            return null;
        }
    }

    /**
     * 支付宝状态通知有时只给 spu_status，这里按审核事件把列表审核列补齐。
     */
    private String resolveAuditStatus(String auditStatus, String spuStatus, String eventType) {
        if (StringUtils.hasText(auditStatus)) {
            return auditStatus;
        }
        if (!"ITEM_AUDIT".equals(eventType) || !StringUtils.hasText(spuStatus)) {
            return null;
        }
        if ("AVAILABLE".equals(spuStatus)) {
            return "PASS";
        }
        if ("AUDIT_REJECT".equals(spuStatus)) {
            return "REJECT";
        }
        if ("AUDITING".equals(spuStatus)) {
            return "AUDITING";
        }
        return null;
    }

    /**
     * 兼容文档里的 event_time 和历史样例里的 timestamp。
     */
    private Long resolveEventTime(JSONObject bizContent) {
        Long timestamp = bizContent.getLong("timestamp");
        if (timestamp != null) {
            return timestamp;
        }
        return bizContent.getLong("event_time");
    }
}
