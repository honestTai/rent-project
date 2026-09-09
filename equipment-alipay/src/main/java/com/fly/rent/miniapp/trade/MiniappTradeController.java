package com.fly.rent.miniapp.trade;

import com.alipay.api.AlipayApiException;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.common.user.RentProfileService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.NoUseException;
import com.fly.rent.entity.Result;
import com.fly.rent.legacy.dto.apilyRentUtil.RentOrderCreateParam;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.ApiResponse;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.RentOrderVto;
import com.fly.rent.legacy.service.RentOrderCheckService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序交易控制器（参照 ApilyRentUp 简化）。
 * 直接调用 legacy service，不再经过 MiniappTradeService 中间层。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rent/v1/miniapp/trade")
public class MiniappTradeController {

    private final RentCurrentUserService currentUserService;
    private final RentProfileService profileService;
    private final RentOrderService rentOrderService;
    private final RentOrderCheckService rentOrderCheckService;

    /**
     * 租赁前判断接口（与老 ApilyRentUp.check 一致）
     * 传入 param 为 outSkuId，根据 outSkuId 查询本系统商品的押金、租金并回传
     */
    @PostMapping("/check")
    public Result getCheckResult(@RequestParam(value = "param", required = false) String param) {
        ApiResponse result = rentOrderCheckService.check(param);
        return ResultUtil.success(result);
    }

    /**
     * 租赁前判断接口（别名，body 传 JSON）
     * 从 JSON 中提取 skuId，查询商品的押金、租金等信息
     */
    @PostMapping("/preview")
    public Result preview(@RequestBody(required = false) Map<String, Object> body) {
        String skuId = "";
        if (body != null) {
            Object val = body.getOrDefault("skuId", body.get("param"));
            if (val != null) {
                skuId = String.valueOf(val);
            }
        }
        ApiResponse result = rentOrderCheckService.check(skuId, parseInstallmentCount(body), parseDuration(body));
        return ResultUtil.success(result);
    }

    /**
     * 租赁订单创建（与老 ApilyRentUp.creatRentOrder 一致）
     * 根据 sourceId 和 apiResponse 创建商家侧订单
     */
    @PostMapping("/creatRentOrder")
    public Result creatRentOrder(@RequestBody RentOrderCreateParam rentOrderCreateParam) throws AlipayApiException {
        return doCreateRentOrder(rentOrderCreateParam);
    }

    /**
     * 租赁订单创建（REST 风格路径）。仅生成订单，不记台账。
     */
    @PostMapping("/orders")
    public Result createOrder(@RequestBody RentOrderCreateParam rentOrderCreateParam) throws AlipayApiException {
        return doCreateRentOrder(rentOrderCreateParam);
    }

    private Result doCreateRentOrder(RentOrderCreateParam rentOrderCreateParam) throws AlipayApiException {
        try {
            String userUuid = currentUserService.requireUserUuid();
            if (!profileService.hasBoundPhone(userUuid)) {
                throw new NoUseException("下单前请先绑定手机号");
            }
            RentOrderVto result = rentOrderService.createRentOrder(rentOrderCreateParam, userUuid);
            return ResultUtil.success(result);
        } catch (NoUseException e) {
            log.warn("创建订单业务校验失败: {}", e.getMessage());
            RentOrderVto rentOrderVto = new RentOrderVto();
            rentOrderVto.setSuccess(false);
            rentOrderVto.setErrorMsg(e.getMessage());
            rentOrderVto.setErrorCode(AlipayRentConstants.ERROR_CODE_OTHER);
            return ResultUtil.success(rentOrderVto);
        } catch (RuntimeException e) {
            log.error("创建订单异常", e);
            RentOrderVto rentOrderVto = new RentOrderVto();
            rentOrderVto.setSuccess(false);
            rentOrderVto.setErrorMsg("系统繁忙，请稍后重试");
            rentOrderVto.setErrorCode(AlipayRentConstants.ERROR_CODE_OTHER);
            return ResultUtil.success(rentOrderVto);
        }
    }

    private Integer parseInstallmentCount(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object raw = body.get("installmentCount");
        if (raw == null) {
            raw = body.get("periodTotal");
        }
        if (raw == null) {
            raw = body.get("period");
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseDuration(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object raw = body.get("duration");
        if (raw == null) {
            raw = body.get("rentDuration");
        }
        if (raw == null) {
            raw = body.get("orderDuration");
        }
        if (raw == null) {
            raw = body.get("rentPeriod");
        }
        if (raw == null) {
            raw = body.get("orderKeep");
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
