package com.fly.rent.miniapp.order;

import com.alipay.api.AlipayApiException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.capability.buyout.RentBuyoutPaymentService;
import com.fly.rent.capability.billing.InstallmentBillService;
import com.fly.rent.capability.contract.OrderContractService;
import com.fly.rent.capability.withhold.WithholdAgreementService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.model.RentUserProfileExtra;
import com.fly.rent.common.order.RentViewAssembler;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.Result;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.RentOrderVto;
import com.fly.rent.legacy.service.RentOrderPayService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.order.dto.MiniappOrderReturnRequest;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.support.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.fly.rent.common.support.RentTimeSupport;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 小程序订单管理控制器（参照 ApilyRentUp 简化）。
 * 直接调用 legacy service，不再经过 MiniappOrderService 中间层。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rent/v1/miniapp/orders")
public class MiniappOrderController {

    private final OrderMapper orderMapper;
    private final RentCurrentUserService currentUserService;
    private final MiniappOrderAnalyticsService orderAnalyticsService;
    private final MiniappRentOperLogService miniappRentOperLogService;
    private final RentViewAssembler rentViewAssembler;
    private final RentExtensionStore rentExtensionStore;
    private final RentOrderService rentOrderService;
    private final RentOrderPayService rentOrderPayService;
    private final AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;
    private final OrderReturnRecordService orderReturnRecordService;
    private final RentContractService rentContractService;
    private final OrderContractService orderContractService;
    private final InstallmentBillService installmentBillService;
    private final WithholdAgreementService withholdAgreementService;
    private final AttrMapper attrMapper;
    private final RentBuyoutPaymentService buyoutPaymentService;

    private static final List<String> PENDING_STATUSES = Arrays.asList(
            "CREATED", "SIGNED", "APPROVED", "PAID", "PENDING_CANCEL");
    private static final List<String> RENTING_STATUSES = Arrays.asList(
            "DELIVERED", "RECEIVED", "RETURN_DELIVERED");
    private static final List<String> FINISHED_STATUSES = Arrays.asList(
            "RETURN_RECEIVED", "FINISHED", "CLOSED");
    private static final String ID_CARD_REVIEW_PENDING_UPLOAD = "PENDING_UPLOAD";
    private static final String ID_CARD_REVIEW_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String ID_CARD_REVIEW_APPROVED = "APPROVED";
    private static final String ID_CARD_REVIEW_REJECTED = "REJECTED";

    /**
     * 获取订单状态统计数据
     */
    @GetMapping("/stats")
    public Result stats() {
        String userUuid = currentUserService.requireUserUuid();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", orderMapper.selectCount(new QueryWrapper<Order>().eq("user_uuid", userUuid)));
        stats.put("pending", countByStatuses(userUuid, PENDING_STATUSES));
        stats.put("renting", countByStatuses(userUuid, RENTING_STATUSES));
        stats.put("finished", countByStatuses(userUuid, FINISHED_STATUSES));
        return ResultUtil.success(stats);
    }

    /**
     * 获取当前登录用户指定自然月的租赁分析。
     */
    @GetMapping("/analytics/monthly")
    public Result monthlyAnalytics(@RequestParam(value = "month", required = false) String month) {
        return ResultUtil.success(orderAnalyticsService.getMonthlyAnalytics(month), "success");
    }

    /** 获取当前登录用户指定自然年的租赁统计。 */
    @GetMapping("/analytics/yearly")
    public Result yearlyAnalytics(@RequestParam(value = "year", required = false) String year) {
        return ResultUtil.success(orderAnalyticsService.getYearlyAnalytics(year), "success");
    }

    /**
     * 分页查询当前用户的订单列表
     */
    @PostMapping("/query")
    public Result list(@RequestBody(required = false) Map<String, Object> body) {
        String userUuid = currentUserService.requireUserUuid();
        int page = 1;
        int pageSize = 20;
        String tab = null;
        String keyword = null;

        if (body != null) {
            if (body.get("page") != null) page = Integer.parseInt(body.get("page").toString());
            if (body.get("pageSize") != null) pageSize = Integer.parseInt(body.get("pageSize").toString());
            if (body.get("tab") != null) tab = body.get("tab").toString();
            if (body.get("keyword") != null) keyword = body.get("keyword").toString();
        }
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;

        QueryWrapper<Order> wrapper = new QueryWrapper<Order>()
                .eq("user_uuid", userUuid)
                .isNotNull("source_id")
                .ne("source_id", "")
                .orderByDesc("created_at");

        List<String> tabStatuses = resolveStatusesByTab(tab);
        if (tabStatuses != null && !tabStatuses.isEmpty()) {
            wrapper.in("alipay_status", tabStatuses);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.apply("order_no LIKE {0} OR goods_title LIKE {0}", "%" + kw + "%"));
        }

        Page<Order> mpPage = new Page<>(page, pageSize);
        orderMapper.selectPage(mpPage, wrapper);

        // 批量获取扩展信息
        List<String> orderNos = mpPage.getRecords().stream()
                .map(Order::getOrderNo)
                .collect(Collectors.toList());
        Map<String, RentOrderExtension> extensions = rentExtensionStore.loadOrderExtensions(orderNos);
        RentUserProfileExtra userExtra = rentExtensionStore.loadUserProfileExtra(userUuid);

        List<RentViews.RentalOrderView> views = mpPage.getRecords().stream()
                .map(order -> {
                    return buildOrderView(order, extensions.get(order.getOrderNo()), userExtra);
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("total", mpPage.getTotal());
        data.put("list", views);
        return ResultUtil.success(data);
    }

    /**
     * 查询订单详情
     * 返回格式为 { order: RentalOrderView }，与小程序端 fetchOrderDetail 期望的 res.order 一致。
     */
    @GetMapping("/{orderId}")
    public Result detail(@PathVariable("orderId") Integer orderId) {
        Order order;
        try {
            order = requireOwnOrder(orderId);
        } catch (IllegalArgumentException e) {
            return ResultUtil.error(-1, e.getMessage());
        }

        autoFillIdentityIfVerified(order);

        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        RentUserProfileExtra userExtra = rentExtensionStore.loadUserProfileExtra(order.getUserUuid());

        RentViews.RentalOrderView view = buildOrderView(order, extension, userExtra);

        Map<String, Object> data = new HashMap<>();
        data.put("order", view);
        data.put("returnRecord", orderReturnRecordService.toView(orderReturnRecordService.findByOrderId(order.getOrderId())));
        return ResultUtil.success(data);
    }

    /**
     * 查询当前用户订单的租赁协议预览。
     * 身份证号按签约合同要求完整返回，但仅限订单本人访问。
     */
    @GetMapping("/{orderId}/contract-preview")
    public Result contractPreview(@PathVariable("orderId") Integer orderId) {
        String userUuid = currentUserService.requireUserUuid();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return ResultUtil.error(-1, "订单不存在");
        }
        if (!userUuid.equals(order.getUserUuid())) {
            return ResultUtil.error(-1, "该订单不属于当前用户");
        }
        if (isContractReadyStatus(order.getAlipayStatus()) && !StringUtils.hasText(order.getContractPdfUrl())) {
            rentContractService.generateSignedContractAsync(order.getOrderId(), "协议预览补偿");
        }
        return ResultUtil.success(rentContractService.buildContractView(order));
    }

    @GetMapping("/{orderId}/esign-contract")
    public Result esignContract(@PathVariable("orderId") Integer orderId) {
        try {
            return ResultUtil.success(orderContractService.getContract(orderId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{orderId}/esign-contract/sign")
    public Result startEsignContract(@PathVariable("orderId") Integer orderId) {
        try {
            return ResultUtil.success(orderContractService.startSign(orderId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
    }

    @GetMapping("/{orderId}/installment-bills")
    public Result installmentBills(@PathVariable("orderId") Integer orderId) {
        try {
            return ResultUtil.success(installmentBillService.queryPlan(orderId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{orderId}/installment-bills/{billId}/pay")
    public Result payInstallmentBill(@PathVariable("orderId") Integer orderId,
                                     @PathVariable("billId") Long billId) {
        try {
            return ResultUtil.success(installmentBillService.payBill(orderId, billId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{orderId}/withhold/sign")
    public Result withholdSign(@PathVariable("orderId") Integer orderId) {
        try {
            return ResultUtil.success(withholdAgreementService.sign(orderId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
    }

    private boolean isContractReadyStatus(String status) {
        return Arrays.asList(
                AlipayRentConstants.STATUS_SIGNED,
                AlipayRentConstants.STATUS_APPROVED,
                AlipayRentConstants.STATUS_PAID,
                AlipayRentConstants.STATUS_DELIVERED,
                AlipayRentConstants.STATUS_RECEIVED,
                AlipayRentConstants.STATUS_RETURN_DELIVERED,
                AlipayRentConstants.STATUS_RETURN_RECEIVED,
                AlipayRentConstants.STATUS_FINISHED
        ).contains(status);
    }

    /**
     * 租赁订单支付（参照 ApilyRentUp.rentOrderPay）
     */
    @PostMapping("/{orderId}/pay")
    public Result rentOrderPay(@PathVariable("orderId") Integer orderId) {
        Order order;
        try {
            order = requireOwnOrder(orderId);
            RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
            if (extension != null && Boolean.TRUE.equals(extension.getIdCardPhotoRequired())) {
                if (extension.getIdCardPhotoUrls() == null || extension.getIdCardPhotoUrls().size() < 2) {
                    return ResultUtil.error(-1, "请先上传身份证正反面照片后再支付");
                }
                if (!ID_CARD_REVIEW_APPROVED.equals(resolveIdCardPhotoReviewStatus(extension))) {
                    return ResultUtil.error(-1, "身份证照片需商家审核通过后才能支付");
                }
            }
            String tradeNo = rentOrderPayService.pay(order);
            Map<String, Object> data = new HashMap<>();
            data.put("tradeNO", tradeNo);
            return ResultUtil.success(data);
        } catch (IllegalArgumentException e) {
            return ResultUtil.error(-1, e.getMessage());
        } catch (AlipayApiException e) {
            log.error("订单支付失败: orderId={}", orderId, e);
            return ResultUtil.error(-1, "支付失败，请稍后重试");
        }
    }

    @PostMapping("/{orderId}/buyout/pay")
    public Result payBuyout(@PathVariable("orderId") Integer orderId) {
        try {
            Order order = requireOwnOrder(orderId);
            return ResultUtil.success(buyoutPaymentService.payBuyout(order));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResultUtil.error(-1, e.getMessage());
        } catch (AlipayApiException e) {
            log.error("订单买断支付失败: orderId={}", orderId, e);
            return ResultUtil.error(-1, "买断支付失败，请稍后重试");
        }
    }

    /**
     * 保存用户支付前上传的身份证照片。
     */
    @PostMapping("/{orderId}/identity-photos")
    public Result saveIdentityPhotos(
            @PathVariable("orderId") Integer orderId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String userUuid = currentUserService.requireUserUuid();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return ResultUtil.error(-1, "订单不存在");
        }
        if (!userUuid.equals(order.getUserUuid())) {
            return ResultUtil.error(-1, "该订单不属于当前用户");
        }
        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        if (extension == null) {
            extension = new RentOrderExtension();
        }
        if (!Boolean.TRUE.equals(extension.getIdCardPhotoRequired())) {
            return ResultUtil.error(-1, "当前订单无需上传身份证照片");
        }
        if (ID_CARD_REVIEW_APPROVED.equals(resolveIdCardPhotoReviewStatus(extension))) {
            return ResultUtil.error(-1, "身份证照片已审核通过，无需重复上传");
        }
        List<String> photoUrls = parsePhotoUrls(body == null ? null : body.get("photoUrls"));
        if (photoUrls.size() < 2) {
            return ResultUtil.error(-1, "请上传身份证正反面照片");
        }
        extension.setIdCardPhotoUrls(photoUrls);
        extension.setIdCardPhotoUploadedAt(System.currentTimeMillis());
        extension.setIdCardPhotoReviewStatus(ID_CARD_REVIEW_PENDING_REVIEW);
        extension.setIdCardPhotoReviewRemark(null);
        extension.setIdCardPhotoReviewedAt(null);
        rentExtensionStore.saveOrderExtension(order.getOrderNo(), extension);

        RentUserProfileExtra userExtra = rentExtensionStore.loadUserProfileExtra(order.getUserUuid());
        RentViews.RentalOrderView view = buildOrderView(order, extension, userExtra);

        Map<String, Object> data = new HashMap<>();
        data.put("order", view);
        return ResultUtil.success(data);
    }

    /**
     * 同步订单状态（从支付宝查询最新状态并更新本地）
     * 返回更新后的订单详情，前端可据此判断是否已签约
     */
    @PostMapping("/{orderId}/sync-status")
    public Result syncStatus(@PathVariable("orderId") Integer orderId) {
        Order order;
        try {
            order = requireOwnOrder(orderId);
        } catch (IllegalArgumentException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
        if (order.getRentOrderId() == null || order.getRentOrderId().isEmpty()) {
            return ResultUtil.error(-1, "订单未关联支付宝租赁单，无法同步");
        }

        try {
            com.alipay.api.response.AlipayCommerceRentOrderQueryResponse queryResponse = rentOrderService.orderStatusSearch(order);
            if (queryResponse.isSuccess() && queryResponse.getStatus() != null) {
                String alipayStatus = queryResponse.getStatus();
                if (!alipayStatus.equals(order.getAlipayStatus())) {
                    String oldStatus = order.getAlipayStatus();
                    log.info("同步订单状态: orderId={}, {} -> {}", orderId, order.getAlipayStatus(), alipayStatus);
                    order.setAlipayStatus(alipayStatus);
                    order.setUpdateTime(new java.util.Date());
                    orderMapper.updateById(order);
                    // 小程序主动同步也会直接推进本地状态，需要补发生命周期通知。
                    alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, alipayStatus, "小程序同步");
                }
            }
        } catch (AlipayApiException e) {
            log.error("同步订单状态失败: orderId={}", orderId, e);
            return ResultUtil.error(-1, "同步状态失败，请稍后重试");
        }

        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        RentUserProfileExtra userExtra = rentExtensionStore.loadUserProfileExtra(order.getUserUuid());
        RentViews.RentalOrderView view = buildOrderView(order, extension, userExtra);

        Map<String, Object> data = new HashMap<>();
        data.put("order", view);
        return ResultUtil.success(data);
    }

    /**
     * 执行订单操作（参照 ApilyRentUp 的各个接口）
     */
    @PutMapping("/{orderId}/actions")
    public Result action(
            @PathVariable("orderId") Integer orderId,
            @RequestBody Map<String, Object> body
    ) {
        String action = body != null ? (String) body.get("action") : null;
        if (action == null || action.trim().isEmpty()) {
            return ResultUtil.error(-1, "action 不能为空");
        }

        try {
            Order order = requireOwnOrder(orderId);
            switch (action.trim().toLowerCase()) {
                case "refund":
                    return miniappRentOperLogService.applyRefund(order, body);
                case "confirmreceipt":
                    return miniappRentOperLogService.confirmReceipt(order, body);
                case "returndevice":
                    return miniappRentOperLogService.returnDevice(order, body);
                case "renewrent":
                    return renewRent(order, body);
                default:
                    return ResultUtil.error(-1, "不支持的 action: " + action);
            }
        } catch (IllegalArgumentException e) {
            return ResultUtil.error(-1, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("订单操作失败: orderId={}, action={}, msg={}", orderId, action, e.getMessage());
            return ResultUtil.error(-1, e.getMessage());
        } catch (AlipayApiException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
    }

    /**
     * 订单归还（参照 ApilyRentUp.rentOrderReturn）
     */
    @PostMapping("/return")
    public Result rentOrderReturn(@RequestBody MiniappOrderReturnRequest request) throws AlipayApiException {
        try {
            if (request == null || request.getOrderId() == null) {
                return ResultUtil.error(-1, "订单不存在");
            }
            Order orderDb = requireOwnOrder(request.getOrderId());
            if (request.getCourCode() != null) orderDb.setCourCode(request.getCourCode());
            if (request.getCourno() != null) orderDb.setCourno(request.getCourno());
            if (request.getCourName() != null) orderDb.setCourName(request.getCourName());
            if (request.getReturnType() != null) orderDb.setReturnType(request.getReturnType());
            Map<String, Object> body = buildReturnBody(request);
            return miniappRentOperLogService.returnDevice(orderDb, body);
        } catch (IllegalArgumentException e) {
            return ResultUtil.error(-1, e.getMessage());
        }
    }

    /**
     * 将小程序寄回请求转换为台账和服务层通用 Map。
     * 保留物流、照片、说明等完整寄回资料，避免台账只记录 returnType。
     *
     * @param request 小程序寄回请求
     * @return 通用请求 Map
     */
    private Map<String, Object> buildReturnBody(MiniappOrderReturnRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("returnType", request.getReturnType());
        body.put("courCode", request.getCourCode());
        body.put("courno", request.getCourno());
        body.put("courName", request.getCourName());
        body.put("photoUrls", request.getPhotoUrls());
        body.put("returnPhotos", request.getReturnPhotos());
        body.put("detailRemark", request.getDetailRemark());
        body.put("detail", request.getDetail());
        return body;
    }

    private Order requireOwnOrder(Integer orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        String userUuid = currentUserService.requireUserUuid();
        if (!userUuid.equals(order.getUserUuid())) {
            throw new IllegalArgumentException("该订单不属于当前用户");
        }
        return order;
    }

    private RentViews.RentalOrderView buildOrderView(Order order, RentOrderExtension extension, RentUserProfileExtra userExtra) {
        Attr attr = order.getAttrId() == null ? null : attrMapper.selectById(order.getAttrId());
        RentViews.RentalOrderView view = rentViewAssembler.toRentalOrderView(order, null, attr, extension, userExtra);
        view.setOrderId(String.valueOf(order.getOrderId()));
        view.setBuyoutPaid(buyoutPaymentService.isBuyoutPaid(order.getOrderId()));
        return view;
    }

    private List<String> parsePhotoUrls(Object rawValue) {
        if (!(rawValue instanceof List)) {
            return Collections.emptyList();
        }
        List<?> rawList = (List<?>) rawValue;
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String value = String.valueOf(item).trim();
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * 兼容旧订单扩展的身份证照片审核状态。
     * 老数据没有状态字段时，根据是否已上传正反面推导待上传或待审核。
     */
    private String resolveIdCardPhotoReviewStatus(RentOrderExtension extension) {
        if (extension == null || !Boolean.TRUE.equals(extension.getIdCardPhotoRequired())) {
            return "NOT_REQUIRED";
        }
        if (StringUtils.hasText(extension.getIdCardPhotoReviewStatus())) {
            return extension.getIdCardPhotoReviewStatus();
        }
        return extension.getIdCardPhotoUrls() != null && extension.getIdCardPhotoUrls().size() >= 2
                ? ID_CARD_REVIEW_PENDING_REVIEW
                : ID_CARD_REVIEW_PENDING_UPLOAD;
    }

    // ==================== 辅助方法 ====================

    /**
     * 自动回填订单身份信息，优先从 Redis（RentUserProfileExtra）读取，避免每次查库。
     * - 订单无值 + 用户已认证 → 回填
     * - 订单有值 + 值一致 → 跳过，不重复写库
     * - 订单有值 + 值不一致 + 已过签约阶段 → 标记需重新认证（不自动覆盖）
     */
    private void autoFillIdentityIfVerified(Order order) {
        RentUserProfileExtra extra = rentExtensionStore.loadUserProfileExtra(order.getUserUuid());

        String userRealName = extra != null ? extra.getRealName() : null;
        String userIdCard = extra != null ? extra.getIdCard() : null;
        boolean userVerified = extra != null && Boolean.TRUE.equals(extra.getVerified())
                && StringUtils.hasText(userRealName) && StringUtils.hasText(userIdCard);

        if (!userVerified) {
            return;
        }

        String expectedAvatar = userRealName;
        String expectedEm = RentTimeSupport.maskIdCard(userIdCard);
        String currentAvatar = order.getAvatar();
        String currentEm = order.getEm();

        boolean avatarEmpty = !StringUtils.hasText(currentAvatar);
        boolean emEmpty = !StringUtils.hasText(currentEm);

        // 都有值且一致，不改库
        if (!avatarEmpty && !emEmpty
                && expectedAvatar.equals(currentAvatar) && expectedEm.equals(currentEm)) {
            return;
        }

        // 都有值但不一致，已过签约阶段的订单不自动覆盖，提示重新认证
        if (!avatarEmpty && !emEmpty
                && (!expectedAvatar.equals(currentAvatar) || !expectedEm.equals(currentEm))) {
            String status = order.getAlipayStatus();
            if (!"CREATED".equals(status) && !"SIGNED".equals(status)) {
                log.warn("订单身份信息与当前用户不一致，需重新认证: orderId={}, orderAvatar={}, userRealName={}",
                        order.getOrderId(), currentAvatar, userRealName);
                // 清空，让前端展示"身份认证"按钮
                order.setAvatar(null);
                order.setEm(null);
                orderMapper.updateById(order);
                return;
            }
        }

        // 有空值，回填
        boolean updated = false;
        if (avatarEmpty || !expectedAvatar.equals(currentAvatar)) {
            order.setAvatar(expectedAvatar);
            updated = true;
        }
        if (emEmpty || !expectedEm.equals(currentEm)) {
            order.setEm(expectedEm);
            updated = true;
        }
        if (updated) {
            orderMapper.updateById(order);
            log.info("已自动回填身份信息: orderId={}, realName={}", order.getOrderId(), userRealName);
        }
    }

    /**
     * 发起续租。
     * 续租会创建一笔新的子单，而不是直接修改原单租期，
     * 这样现有的支付、回调、售后与后台查询链路都还能继续按“一个本地订单对应一个支付宝订单”工作。
     */
    private Result renewRent(Order order, Map<String, Object> body) throws AlipayApiException {
        String userUuid = currentUserService.requireUserUuid();
        if (!userUuid.equals(order.getUserUuid())) {
            return ResultUtil.error(-1, "该订单不属于当前用户");
        }

        Integer renewDuration = parsePositiveInt(body == null ? null : body.get("renewDuration"), "renewDuration");
        String sourceId = body == null || body.get("sourceId") == null ? null : String.valueOf(body.get("sourceId")).trim();

        RentOrderVto reletResult = rentOrderService.createReletOrder(order, renewDuration, sourceId, userUuid);

        Map<String, Object> data = new HashMap<>();
        data.put("result", reletResult);
        data.put("renewDuration", renewDuration);
        data.put("sourceId", sourceId);
        data.put("originOrderId", order.getOrderId());
        data.put("originRentOrderId", order.getRentOrderId());
        return ResultUtil.success(data);
    }

    /**
     * 统一解析动作参数里的正整数，兼容前端把数值按 string/number 混传。
     */
    private Integer parsePositiveInt(Object rawValue, String fieldName) {
        if (rawValue == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        try {
            int value = Integer.parseInt(String.valueOf(rawValue).trim());
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " 必须大于 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " 格式不正确");
        }
    }

    private int countByStatuses(String userUuid, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }
        return orderMapper.selectCount(new QueryWrapper<Order>()
                .eq("user_uuid", userUuid)
                .in("alipay_status", statuses)).intValue();
    }

    private List<String> resolveStatusesByTab(String tab) {
        if (tab == null || tab.trim().isEmpty()) {
            return null;
        }
        switch (tab.trim().toLowerCase()) {
            case "pending":
                return PENDING_STATUSES;
            case "renting":
                return RENTING_STATUSES;
            case "finished":
                return FINISHED_STATUSES;
            case "all":
            default:
                return null;
        }
    }
}
