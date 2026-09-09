package com.fly.rent.web.rent;

import com.alipay.api.domain.FulfillmentDeliveryInfo;
import com.alipay.api.response.AlipayCommerceRentOrderAftersaleCreateResponse;
import com.alipay.api.response.AlipayCommerceRentOrderAftersaleConfirmResponse;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.aftersale.service.RentAftersaleSyncService;
import com.fly.rent.aftersale.service.RentDepositDeductService;
import com.fly.rent.capability.billing.InstallmentBillService;
import com.fly.rent.capability.contract.OrderContractService;
import com.fly.rent.capability.risk.OrderRiskService;
import com.fly.rent.capability.withhold.WithholdAgreementService;
import com.fly.rent.web.support.OperLog;
import com.fly.rent.web.support.OperLogContext;
import com.fly.rent.web.support.WebOrderOperation;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.web.support.WebResponseUtil;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.order.RentOrderReletRelationService;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.common.support.RentOrderLocator;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.RedisClient;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.OrderContract;
import com.fly.rent.entity.RentDepositDeductRecord;
import com.fly.rent.entity.RentOrderReturnRecord;
import com.fly.rent.entity.Result;
import com.fly.rent.entity.User;
import com.fly.rent.legacy.service.RentAftersaleService;
import com.fly.rent.legacy.service.RentOrderFulfillmentService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.legacy.service.exception.RentAftersaleException;
import com.fly.rent.mapper.RentDepositDeductRecordMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.UserMapper;
import com.fly.rent.miniapp.order.OrderReturnRecordService;
import com.fly.rent.miniapp.order.RentContractAlipaySyncService;
import com.fly.rent.miniapp.order.RentContractService;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 租赁组件 Web 后台服务。
 * 对接支付宝租赁组件的后台操作，包括退款、商家确认、发货、收货、完结、详情、风控、同步、关单等。
 *
 * @author HonestTat
 * @since 2026-03-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebRentComponentService {

    private static final String ID_CARD_REVIEW_PENDING_UPLOAD = "PENDING_UPLOAD";
    private static final String ID_CARD_REVIEW_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String ID_CARD_REVIEW_APPROVED = "APPROVED";
    private static final String ID_CARD_REVIEW_REJECTED = "REJECTED";
    private static final String MERCHANT_DELIVERY_SEND = "MERCHANT_DELIVERY_SEND";
    private final RestTemplate signedFileRestTemplate = new RestTemplate();

    private final OrderMapper orderMapper;
    private final RentOrderLocator orderLocator;
    private final RentOrderService legacyOrderService;
    private final RentOrderFulfillmentService legacyFulfillmentService;
    private final OrderRiskService orderRiskService;
    private final RentAftersaleService rentAftersaleService;
    private final ObjectMapper objectMapper;
    private final AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;
    private final RedisClient redisClient;
    private final RentDepositDeductRecordMapper rentDepositDeductRecordMapper;
    private final TransactionTemplate transactionTemplate;
    private final RentDepositDeductService rentDepositDeductService;
    private final OrderReturnRecordService orderReturnRecordService;
    private final RentExtensionStore rentExtensionStore;
    private final RentOrderReletRelationService reletRelationService;
    private final UserMapper userMapper;
    private final RentContractService rentContractService;
    private final RentContractAlipaySyncService rentContractAlipaySyncService;
    private final RentAftersaleSyncService rentAftersaleSyncService;
    private final OrderContractService orderContractService;
    private final InstallmentBillService installmentBillService;
    private final WithholdAgreementService withholdAgreementService;

    /**
     * 退款处理。
     * 根据 isAgree 决定同意或拒绝退款，调用支付宝退款接口后记录台账。
     *
     * @param req 请求参数，需包含 orderId、isAgree
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order refund(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Boolean isAgree = req.bool("isAgree");
        Order order = locateOrder(orderId);
        // 后台接口和前端页面保持同一套退款规则，避免只放开按钮却让接口继续拦截。
        validateRefundRequest(order, isAgree);

        WebOrderOperation op = Boolean.TRUE.equals(isAgree)
                ? WebOrderOperation.RENT_REFUND_APPROVE
                : WebOrderOperation.RENT_REFUND_REJECT;
        OperLogContext.begin(order, op, req);

        legacyOrderService.refund(order, isAgree);
        op.applyState(order);
        order.setUpdateTime(new Date());
        order.updateById();
        return order;
    }

    /**
     * 商家确认审核。
     * 根据 isAgree 决定通过或拒绝商家审核，调用支付宝确认接口后记录台账。
     *
     * @param req 请求参数，需包含 orderId、isAgree
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order merchantConfirm(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Boolean isAgree = req.bool("isAgree");
        Order order = locateOrder(orderId);
        boolean requireIdCardPhoto = Boolean.TRUE.equals(req.bool("idCardPhotoRequired"));
        boolean requireEsign = Boolean.TRUE.equals(req.bool("esignRequired"));

        WebOrderOperation op = Boolean.TRUE.equals(isAgree) && requireIdCardPhoto
                ? WebOrderOperation.IDENTITY_PHOTO_REQUIRED
                : Boolean.TRUE.equals(isAgree)
                ? WebOrderOperation.MERCHANT_CONFIRM_APPROVE
                : WebOrderOperation.MERCHANT_CONFIRM_REJECT;
        OperLogContext.begin(order, op, req);

        if (Boolean.TRUE.equals(isAgree) && requireEsign && !orderContractService.isEsignCapabilityEnabled()) {
            throw new IllegalArgumentException("电子合同未开启，请先配置 e签宝后再要求用户签署");
        }

        if (Boolean.TRUE.equals(isAgree) && requireIdCardPhoto) {
            if (!AlipayRentConstants.STATUS_SIGNED.equals(order.getAlipayStatus())) {
                throw new IllegalArgumentException("只有用户已签约订单才能要求补充身份证照片");
            }
            applyIdentityPhotoRequirement(order, true);
            applyEsignRequirement(order, requireEsign);
            return order;
        }
        if (Boolean.TRUE.equals(isAgree) && hasPendingIdentityPhotoReview(order)) {
            throw new IllegalArgumentException("身份证照片需后台二次审核通过后才能通过订单");
        }

        legacyOrderService.merchantConfirm(order, isAgree);
        op.applyState(order);
        order.setUpdateTime(new Date());
        order.updateById();
        if (Boolean.TRUE.equals(isAgree)) {
            applyIdentityPhotoRequirement(order, false);
            applyEsignRequirement(order, requireEsign);
        }
        return order;
    }

    /**
     * 身份证照片二次审核。
     * 审核通过才调用支付宝履约审核通过并推进订单到 APPROVED；审核不通过时订单仍保持 SIGNED，用户可在小程序重新上传。
     *
     * @param req 请求参数，需包含 orderId、isAgree，可选 remark
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order identityPhotoReview(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Boolean isAgree = req.bool("isAgree");
        if (isAgree == null) {
            throw new IllegalArgumentException("isAgree 不能为空");
        }

        Order order = locateOrder(orderId);
        WebOrderOperation op = Boolean.TRUE.equals(isAgree)
                ? WebOrderOperation.IDENTITY_PHOTO_APPROVE
                : WebOrderOperation.IDENTITY_PHOTO_REJECT;
        OperLogContext.begin(order, op, req);

        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        if (extension == null || !Boolean.TRUE.equals(extension.getIdCardPhotoRequired())) {
            throw new IllegalArgumentException("当前订单未要求上传身份证照片");
        }
        if (extension.getIdCardPhotoUrls() == null || extension.getIdCardPhotoUrls().size() < 2) {
            throw new IllegalArgumentException("用户尚未上传身份证正反面照片");
        }

        String remark = req.text("remark");
        if (Boolean.TRUE.equals(isAgree)) {
            if (!AlipayRentConstants.STATUS_SIGNED.equals(order.getAlipayStatus())) {
                throw new IllegalArgumentException("只有用户已签约订单才能审核通过身份证照片");
            }
            legacyOrderService.merchantConfirm(order, true);
            op.applyState(order);
            order.setUpdateTime(new Date());
            order.updateById();
            extension.setIdCardPhotoReviewStatus(ID_CARD_REVIEW_APPROVED);
            extension.setIdCardPhotoReviewRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        } else {
            extension.setIdCardPhotoReviewStatus(ID_CARD_REVIEW_REJECTED);
            extension.setIdCardPhotoReviewRemark(StringUtils.hasText(remark) ? remark.trim() : "身份证照片审核不通过，请重新上传清晰的正反面照片");
        }
        extension.setIdCardPhotoReviewedAt(System.currentTimeMillis());
        rentExtensionStore.saveOrderExtension(order.getOrderNo(), extension);
        attachOrderExtension(order, extension);
        return order;
    }

    /**
     * 发货。
     * 组装物流信息并调用支付宝发货接口，更新订单物流字段后记录台账。
     *
     * @param req 请求参数，需包含 orderId，可选 courCode、courno、courName、rentSendStatus
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order send(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        OperLogContext.begin(order, WebOrderOperation.RENT_SEND, req);

        String courCode = req.text("courCode");
        String courno = req.text("courno");
        String courName = req.text("courName");
        String rentSendStatus = req.text("rentSendStatus");
        validateShipRiskConfirmation(rentSendStatus != null ? rentSendStatus : order.getRentSendStatus(), req);

        if (courCode != null) order.setCourCode(courCode);
        if (courno != null) order.setCourno(courno);
        if (courName != null) order.setCourName(courName);
        if (rentSendStatus != null) order.setRentSendStatus(rentSendStatus);

        List<FulfillmentDeliveryInfo> deliveryList = new ArrayList<>();
        FulfillmentDeliveryInfo deliveryInfo = new FulfillmentDeliveryInfo();
        deliveryInfo.setDeliveryId(courCode != null ? courCode : "EXPRESS");
        deliveryInfo.setWaybillId(courno);
        deliveryList.add(deliveryInfo);

        legacyFulfillmentService.send(order, deliveryList);
        order.setUpdateTime(new Date());
        order.updateById();
        return order;
    }

    private void validateShipRiskConfirmation(String rentSendStatus, WebRequest req) {
        if (!MERCHANT_DELIVERY_SEND.equals(rentSendStatus)) {
            return;
        }
        if (!Boolean.TRUE.equals(req.bool("riskConfirmed"))) {
            throw new IllegalArgumentException("请先查看租安盾发货前风险并确认后再发货");
        }
    }

    /**
     * 确认收货。
     * 调用支付宝收货确认接口，将收货状态同步到本地后记录台账。
     *
     * @param req 请求参数，需包含 orderId，可选 rentSendStatus
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order confirmSend(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        OperLogContext.begin(order, WebOrderOperation.RENT_CONFIRM_RECEIVE, req);

        String rentSendStatus = req.text("rentSendStatus");
        if (rentSendStatus == null) {
            rentSendStatus = "USER_DELIVERY_RECEIVED";
        }
        if ("MERCHANT_DELIVERY_RECEIVED".equals(rentSendStatus)) {
            orderContractService.requireReceiptSignReady(order);
        }

        legacyFulfillmentService.receive(order, rentSendStatus);
        order.setUpdateTime(new Date());
        order.updateById();
        return order;
    }

    /**
     * 完结订单。
     * 调用支付宝订单完结接口，将完结状态同步到本地后记录台账。
     *
     * @param req 请求参数，需包含 orderId，可选 rentSendStatus
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order complete(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        OperLogContext.begin(order, WebOrderOperation.RENT_COMPLETE, req);

        String rentSendStatus = req.text("rentSendStatus");
        if (rentSendStatus == null) {
            rentSendStatus = "NORMAL_FINISH";
        }

        legacyFulfillmentService.finish(order, rentSendStatus);
        WebOrderOperation.RENT_COMPLETE.applyState(order);
        order.setUpdateTime(new Date());
        order.updateById();
        return orderLocator.requireByIdentifier(String.valueOf(order.getOrderId()));
    }

    /**
     * 查询支付宝租赁订单详情。
     * 调用支付宝订单查询接口，返回包含地址、价格、商品、租期账单、状态等完整信息。
     *
     * @param req 请求参数，需包含 orderId
     * @return 支付宝订单详情 Map
     */
    @SneakyThrows
    public Map<String, Object> detail(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        AlipayCommerceRentOrderQueryResponse response = legacyOrderService.orderStatusSearch(order);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", response.isSuccess());
        result.put("status", response.getStatus());
        result.put("subCode", response.getSubCode());
        result.put("subMsg", response.getSubMsg());
        result.put("addressInfo", toMap(response.getAddressInfo()));
        result.put("priceInfo", toMap(response.getPriceInfo()));
        result.put("itemInfos", response.getItemInfos());
        result.put("rentStatementInfos", response.getRentStatementInfos());
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("renewal", buildRenewalRelation(order));
        result.put("returnRecord", orderReturnRecordService.toView(orderReturnRecordService.findByOrderId(order.getOrderId())));
        return result;
    }

    /**
     * 组装后台订单详情中的续租关系。
     * 由于续租父子关系当前存储在订单扩展信息中，这里按同一用户下的订单批量读取扩展后过滤，
     * 让后台查看原单时能看到续租子单，查看续租单时也能看到父单和根单。
     *
     * @param order 当前后台查看的订单
     * @return 续租关系视图
     */
    private Map<String, Object> buildRenewalRelation(Order order) {
        return reletRelationService.buildRelationView(order);
    }

    /**
     * 查找当前订单的续租子单。
     * 查询范围限定在同一用户，避免全表扫描；再通过扩展信息里的 parentOrderNo / originRentOrderId 精确过滤。
     *
     * @param order 当前订单
     * @return 续租子单摘要列表
     */
    private List<Map<String, Object>> findRenewalChildren(Order order) {
        if (order == null || order.getUserUuid() == null || order.getOrderNo() == null) {
            return Collections.emptyList();
        }
        List<Order> candidates = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("user_uuid", order.getUserUuid())
                .ne("order_id", order.getOrderId())
                .isNotNull("order_no")
                .orderByDesc("created_at"));
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> orderNos = new ArrayList<>();
        for (Order candidate : candidates) {
            if (candidate.getOrderNo() != null) {
                orderNos.add(candidate.getOrderNo());
            }
        }
        Map<String, RentOrderExtension> extensionMap = rentExtensionStore.loadOrderExtensions(orderNos);
        List<Map<String, Object>> children = new ArrayList<>();
        for (Order candidate : candidates) {
            RentOrderExtension childExtension = extensionMap.get(candidate.getOrderNo());
            if (!isRenewalChild(order, childExtension)) {
                continue;
            }
            children.add(toRelationOrder(candidate));
        }
        return children;
    }

    /**
     * 判断候选订单是否属于当前订单的续租子单。
     * parentOrderNo 用于直接续租子单；originRentOrderId 用于多次续租时在根单详情里展示完整续租链。
     *
     * @param order 当前订单
     * @param childExtension 候选订单扩展信息
     * @return true 表示候选订单应展示为当前订单的续租单
     */
    private boolean isRenewalChild(Order order, RentOrderExtension childExtension) {
        if (childExtension == null || !AlipayRentConstants.ORDER_TYPE_RELET.equals(childExtension.getOrderType())) {
            return false;
        }
        if (order.getOrderNo() != null && order.getOrderNo().equals(childExtension.getParentOrderNo())) {
            return true;
        }
        return order.getRentOrderId() != null && order.getRentOrderId().equals(childExtension.getOriginRentOrderId());
    }

    /**
     * 根据订单号或支付宝租赁订单号解析续租关系里的关联订单摘要。
     *
     * @param identifier 订单号或支付宝租赁订单号
     * @return 订单摘要，找不到时返回 null
     */
    private Map<String, Object> resolveRelationOrder(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return null;
        }
        Order relationOrder = orderLocator.findByIdentifier(identifier);
        return relationOrder == null ? null : toRelationOrder(relationOrder);
    }

    /**
     * 转换续租关系中的订单摘要。
     * 只返回后台识别关系所需的关键字段，避免把完整订单对象直接塞进详情响应。
     *
     * @param order 订单实体
     * @return 订单摘要 Map
     */
    private Map<String, Object> toRelationOrder(Order order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderId", order.getOrderId());
        view.put("orderNo", order.getOrderNo());
        view.put("rentOrderId", order.getRentOrderId());
        view.put("alipayStatus", order.getAlipayStatus());
        view.put("orderTotal", order.getOrderTotal());
        view.put("orderDeposit", order.getOrderDeposit());
        view.put("orderRestDeposit", order.getOrderRestDeposit());
        view.put("rentStart", order.getOrderStart());
        view.put("rentEnd", order.getOrderEnd());
        view.put("createdAt", order.getCreatetime());
        return view;
    }

    /**
     * 查询用户风控信息。
     * 调用支付宝风控查询接口，返回风控原始数据。
     *
     * @param req 请求参数，需包含 orderId
     * @return 风控信息 Map
     */
    @SneakyThrows
    public Map<String, Object> riskDetail(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.putAll(orderRiskService.assess(order));
        return result;
    }

    /**
     * 同步支付宝订单状态到本地。
     * 调用支付宝订单查询接口获取最新状态，回写本地 alipayStatus 后记录台账。
     *
     * @param req 请求参数，需包含 orderId
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order sync(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        OperLogContext.begin(order, WebOrderOperation.UPDATE_BY_NO, req);

        AlipayCommerceRentOrderQueryResponse response = legacyOrderService.orderStatusSearch(order);
        String alipayStatus = response.getStatus();
        String oldStatus = order.getAlipayStatus();
        if (alipayStatus != null) {
            order.setAlipayStatus(alipayStatus);
        }
        order.setUpdateTime(new Date());
        order.updateById();
        // 后台同步直接改写了本地状态，需要在 Web 层补发生命周期通知。
        if (alipayStatus != null && !alipayStatus.equals(oldStatus)) {
            alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, alipayStatus, "后台同步");
        }
        OperLogContext.setResultBody(alipayStatus);
        return order;
    }

    /**
     * 关闭订单。
     * 调用支付宝关单接口，将本地 alipayStatus 更新为 CLOSED 后记录台账。
     *
     * @param req 请求参数，需包含 orderId
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order close(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        OperLogContext.begin(order, WebOrderOperation.CLOSE_RENT_ORDER, req);

        String oldStatus = order.getAlipayStatus();
        legacyOrderService.closeOrder(order);
        order.setAlipayStatus("CLOSED");
        order.setUpdateTime(new Date());
        order.updateById();
        // 关单是 Web 层直接落库的状态变化入口，需要在这里补发关闭通知。
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "后台关单");
        return order;
    }

    /**
     * 更新订单备注。
     * 将前端输入的备注信息保存到订单中，并记录操作台账。
     *
     * @param req 请求参数，需包含 orderId, remark
     * @return 更新后的订单
     */
    @SneakyThrows
    @OperLog
    public Order updateRemark(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        
        String remark = req.text("remark");
        if (remark == null) {
            remark = "";
        }
        
        Order order = locateOrder(orderId);
        
        OperLogContext.begin(order, WebOrderOperation.UPDATE_REMARK, req);
        
        order.setRemark(remark);
        order.setUpdateTime(new Date());
        order.updateById();
        
        return order;
    }

    /**
     * 订单列表分页。
     * 支持 orderNo、goodTitle、userTitle、tel/userTel、courno、idCard、keyword、alipayStatus、status、pickupWay、start、end。
     */
    public Result page(WebRequest req) {
        Page<Order> page = new Page<>(req.page(), req.limit());
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        if (req.hasText("orderNo")) wrapper.like("order_no", req.text("orderNo"));
        if (req.hasText("goodTitle")) wrapper.like("goods_title", req.text("goodTitle"));
        if (req.hasText("userTitle")) wrapper.like("user_name", req.text("userTitle"));
        if (req.hasText("tel")) wrapper.like("user_phone", req.text("tel"));
        if (req.hasText("userTel")) wrapper.like("user_phone", req.text("userTel"));
        if (req.hasText("courno")) wrapper.like("express_no", req.text("courno"));
        if (req.hasText("idCard")) {
            String kw = req.text("idCard").trim();
            String like = "%" + kw + "%";
            wrapper.and(w -> w.apply("id_card_front LIKE {0} OR id_card_portrait LIKE {0}", like));
        }
        if (req.hasText("keyword")) {
            String kw = req.text("keyword");
            wrapper.and(w -> w.apply("(order_no LIKE {0} OR goods_title LIKE {0} OR user_name LIKE {0} OR user_phone LIKE {0})", "%" + kw + "%"));
        }
        if (req.hasText("alipayStatus") && !"-1".equals(req.text("alipayStatus"))) wrapper.eq("alipay_status", req.text("alipayStatus"));
        if (req.integer("status") != null && req.integer("status") >= 0) wrapper.eq("status", req.integer("status"));
        if (req.integer("pickupWay") != null && req.integer("pickupWay") >= 0) wrapper.eq("support_pickup", req.integer("pickupWay"));
        Long start = req.longValue("start");
        if (start != null && start > 0) wrapper.ge("created_at", start);
        Long end = req.longValue("end");
        if (end != null && end > 0) wrapper.le("created_at", end);
        wrapper.isNotNull("source_id").ne("source_id", "");
        wrapper.orderByDesc("created_at");
        orderMapper.selectPage(page, wrapper);
        attachReturnRecords(page.getRecords());
        attachOrderExtensions(page.getRecords());
        attachAccountUsers(page.getRecords());
        return WebResponseUtil.page(page.getRecords(), page.getTotal());
    }

    /**
     * 后台人工补生成签约协议 PDF。
     *
     * @param req 请求参数，需包含 orderId 或 orderNo
     * @return 协议编号和 PDF 地址
     */
    @SneakyThrows
    public Map<String, Object> generateContractPdf(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            orderId = req.raw().get("orderNo");
        }
        Order order = locateOrder(orderId);
        if (!isContractReadyStatus(order.getAlipayStatus())) {
            throw new IllegalArgumentException("当前订单状态未完成签约，不支持生成协议PDF: " + order.getAlipayStatus());
        }
        com.fly.rent.common.dto.RentViews.RentalContractView contract =
                rentContractService.generateSignedContractNow(order.getOrderId(), "后台重新生成协议PDF", true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("contractNo", contract.getContractNo());
        result.put("contractPdfUrl", contract.getContractPdfUrl());
        result.put("contractPdfPath", contract.getContractPdfPath());
        rentContractAlipaySyncService.syncContractAfterReceive(order.getOrderId(), "后台补生成协议PDF");
        Order latest = orderMapper.selectById(order.getOrderId());
        if (latest != null) {
            result.put("contractAlipayFileId", latest.getContractAlipayFileId());
            result.put("contractAlipaySyncStatus", latest.getContractAlipaySyncStatus());
            result.put("contractAlipaySyncedAt", latest.getContractAlipaySyncedAt());
            result.put("contractAlipaySyncError", latest.getContractAlipaySyncError());
        }
        return result;
    }

    public Map<String, Object> esignContract(WebRequest req) {
        return orderContractService.getContractForAdmin(locateOrder(req.raw().get("orderId")).getOrderId());
    }

    public Map<String, Object> startEsignContract(WebRequest req) {
        return orderContractService.startSignForAdmin(locateOrder(req.raw().get("orderId")).getOrderId());
    }

    public ResponseEntity<byte[]> esignSignedFile(WebRequest req) {
        Order order = locateOrder(req.raw().get("orderId"));
        OrderContract contract = orderContractService.getSignedContractFileForAdmin(order.getOrderId());
        String fileUrl = firstMeaningful(contract.getDownloadUrl(), contract.getViewUrl(), contract.getPdfUrl());
        byte[] pdfBytes = fetchSignedFile(fileUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdfBytes.length);
        headers.setCacheControl(CacheControl.noStore().getHeaderValue());
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + signedFileName(contract) + "\"");
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    private byte[] fetchSignedFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalStateException("电子合同签署文件地址为空");
        }
        ResponseEntity<byte[]> response = signedFileRestTemplate.exchange(
                URI.create(fileUrl), HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("电子合同签署文件为空");
        }
        return body;
    }

    private String signedFileName(OrderContract contract) {
        String contractNo = firstMeaningful(contract.getContractNo(), "signed-contract");
        return contractNo.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    private String firstMeaningful(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    public Map<String, Object> installmentBills(WebRequest req) {
        return installmentBillService.queryPlanForAdmin(locateOrder(req.raw().get("orderId")).getOrderId());
    }

    public Map<String, Object> withholdSign(WebRequest req) {
        return withholdAgreementService.signForAdmin(locateOrder(req.raw().get("orderId")).getOrderId());
    }

    /**
     * 后台人工触发租赁合同回传支付宝。
     * 该入口只做补偿触发和结果查询，具体幂等、失败落库、台账记录统一由 RentContractAlipaySyncService 处理。
     *
     * @param req 请求参数，需包含 orderId 或 orderNo
     * @return 合同回传状态、支付宝 file_id 和最近失败原因
     */
    public Map<String, Object> syncContractToAlipay(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            orderId = req.raw().get("orderNo");
        }
        Order order = locateOrder(orderId);
        rentContractAlipaySyncService.syncContractAfterReceive(order.getOrderId(), "后台手动回传协议");
        Order latest = orderMapper.selectById(order.getOrderId());
        if (latest == null) {
            latest = order;
        }
        return buildContractSyncResult(latest);
    }

    /**
     * 统一组装前端协议页需要展示的合同回传结果。
     */
    private Map<String, Object> buildContractSyncResult(Order order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("contractPdfUrl", order.getContractPdfUrl());
        result.put("contractPdfPath", order.getContractPdfPath());
        result.put("contractAlipayFileId", order.getContractAlipayFileId());
        result.put("contractAlipaySyncStatus", order.getContractAlipaySyncStatus());
        result.put("contractAlipaySyncedAt", order.getContractAlipaySyncedAt());
        result.put("contractAlipaySyncError", order.getContractAlipaySyncError());
        if ("SUCCESS".equals(order.getContractAlipaySyncStatus())) {
            result.put("message", "租赁合同已回传支付宝");
        } else if ("FAILED".equals(order.getContractAlipaySyncStatus())) {
            result.put("message", order.getContractAlipaySyncError());
        } else if (!isContractAfterReceiveStatus(order.getAlipayStatus())) {
            result.put("message", "订单尚未确认收货，暂不回传合同");
        } else {
            result.put("message", "合同回传已触发，请稍后刷新查看结果");
        }
        return result;
    }

    /**
     * 查询订单用户寄回记录。
     * 后台点击“查看寄回”时使用，返回用户提交的寄回物流、照片和说明。
     *
     * @param req 请求参数，需包含 orderId 或 orderNo
     * @return 寄回记录展示对象；无记录时返回空 Map
     */
    public Map<String, Object> returnRecord(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) {
            orderId = req.raw().get("orderNo");
        }
        Order order = locateOrder(orderId);
        Map<String, Object> view = orderReturnRecordService.toView(orderReturnRecordService.findByOrderId(order.getOrderId()));
        return view == null ? Collections.emptyMap() : view;
    }

    /**
     * 押金/预授权支付情况查询。
     * 调用支付宝「租赁订单查询」接口获取最新押金与账单状态，并返回本地订单押金字段供对照。
     *
     * @param req 请求参数，需包含 orderId 或 orderNo
     * @return 支付宝侧：success、status、priceInfo、rentStatementInfos；本地：orderId、orderNo、rentOrderId、authNo、paymentTradeNo、orderDeposit、remainingDeposit、alipayStatus
     */
    @SneakyThrows
    public Map<String, Object> depositQuery(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        if (orderId == null) orderId = req.raw().get("orderNo");
        Order order = locateOrder(orderId);
        AlipayCommerceRentOrderQueryResponse response = legacyOrderService.orderStatusSearch(order);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", response.isSuccess());
        result.put("subCode", response.getSubCode());
        result.put("subMsg", response.getSubMsg());
        result.put("status", response.getStatus());
        result.put("priceInfo", toMap(response.getPriceInfo()));
        result.put("rentStatementInfos", response.getRentStatementInfos());

        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("rentOrderId", order.getRentOrderId());
        result.put("authNo", order.getOrderAuthNo());
        result.put("paymentTradeNo", order.getPaymentTradeNo());
        result.put("orderDeposit", order.getOrderDeposit());
        result.put("remainingDeposit", order.getOrderRestDeposit());
        result.put("alipayStatus", order.getAlipayStatus());

        result.put("fundAuthDetail", null);
        result.put("depositTypeMsg", "已停用资金授权操作明细查询，请以支付宝租赁订单查询和本地押金字段为准");
        return result;
    }

    /**
     * 扣减押金。
     * 校验订单与金额后，按租赁售后创建+确认流程发起赔付或违约金扣减，并写入单独扣减记录。
     *
     * @param req 请求参数，需包含 orderId、deductAmount、feeType、reasonCode，可选 remark、operatorId、operatorName
     * @return 扣减结果
     */
    @SneakyThrows
    @OperLog
    public Map<String, Object> deductDeposit(WebRequest req) {
        Object orderId = req.raw().get("orderId");
        Order order = locateOrder(orderId);
        OperLogContext.begin(order, WebOrderOperation.DEDUCT_DEPOSIT, req);
        OperLogContext.setDescription("扣减押金-申请赔付售后");
        return rentDepositDeductService.deductDeposit(req);
    }

    /**
     * 查询订单下的扣减记录列表。
     */
    public List<Map<String, Object>> deductRecords(WebRequest req) {
        return rentDepositDeductService.deductRecords(req);
    }

    /**
     * 分页查询订单下的扣减记录列表。
     */
    public Map<String, Object> deductRecordsPage(WebRequest req) {
        return rentDepositDeductService.deductRecordsPage(req);
    }

    /**
     * 对已创建售后单继续执行确认。
     */
    @SneakyThrows
    @OperLog
    public Map<String, Object> confirmDeductRecord(WebRequest req) {
        Integer recordId = req.integer("recordId");
        if (recordId == null) {
            throw new IllegalArgumentException("recordId 不能为空");
        }
        RentDepositDeductRecord record = rentDepositDeductRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("扣减记录不存在");
        }
        Order order = locateOrder(record.getOrderId());
        OperLogContext.begin(order, WebOrderOperation.DEDUCT_DEPOSIT, req);
        OperLogContext.setDescription(describeDeductOperation(req.text("operationType")));
        return rentDepositDeductService.confirmDeductRecord(req);
    }

    /**
     * 把扣减确认动作翻译成台账可读描述，便于在操作台账里区分“申请售后/确认扣款/撤销售后”等动作。
     *
     * @param operationType 前端传入的扣减确认动作
     * @return 台账中文描述
     */
    private String describeDeductOperation(String operationType) {
        if (operationType == null) {
            return "扣减押金-处理售后";
        }
        switch (operationType.trim()) {
            case "PAY_COMPENSATION":
                return "扣减押金-确认售后并扣款";
            case "AFTERSALE_FINISH":
                return "扣减押金-补完结售后";
            case "USER_CANCEL_APPLY":
                return "扣减押金-撤销售后";
            case "MERCHANT_APPROVE":
                return "扣减押金-审核通过";
            case "MERCHANT_REJECT":
                return "扣减押金-拒绝售后";
            case "APPROVE_WITH_USER_PAY":
                return "扣减押金-审核并发起赔付";
            default:
                return "扣减押金-处理售后";
        }
    }

    /**
     * 单订单预览支付宝售后单与本地扣减台账差异。
     *
     * @param req 请求参数，需包含 orderId 或 orderNo
     * @return 支付宝售后差异列表
     */
    @SneakyThrows
    public Map<String, Object> previewAftersales(WebRequest req) {
        return rentAftersaleSyncService.preview(req);
    }

    /**
     * 按本地订单分页扫描支付宝售后。
     *
     * @param req 请求参数，可包含时间范围、订单状态和售后状态
     * @return 扫描结果和统计信息
     */
    public Map<String, Object> scanAftersales(WebRequest req) {
        return rentAftersaleSyncService.scan(req);
    }

    /**
     * 将管理员选中的支付宝售后快照导入到本地扣减台账。
     *
     * @param req 请求参数，需包含 snapshotIds
     * @return 导入结果
     */
    @SneakyThrows
    @OperLog
    public Map<String, Object> importAftersales(WebRequest req) {
        Order order = rentAftersaleSyncService.resolvePrimaryOrderForImport(req);
        if (order != null) {
            OperLogContext.begin(order, WebOrderOperation.AFTERSALE_SYNC, req);
        }
        Map<String, Object> result = rentAftersaleSyncService.importSnapshots(req);
        OperLogContext.setResultBody(result);
        return result;
    }

    private Integer parseDeductAmountCent(String amountText) {
        BigDecimal amount = new BigDecimal(amountText.trim());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("扣减金额必须大于0");
        }
        return OrderUtil.convertYuanToCent(amount.stripTrailingZeros().toPlainString());
    }

    private void validateDeductable(Order order, Integer deductAmountCent, String feeType, String reasonCode) {
        if (order.getRentOrderId() == null || order.getRentOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("订单缺少交易组件订单号，无法发起赔付售后");
        }
        if (order.getUserUuid() == null || order.getUserUuid().trim().isEmpty()) {
            throw new IllegalArgumentException("订单缺少买家支付宝用户ID，无法发起赔付售后");
        }
        if (AlipayRentConstants.STATUS_FINISHED.equals(order.getAlipayStatus())
                || AlipayRentConstants.STATUS_CLOSED.equals(order.getAlipayStatus())) {
            throw new IllegalArgumentException("当前订单状态不允许扣减押金: " + order.getAlipayStatus());
        }
        validateFeeTypeAndReasonCode(feeType, reasonCode);
        int remaining = currentRemainingDeposit(order);
        if (remaining <= 0) {
            throw new IllegalArgumentException("该订单已无可扣减押金");
        }
        if (deductAmountCent > remaining) {
            throw new IllegalArgumentException("扣减金额不能超过剩余押金");
        }
    }

    private int currentRemainingDeposit(Order order) {
        if (order.getOrderRestDeposit() != null) {
            return order.getOrderRestDeposit();
        }
        return order.getOrderDeposit() != null ? order.getOrderDeposit() : 0;
    }

    private void validateFeeTypeAndReasonCode(String feeType, String reasonCode) {
        Set<String> indemnityReasons = new HashSet<>();
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_DAMAGED);
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_REPAIR);
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_LOST);
        indemnityReasons.add(AlipayRentConstants.AFTERSALE_REASON_ITEM_DEPRECIATION);

        Set<String> lateFeeReasons = new HashSet<>();
        lateFeeReasons.add(AlipayRentConstants.AFTERSALE_REASON_RETURN_EARLY);
        lateFeeReasons.add(AlipayRentConstants.AFTERSALE_REASON_RETURN_OVERDUE);

        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_INDEMNITY.equals(feeType)) {
            if (!indemnityReasons.contains(reasonCode)) {
                throw new IllegalArgumentException("赔付金原因码不合法: " + reasonCode);
            }
            return;
        }
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(feeType)) {
            if (!lateFeeReasons.contains(reasonCode)) {
                throw new IllegalArgumentException("违约金原因码不合法: " + reasonCode);
            }
            return;
        }
        throw new IllegalArgumentException("费用类型不合法: " + feeType);
    }

    private String buildDeductRequestNo(Order order, String suffix) {
        return order.getOrderNo() + suffix + System.currentTimeMillis();
    }

    private String resolveOperatorName(String operatorName) {
        String normalized = normalizeText(operatorName);
        return normalized != null ? normalized : "ADMIN";
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RentDepositDeductRecord buildDeductRecord(Order order, Integer deductAmountCent,
                                                      int beforeRemainingDeposit, String feeType, String reasonCode,
                                                      String remark, String operatorId, String operatorName,
                                                      String outRequestNo, String outAftersaleId,
                                                      String confirmRequestNo, String outTradeNo) {
        RentDepositDeductRecord record = new RentDepositDeductRecord();
        record.setOrderId(order.getOrderId());
        record.setOrderNo(order.getOrderNo());
        record.setAuthNo(order.getOrderAuthNo());
        record.setOutAftersaleId(outAftersaleId);
        record.setConfirmRequestNo(confirmRequestNo);
        record.setOutRequestNo(outRequestNo);
        record.setOutTradeNo(outTradeNo);
        record.setFeeType(feeType);
        record.setReasonCode(reasonCode);
        record.setDeductAmount(deductAmountCent);
        record.setBeforeRemainingDeposit(beforeRemainingDeposit);
        record.setReason(resolveReasonText(feeType));
        record.setRemark(remark);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setStatus(RentDepositDeductRecord.STATUS_PROCESSING);
        record.setCreateTime(System.currentTimeMillis());
        record.setUpdateTime(new Date());
        return record;
    }

    private String resolveReasonText(String feeType) {
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_INDEMNITY.equals(feeType)) {
            return "赔付金";
        }
        if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(feeType)) {
            return "违约金";
        }
        return "扣减押金";
    }

    private void markAftersaleCreated(RentDepositDeductRecord record, AlipayCommerceRentOrderAftersaleCreateResponse createResponse) {
        record.setAftersaleNo(createResponse.getAftersaleId());
        record.setOutAftersaleId(createResponse.getOutAftersaleId());
        record.setUpdateTime(new Date());
        rentDepositDeductRecordMapper.updateById(record);
    }

    private RentDepositDeductRecord findLatestRetryableRecord(Integer orderId, String feeType, String reasonCode, Integer deductAmountCent) {
        return rentDepositDeductRecordMapper.selectOne(
                new QueryWrapper<RentDepositDeductRecord>()
                        .eq("order_id", orderId)
                        .eq("fee_type", feeType)
                        .eq("reason_code", reasonCode)
                        .eq("deduct_amount", deductAmountCent)
                        .in("status", RentDepositDeductRecord.STATUS_PROCESSING, RentDepositDeductRecord.STATUS_FAILED)
                        .isNotNull("aftersale_no")
                        .orderByDesc("id")
                        .last("LIMIT 1")
        );
    }

    private Map<String, Object> confirmExistingDeductRecord(Order order, RentDepositDeductRecord record, String operationType) throws Exception {
        if (RentDepositDeductRecord.STATUS_SUCCESS.equals(record.getStatus())) {
            return toDeductConfirmResult(order, record);
        }
        if (record.getAftersaleNo() == null || record.getAftersaleNo().trim().isEmpty()) {
            throw new IllegalArgumentException("当前记录尚未创建售后单，无法继续确认");
        }
        validateConfirmOperationType(operationType);
        String lockKey = "deposit_deduct_lock:" + order.getOrderId();
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisClient.tryLock(lockKey, lockValue, 2, TimeUnit.MINUTES);
        if (!locked) {
            throw new IllegalArgumentException("该订单正在处理扣减确认，请稍后再试");
        }
        try {
            AlipayCommerceRentOrderAftersaleConfirmResponse confirmResponse = rentAftersaleService.confirmCompensationAftersale(
                    order, operationType, record.getFeeType(), record.getReasonCode(), record.getDeductAmount(),
                    record.getAftersaleNo(), record.getOutAftersaleId(), record.getOutTradeNo(), record.getRemark());
            Order latestOrder = persistDeductConfirmResult(order, record, operationType, confirmResponse);
            Map<String, Object> result = toDeductConfirmResult(latestOrder, record);
            OperLogContext.setResultBody(result);
            return result;
        } catch (Exception ex) {
            markDeductRecordFailed(record, ex);
            throw ex;
        } finally {
            redisClient.releaseLock(lockKey, lockValue);
        }
    }

    private AlipayCommerceRentOrderAftersaleCreateResponse buildCreatedResponseFromRecord(RentDepositDeductRecord record) {
        AlipayCommerceRentOrderAftersaleCreateResponse response = new AlipayCommerceRentOrderAftersaleCreateResponse();
        response.setAftersaleId(record.getAftersaleNo());
        response.setOutAftersaleId(record.getOutAftersaleId());
        return response;
    }

    private void validateConfirmOperationType(String operationType) {
        if (AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)
                || AlipayRentConstants.AFTERSALE_OPERATION_USER_CANCEL_APPLY.equals(operationType)) {
            return;
        }
        throw new IllegalArgumentException("当前仅支持 AFTERSALE_FINISH 或 USER_CANCEL_APPLY");
    }

    private Order persistDeductConfirmResult(Order order, RentDepositDeductRecord record, String operationType,
                                             AlipayCommerceRentOrderAftersaleConfirmResponse confirmResponse) {
        return transactionTemplate.execute(status -> {
            Order latestOrder = orderLocator.requireByIdentifier(String.valueOf(order.getOrderId()));
            if (AlipayRentConstants.AFTERSALE_OPERATION_AFTERSALE_FINISH.equals(operationType)) {
                int beforeRemaining = currentRemainingDeposit(latestOrder);
                int afterRemaining = beforeRemaining - record.getDeductAmount();
                if (afterRemaining < 0) {
                    throw new IllegalArgumentException("扣减后剩余押金不能为负数");
                }

                latestOrder.setOrderRestDeposit(afterRemaining);
                latestOrder.setUpdateTime(new Date());
                latestOrder.setRestAmount(OrderUtil.convertCentToYuan(afterRemaining).toString());
                latestOrder.setRestFundAmount(OrderUtil.convertCentToYuan(afterRemaining).toString());
                latestOrder.setTotalPayAmount(addYuanAmount(latestOrder.getTotalPayAmount(), record.getDeductAmount()));
                latestOrder.setTotalPayFundAmount(addYuanAmount(latestOrder.getTotalPayFundAmount(), record.getDeductAmount()));
                if (AlipayRentConstants.AFTERSALE_FEE_TYPE_INDEMNITY.equals(record.getFeeType())) {
                    latestOrder.setOrderDamage((latestOrder.getOrderDamage() == null ? 0 : latestOrder.getOrderDamage()) + record.getDeductAmount());
                } else if (AlipayRentConstants.AFTERSALE_FEE_TYPE_LATE_FEE.equals(record.getFeeType())) {
                    latestOrder.setOrderBeamo((latestOrder.getOrderBeamo() == null ? 0 : latestOrder.getOrderBeamo()) + record.getDeductAmount());
                }
                record.setAfterRemainingDeposit(afterRemaining);
                record.setStatus(RentDepositDeductRecord.STATUS_SUCCESS);
            } else if (AlipayRentConstants.AFTERSALE_OPERATION_USER_CANCEL_APPLY.equals(operationType)) {
                record.setAfterRemainingDeposit(record.getBeforeRemainingDeposit());
                record.setStatus(RentDepositDeductRecord.STATUS_CANCELLED);
            }
            latestOrder.updateById();

            record.setTradeNo(confirmResponse.getTradeNo());
            record.setAlipaySubCode(confirmResponse.getSubCode());
            record.setAlipaySubMsg(confirmResponse.getSubMsg());
            record.setUpdateTime(new Date());
            rentDepositDeductRecordMapper.updateById(record);
            return latestOrder;
        });
    }

    private void markDeductRecordFailed(RentDepositDeductRecord record, Exception ex) {
        record.setStatus(RentDepositDeductRecord.STATUS_FAILED);
        if (ex instanceof RentAftersaleException) {
            RentAftersaleException depositEx = (RentAftersaleException) ex;
            record.setAlipaySubCode(depositEx.getSubCode());
            record.setAlipaySubMsg(depositEx.getSubMsg());
        } else {
            record.setAlipaySubMsg(ex.getMessage() != null ? ex.getMessage() : "扣减失败");
        }
        record.setUpdateTime(new Date());
        rentDepositDeductRecordMapper.updateById(record);
    }

    private Map<String, Object> toDeductRecordView(RentDepositDeductRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("orderId", record.getOrderId());
        view.put("orderNo", record.getOrderNo());
        view.put("aftersaleNo", record.getAftersaleNo());
        view.put("outAftersaleId", record.getOutAftersaleId());
        view.put("confirmRequestNo", record.getConfirmRequestNo());
        view.put("outTradeNo", record.getOutTradeNo());
        view.put("tradeNo", record.getTradeNo());
        view.put("feeType", record.getFeeType());
        view.put("reasonCode", record.getReasonCode());
        view.put("deductAmount", record.getDeductAmount());
        view.put("beforeRemainingDeposit", record.getBeforeRemainingDeposit());
        view.put("afterRemainingDeposit", record.getAfterRemainingDeposit());
        view.put("remark", record.getRemark());
        view.put("status", record.getStatus());
        view.put("alipaySubCode", record.getAlipaySubCode());
        view.put("alipaySubMsg", record.getAlipaySubMsg());
        view.put("createTime", record.getCreateTime());
        return view;
    }

    private Map<String, Object> toDeductConfirmResult(Order order, RentDepositDeductRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        result.put("recordId", record.getId());
        result.put("status", record.getStatus());
        result.put("remainingDeposit", order.getOrderRestDeposit());
        result.put("aftersaleNo", record.getAftersaleNo());
        result.put("outAftersaleId", record.getOutAftersaleId());
        result.put("tradeNo", record.getTradeNo());
        result.put("outTradeNo", record.getOutTradeNo());
        return result;
    }

    private String addYuanAmount(String existing, Integer amountCent) {
        BigDecimal current = BigDecimal.ZERO;
        if (existing != null && !existing.trim().isEmpty()) {
            try {
                current = new BigDecimal(existing.trim());
            } catch (NumberFormatException ignore) {
                current = BigDecimal.ZERO;
            }
        }
        return current.add(OrderUtil.convertCentToYuan(amountCent)).toString();
    }

    // 支付宝统一收单退款能力关注的是交易是否仍可退款，因此这里放宽到已支付后的履约中状态；
    // 但“驳回退款”仍然只允许在用户已发起退款申请（PENDING_CANCEL）时执行。
    private void validateRefundRequest(Order order, Boolean isAgree) {
        String status = order.getAlipayStatus();
        if (Boolean.TRUE.equals(isAgree)) {
            if (!AlipayRentConstants.STATUS_PAID.equals(status)
                    && !AlipayRentConstants.STATUS_DELIVERED.equals(status)
                    && !AlipayRentConstants.STATUS_RECEIVED.equals(status)
                    && !AlipayRentConstants.STATUS_RETURN_DELIVERED.equals(status)
                    && !AlipayRentConstants.STATUS_RETURN_RECEIVED.equals(status)
                    && !AlipayRentConstants.STATUS_PENDING_CANCLE.equals(status)) {
                throw new IllegalArgumentException("当前订单状态不支持退款: " + status);
            }
            return;
        }
        if (!AlipayRentConstants.STATUS_PENDING_CANCLE.equals(status)) {
            throw new IllegalArgumentException("当前订单状态不支持退款驳回: " + status);
        }
    }

    private boolean isContractReadyStatus(String status) {
        return AlipayRentConstants.STATUS_SIGNED.equals(status)
                || AlipayRentConstants.STATUS_APPROVED.equals(status)
                || AlipayRentConstants.STATUS_PAID.equals(status)
                || AlipayRentConstants.STATUS_DELIVERED.equals(status)
                || AlipayRentConstants.STATUS_RECEIVED.equals(status)
                || AlipayRentConstants.STATUS_RETURN_DELIVERED.equals(status)
                || AlipayRentConstants.STATUS_RETURN_RECEIVED.equals(status)
                || AlipayRentConstants.STATUS_FINISHED.equals(status)
                || AlipayRentConstants.STATUS_CLOSED.equals(status);
    }

    /**
     * 合同回传支付宝要求确认收货后执行。
     * 已进入归还、已归还或完结的订单都说明已经越过确认收货节点，允许人工补偿回传。
     */
    private boolean isContractAfterReceiveStatus(String status) {
        return AlipayRentConstants.STATUS_RECEIVED.equals(status)
                || AlipayRentConstants.STATUS_RETURN_DELIVERED.equals(status)
                || AlipayRentConstants.STATUS_RETURN_RECEIVED.equals(status)
                || AlipayRentConstants.STATUS_FINISHED.equals(status);
    }

    /**
     * 定位订单，orderId 可能是 Integer，统一转为 String 后通过 orderLocator 查找。
     */
    private Order locateOrder(Object orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId 不能为空");
        }
        return orderLocator.requireByIdentifier(String.valueOf(orderId));
    }

    private void applyIdentityPhotoRequirement(Order order, boolean required) {
        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        if (extension == null) {
            extension = new RentOrderExtension();
        }
        extension.setIdCardPhotoRequired(required);
        if (required) {
            extension.setIdCardPhotoReviewStatus(ID_CARD_REVIEW_PENDING_UPLOAD);
            extension.setIdCardPhotoReviewRemark(null);
            extension.setIdCardPhotoReviewedAt(null);
        } else {
            extension.setIdCardPhotoUrls(Collections.emptyList());
            extension.setIdCardPhotoUploadedAt(null);
            extension.setIdCardPhotoReviewStatus("NOT_REQUIRED");
            extension.setIdCardPhotoReviewRemark(null);
            extension.setIdCardPhotoReviewedAt(null);
        }
        rentExtensionStore.saveOrderExtension(order.getOrderNo(), extension);
        attachOrderExtension(order, extension);
    }

    private void applyEsignRequirement(Order order, boolean required) {
        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        if (extension == null) {
            extension = new RentOrderExtension();
        }
        extension.setEsignRequired(required);
        rentExtensionStore.saveOrderExtension(order.getOrderNo(), extension);
        attachOrderExtension(order, extension);
    }

    /**
     * 为订单列表批量挂载最新寄回记录，避免后台页面逐行发请求才能看到寄回状态。
     *
     * @param orders 当前分页订单列表
     */
    private void attachReturnRecords(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Integer> orderIds = new ArrayList<>();
        for (Order order : orders) {
            if (order.getOrderId() != null) {
                orderIds.add(order.getOrderId());
            }
        }
        Map<Integer, RentOrderReturnRecord> returnRecordMap = orderReturnRecordService.findByOrderIds(orderIds);
        for (Order order : orders) {
            order.setLatestReturnRecord(returnRecordMap.get(order.getOrderId()));
        }
    }

    private void attachOrderExtensions(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<String> orderNos = new ArrayList<>();
        for (Order order : orders) {
            if (order.getOrderNo() != null) {
                orderNos.add(order.getOrderNo());
            }
        }
        Map<String, RentOrderExtension> extensionMap = rentExtensionStore.loadOrderExtensions(orderNos);
        for (Order order : orders) {
            attachOrderExtension(order, extensionMap.get(order.getOrderNo()));
        }
    }

    private void attachOrderExtension(Order order, RentOrderExtension extension) {
        if (order == null) {
            return;
        }
        order.setIdCardPhotoRequired(extension != null && Boolean.TRUE.equals(extension.getIdCardPhotoRequired()));
        order.setEsignRequired(extension != null && Boolean.TRUE.equals(extension.getEsignRequired()));
        order.setIdCardPhotoUrls(extension == null || extension.getIdCardPhotoUrls() == null
                ? Collections.emptyList()
                : extension.getIdCardPhotoUrls());
        order.setIdCardPhotoUploadedAt(extension == null ? null : extension.getIdCardPhotoUploadedAt());
        order.setIdCardPhotoReviewStatus(resolveIdCardPhotoReviewStatus(extension));
        order.setIdCardPhotoReviewRemark(extension == null ? null : extension.getIdCardPhotoReviewRemark());
        order.setIdCardPhotoReviewedAt(extension == null ? null : extension.getIdCardPhotoReviewedAt());
    }

    /**
     * 兼容旧订单扩展的身份证照片审核状态。
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

    /**
     * 判断订单是否已经进入身份证照片补充流程但尚未二次审核通过。
     */
    private boolean hasPendingIdentityPhotoReview(Order order) {
        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        return extension != null
                && Boolean.TRUE.equals(extension.getIdCardPhotoRequired())
                && !ID_CARD_REVIEW_APPROVED.equals(resolveIdCardPhotoReviewStatus(extension));
    }

    private void attachAccountUsers(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<String> userUuids = new HashSet<>();
        for (Order order : orders) {
            if (order.getUserUuid() != null && !order.getUserUuid().trim().isEmpty()) {
                userUuids.add(order.getUserUuid());
            }
        }
        if (userUuids.isEmpty()) {
            return;
        }
        List<User> users = userMapper.selectList(new QueryWrapper<User>().in("uuid", userUuids));
        Map<String, User> userMap = new LinkedHashMap<>();
        for (User user : users) {
            if (user.getUuid() != null) {
                userMap.put(user.getUuid(), user);
            }
        }
        for (Order order : orders) {
            User user = userMap.get(order.getUserUuid());
            if (user == null) {
                continue;
            }
            order.setAccountUserTel(user.getUserTel());
            order.setAccountRealName(user.getRealName());
            order.setAccountIdCard(user.getIdCard());
        }
    }

    /**
     * 将对象转为 Map，用于序列化支付宝响应中的嵌套对象。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.convertValue(obj, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
