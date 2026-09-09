package com.fly.rent.legacy.service.impl;

import com.fly.rent.common.order.RentOrderReletRelationService;
import com.fly.rent.capability.billing.InstallmentPaymentSyncService;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.legacy.dto.apilyRentUtil.AddressInfo;
import com.fly.rent.legacy.dto.apilyRentUtil.BaseApily;
import com.fly.rent.legacy.dto.apilyRentUtil.OrderRentParams;
import com.fly.rent.legacy.dto.apilyRentUtil.RentOrderCreateParam;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.ApiResponse;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.CostInfo;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.RentOrderVto;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.config.NoUseException;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentInstallmentInfoEntity;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderSafetyService;
import com.fly.rent.legacy.service.RentOrderService;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.support.util.AlipayUserIdentityUtil;
import com.fly.rent.support.util.CommonUtil;
import com.fly.rent.support.util.OrderUtil;
import com.fly.rent.support.util.RentInstallmentPlanSupport;
import com.alipay.api.AlipayApiException;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.alipay.api.domain.AlipayCommerceRentOrderCloseModel;
import com.alipay.api.domain.AlipayCommerceRentOrderCreateModel;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentApproveModel;
import com.alipay.api.domain.RentCreditInfoDTO;
import com.alipay.api.domain.RentFundAuthFreezeInfoDTO;
import com.alipay.api.domain.RentGoodsDetailInfoDTO;
import com.alipay.api.domain.RentInstallmentInfo;
import com.alipay.api.domain.RentOrderPriceInfoDTO;
import com.alipay.api.domain.RentOrderReceiverAddressInfoDTO;
import com.alipay.api.domain.RentOrderStatementInfoVO;
import com.alipay.api.domain.RentPathInfoDTO;
import com.alipay.api.domain.RentPlanInfoDTO;
import com.alipay.api.domain.RentReletInfoDTO;
import com.alipay.api.domain.RentServiceProtocolDTO;
import com.alipay.api.domain.RentSignInfoDTO;
import com.alipay.api.request.AlipayCommerceRentOrderCloseRequest;
import com.alipay.api.request.AlipayCommerceRentOrderCreateRequest;
import com.alipay.api.request.AlipayCommerceRentOrderFulfillmentApproveRequest;
import com.alipay.api.request.AlipayCommerceRentOrderQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayCommerceRentOrderCloseResponse;
import com.alipay.api.response.AlipayCommerceRentOrderCreateResponse;
import com.alipay.api.response.AlipayCommerceRentOrderFulfillmentApproveResponse;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * 旧租赁订单核心服务实现。
 * 负责本地订单创建、支付宝租赁订单同步、审核、退款和关单等主流程。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Slf4j
@Service
public class RentOrderServiceImpl implements RentOrderService {

    private static final int PICKUP_STORE = 0;
    private static final int PICKUP_EXPRESS = 1;
    private static final String PICKUP_TYPE_STORE = "store";

    @Autowired
    private BaseApily baseApily;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RentInstallmentInfoEntityMapper rentInstallmentInfoEntityMapper;

    @Autowired
    private AlipayClientService alipayClientService;

    @Autowired
    private RentOrderSafetyService rentOrderSafetyService;
    @Autowired
    private AlipayOrderLifecycleNotifyService alipayOrderLifecycleNotifyService;
    @Autowired
    private RentOrderReletRelationService reletRelationService;
    @Autowired
    private ZhongtaiConfigService zhongtaiConfigService;
    @Autowired
    private AlipayPlatformConfigService alipayPlatformConfigService;
    @Autowired
    private InstallmentPaymentSyncService installmentPaymentSyncService;

    /**
     * 支付宝租赁建单主流程：先落本地订单，再调支付宝创建租赁单并回写支付宝订单号。
     *
     * @param rentOrderCreateParam 建单入参，含：sourceId（幂等）、apiResponse（费用/阶段计划）、outSkuId（规格 ID）、
     *                              orderRentParams（租期/起止时间）、addressInfo（收货地址）。由调用方组装传入。
     * @param userUuid             当前用户 UUID，用于加锁、查用户、建单归属。
     * @return 订单结果视图：success、orderId（支付宝租赁单号）、outOrderId、path（小程序详情页路径）；失败时带 errorMsg/errorCode。
     * @throws AlipayApiException 调用支付宝接口异常。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RentOrderVto createRentOrder(RentOrderCreateParam rentOrderCreateParam, String userUuid) throws AlipayApiException {
        // 1. 分布式锁防同一用户并发建单
        String lockValue = rentOrderSafetyService.acquireCreateLock(userUuid);
        try {
            rentOrderSafetyService.ensureCreateAllowed(userUuid);

            // 2. 校验用户存在
            User user = new User().selectOne(new QueryWrapper<User>().eq("uuid", userUuid));
            if (user == null) {
                throw new NoUseException("用户不存在");
            }

            // 3. 校验入参与 apiResponse 结构（success、bizParamData、costInfo）
            if (rentOrderCreateParam == null || rentOrderCreateParam.getApiResponse() == null) {
                throw new NoUseException("订单参数不能为空");
            }

            ApiResponse apiResponse = rentOrderCreateParam.getApiResponse();
            if (!apiResponse.success || apiResponse.bizParamData == null || apiResponse.bizParamData.costInfo == null) {
                String errorMsg = apiResponse.errorMsg != null ? apiResponse.errorMsg : "订单前置数据无效";
                throw new NoUseException(errorMsg);
            }

            // 4. 按 outSkuId 查规格，校验库存与商品存在
            Attr attr = resolveCreateAttr(rentOrderCreateParam.getOutSkuId());
            if (attr == null) {
                throw new NoUseException("商品规格不存在");
            }

            if (attr.getAttrNum() <= 0) {
                throw new NoUseException("该规格暂时无货");
            }

            Good good = baseApily.getGoodMapper().selectById(attr.getGoodId());
            if (good == null) {
                throw new NoUseException("商品不存在");
            }

            // 5. 组装本地订单实体并落库（含分期计划等）
            Order order = buildOrder(rentOrderCreateParam, user, attr, good, apiResponse);
            saveOrderToDatabase(order, attr);
            // 6. 调支付宝创建租赁单，回写 rentOrderId/outOrderId，返回结果视图
            return createAlipayRentOrder(order);
        } finally {
            rentOrderSafetyService.releaseCreateLock(userUuid, lockValue);
        }
    }

    /**
     * 续租建单主流程。
     * 关键点有两层：
     * 1. 本地侧仍然先生成一笔新的子订单，用来承载本次续租的租期与租金；
     * 2. 支付宝侧不切换到新的产品族，而是继续走 alipay.commerce.rent.order.create，
     *    只是把 order_type 改成 RELET，并补 parent_order_id / relet_info.origin_order_id。
     *
     * @param originOrder   原租赁订单，本地必须已存在且已同步支付宝租赁单号
     * @param renewDuration 续租天数，必须大于 0
     * @param sourceId      本次续租追踪 ID，优先使用新值，空则回退原单 sourceId
     * @param userUuid      当前用户 UUID
     * @return 续租子单建单结果
     * @throws AlipayApiException 支付宝接口调用异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RentOrderVto createReletOrder(
            Order originOrder,
            Integer renewDuration,
            String sourceId,
            String userUuid
    ) throws AlipayApiException {
        String lockValue = rentOrderSafetyService.acquireCreateLock(userUuid);
        try {
            rentOrderSafetyService.ensureReletCreateAllowed(userUuid);

            Order latestOriginOrder = reloadOriginOrder(originOrder);
            validateReletRequest(latestOriginOrder, renewDuration, userUuid);

            Attr attr = baseApily.getAttrMapper().selectById(latestOriginOrder.getAttrId());
            if (attr == null) {
                throw new NoUseException("原订单规格不存在，无法续租");
            }
            Good good = baseApily.getGoodMapper().selectById(latestOriginOrder.getGoodId());
            if (good == null) {
                throw new NoUseException("原订单商品不存在，无法续租");
            }

            Order reletOrder = buildReletOrder(latestOriginOrder, attr, good, renewDuration, sourceId);
            saveOrderToDatabase(reletOrder, attr);
            RentOrderVto result = createAlipayRentOrder(reletOrder, latestOriginOrder);
            if (result.isSuccess()) {
                reletRelationService.saveRelation(reletOrder, latestOriginOrder, renewDuration);
            }
            return result;
        } finally {
            rentOrderSafetyService.releaseCreateLock(userUuid, lockValue);
        }
    }

    /**
     * 根据建单参数与用户/商品/规格构建本地订单实体（未落库），供 saveOrderToDatabase 与 createAlipayRentOrder 使用。
     *
     * @param rentOrderCreateParam 建单参数，用其 addressInfo、orderRentParams、sourceId 等填充订单字段。
     * @param user                 当前用户，取 userId、uuid。
     * @param attr                 规格，取 attrId、goodId、单价、押金、数量、租送方式等。
     * @param good                 商品，取标题、封面等。
     * @param apiResponse          前置 API 响应，用其 bizParamData.costInfo 的 deposit 写入押金；总租金按单日价、数量、租期重新计算。
     * @return 未持久化的 Order 实体，含订单号、支付宝状态 CREATED、租期起止、来源 ID 等。
     */
    private Order buildOrder(RentOrderCreateParam rentOrderCreateParam, User user, Attr attr, Good good, ApiResponse apiResponse) {
        Order order = new Order();
        AddressInfo topAddress = rentOrderCreateParam.getAddressInfo();
        AddressInfo rentAddress = rentOrderCreateParam.getOrderRentParams() == null
                ? null
                : rentOrderCreateParam.getOrderRentParams().getAddressInfo();
        if (topAddress == null && rentAddress == null) {
            throw new NoUseException("收货地址信息不能为空");
        }
        String address = firstNotBlank(
                topAddress == null ? null : topAddress.getAddress(),
                rentAddress == null ? null : rentAddress.getAddress()
        );
        String userName = firstNotBlank(
                topAddress == null ? null : topAddress.getFullname(),
                rentAddress == null ? null : rentAddress.getFullname(),
                user == null ? null : user.getUserTitle(),
                user == null ? null : user.getRealName()
        );
        String userPhone = firstNotBlank(
                topAddress == null ? null : topAddress.getMobilePhone(),
                rentAddress == null ? null : rentAddress.getMobilePhone(),
                user == null ? null : user.getUserTel()
        );
        if (!hasText(userName)) {
            throw new NoUseException("收货人姓名不能为空");
        }
        if (!hasText(userPhone)) {
            throw new NoUseException("下单前请先绑定手机号");
        }

        order.setUserId(user.getUserId());
        order.setAddr(address);
        order.setUserTitle(userName);
        order.setUserTel(userPhone);
        order.setUserUuid(user.getUuid());
        order.setReturnInfo(zhongtaiConfigService.getString("alipay", "rent.return.address.json"));

        order.setAttrId(attr.getAttrId());
        order.setAttrTitle(attr.getAttrTitle());
        order.setAttrAmount(attr.getAttrAmount());
        order.setGoodId(attr.getGoodId());
        order.setGoodTitle(good.getGoodTitle());
        order.setGoodCover(good.getGoodCover());
        OrderRentParams orderRentParams = rentOrderCreateParam.getOrderRentParams();
        order.setAttrNum(orderRentParams.getQuantity());
        order.setAlipayStatus(AlipayRentConstants.STATUS_CREATED);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date startDate = sdf.parse(orderRentParams.getRentStartTime());
            order.setOrderStart(startDate.getTime());
            Date endDate = sdf.parse(orderRentParams.getRentEndTime());
            order.setOrderEnd(endDate.getTime());
        } catch (Exception e) {
            log.error("时间格式转换失败: {}", e.getMessage());
            throw new NoUseException("时间格式错误，请使用 yyyy-MM-dd HH:mm:ss");
        }
        order.setOrderKeep(orderRentParams.getDuration());

        CostInfo costInfo = apiResponse.bizParamData.costInfo;
        BigDecimal depositYuan = new BigDecimal(costInfo.deposit);
        order.setOrderDeposit(depositYuan.multiply(BigDecimal.valueOf(100)).intValue());

        order.setOrderTotal(RentInstallmentPlanSupport.calculateTotalRentAmount(
                attr.getAttrAmount(),
                orderRentParams.getQuantity(),
                orderRentParams.getDuration()
        ));
        int periodTotal = RentInstallmentPlanSupport.resolveRequestedPeriod(rentOrderCreateParam, attr);
        RentInstallmentPlanSupport.applyOrderPeriodFields(order, periodTotal);
        order.setOrderTradeType(periodTotal > 1 ? resolveInstallmentBillingCycle(attr) : 1);

        order.setSourceId(rentOrderCreateParam.getSourceId());
        order.setOrderBeamo(0);
        order.setOrderDamage(0);
        order.setRentToSend(attr.getRentToSend());
        applyPickupChoice(order, rentOrderCreateParam, good, address);
        order.setCreatetime(System.currentTimeMillis());

        return order;
    }

    private void applyPickupChoice(Order order, RentOrderCreateParam param, Good good, String address) {
        boolean storePickup = isStorePickupRequested(param);
        if (storePickup && !isStorePickupSupported(good)) {
            throw new NoUseException("该商品不支持线下自提");
        }
        order.setGoodDistr(good == null ? null : good.getGoodDistr());
        order.setOfflinePickup(storePickup ? PICKUP_STORE : PICKUP_EXPRESS);
        order.setPickupAddr(storePickup ? resolvePickupAddress(address) : null);
        order.setFreight(storePickup ? 0 : safeInt(good == null ? null : good.getFreight()));
    }

    private String resolvePickupAddress(String fallbackAddress) {
        String merchantAddress = alipayPlatformConfigService == null ? null : alipayPlatformConfigService.contractMerchantAddress();
        return firstNotBlank(merchantAddress, fallbackAddress);
    }

    private boolean isStorePickupRequested(RentOrderCreateParam param) {
        if (param == null) {
            return false;
        }
        if (PICKUP_TYPE_STORE.equalsIgnoreCase(trimToNull(param.getPickupType()))) {
            return true;
        }
        OrderRentParams rentParams = param.getOrderRentParams();
        return rentParams != null && PICKUP_TYPE_STORE.equalsIgnoreCase(trimToNull(rentParams.getPickupType()));
    }

    private boolean isStorePickupSupported(Good good) {
        return good != null && good.getOfflinePickup() != null && good.getOfflinePickup() == PICKUP_STORE;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmedValue = trimToNull(value);
            if (trimmedValue != null) {
                return trimmedValue;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 保存本地订单及附属数据：补全订单字段、设置交易类型与最终支付金额、insert 后按主键再查一次以拿到 orderNo 等。
     *
     * @param order 由 buildOrder 得到的订单实体，将被 fillOrder 补全、设置 orderTradeType=1、orderFinalpay 后落库。
     * @param attr  规格，供 fillOrder 使用。
     */
    private void saveOrderToDatabase(Order order, Attr attr) {
        order = fillOrder(attr, order);
        if (order.getOrderTradeType() == null) {
            order.setOrderTradeType(1);
        }
        if (order.getOrderTotalRentPeriods() == null || order.getOrderTotalRentPeriods() <= 0) {
            RentInstallmentPlanSupport.applyOrderPeriodFields(order, 1);
        }
        order.setOrderFinalpay(calFinal(order));
        order.insert();
        order.selectById();
    }

    /**
     * 调用支付宝「创建租赁订单」接口，成功则回写本地订单的 rentOrderId、outOrderId，失败则把订单状态置为已关闭。
     *
     * @param order 已落库的本地订单，含 orderNo、goodId、attrId、租金押金、租期、用户 UUID、地址等，用于组装支付宝请求。
     * @return 结果视图：成功时 success=true、orderId/outOrderId/path；失败时 success=false、errorMsg/errorCode，且本地订单已更新为 CLOSED。
     * @throws AlipayApiException 支付宝接口调用异常。
     */
    @Override
    public RentOrderVto createAlipayRentOrder(Order order) throws AlipayApiException {
        return createAlipayRentOrder(order, null);
    }

    /**
     * 统一封装支付宝租赁建单调用。
     * 当 parentOrder 为空时表示普通租赁单；
     * 当 parentOrder 不为空时表示续租子单，需要按 RELET 语义组装请求。
     *
     * @param order       当前准备创建到支付宝侧的本地订单
     * @param parentOrder 续租父单；普通租赁时传 null
     * @return 建单结果
     * @throws AlipayApiException 支付宝接口调用异常
     */
    private RentOrderVto createAlipayRentOrder(Order order, Order parentOrder) throws AlipayApiException {
        RentOrderVto rentOrderVto = new RentOrderVto();
        rentOrderVto.setSuccess(false);

        Good good = baseApily.getGoodMapper().selectById(order.getGoodId());
        Attr attr = baseApily.getAttrMapper().selectById(order.getAttrId());

        AlipayCommerceRentOrderCreateRequest request = buildAlipayRentOrderRequest(order, good, attr, parentOrder);
        AlipayCommerceRentOrderCreateResponse response = alipayClientService.execute(request);

        if (response.isSuccess()) {
            order.setRentOrderId(response.getOrderId());
            order.setOutOrderId(response.getOutOrderId());
            order.setUpdateTime(new Date());
            orderMapper.updateById(order);

            rentOrderVto.setSuccess(true);
            rentOrderVto.setOrderId(response.getOrderId());
            rentOrderVto.setOutOrderId(response.getOutOrderId());
            rentOrderVto.setPath(buildOrderDetailPath(order.getOrderId()));
            alipayOrderLifecycleNotifyService.notifyOrderCreated(order);
        } else {
            // 支付宝建单失败：抛异常触发 @Transactional 回滚，保证本地订单与分期一起回滚，
            // 避免出现"支付宝无单、本地有残留单"的脏数据。错误信息通过异常消息透传给前端。
            log.error("创建支付宝租赁订单失败: {}", response.getSubMsg());
            throw new NoUseException(response.getSubMsg() != null ? response.getSubMsg() : "创建支付宝订单失败");
        }

        return rentOrderVto;
    }

    /**
     * 组装支付宝「单次创建租赁订单」请求体：订单基础信息、路径、买家、价格、租期与分期、芝麻/资金预授权、商品明细、收货与默认归还地址等。
     *
     * @param order 本地订单，提供订单号、来源 ID、用户 UUID、租金押金、起止时间、地址、商品与规格 ID 等。
     * @param good  商品，提供标题、goodId。
     * @param attr  规格，提供 attrId、单价、数量等；默认归还地址从码表读取（详细地址、电话、联系人）。
     * @return 已设置 bizModel 的 AlipayCommerceRentOrderCreateRequest，供 alipayClientService.execute 调用。
     */
    private AlipayCommerceRentOrderCreateRequest buildAlipayRentOrderRequest(
            Order order,
            Good good,
            Attr attr,
            Order parentOrder
    ) {
        AlipayCommerceRentOrderCreateRequest request = new AlipayCommerceRentOrderCreateRequest();
        AlipayCommerceRentOrderCreateModel model = new AlipayCommerceRentOrderCreateModel();
        boolean reletOrder = parentOrder != null;

        model.setOutOrderId(order.getOrderNo());
        model.setOrderType(reletOrder ? AlipayRentConstants.ORDER_TYPE_RELET : AlipayRentConstants.ORDER_TYPE_RENT);
        model.setTitle(order.getAttrTitle());
        model.setSourceId(order.getSourceId());

        if (reletOrder) {
            // 支付宝官方 SDK 已明确：续租仍使用同一个 rent.order.create，本质区别在于：
            // 1. order_type = RELET
            // 2. parent_order_id 指向当前这次续租所挂靠的父单
            // 3. relet_info.origin_order_id 指向最初的根单，便于支付宝把多次续租串成一条链
            model.setParentOrderId(parentOrder.getRentOrderId());
            RentReletInfoDTO reletInfo = new RentReletInfoDTO();
            reletInfo.setOriginOrderId(resolveOriginRentOrderId(parentOrder));
            model.setReletInfo(reletInfo);
        }

        RentPathInfoDTO pathInfo = new RentPathInfoDTO();
        String orderDetailPath = buildOrderDetailPath(order.getOrderId());
        pathInfo.setReturnPath(orderDetailPath);
        pathInfo.setReletPath(orderDetailPath);
        pathInfo.setBuyoutPath(orderDetailPath);
        pathInfo.setDetailPath(orderDetailPath);

        List<RentServiceProtocolDTO> protocols = new ArrayList<>();
        RentServiceProtocolDTO protocol = new RentServiceProtocolDTO();
        protocol.setProtocolPath(appendOrderIdToProtocolPath(alipayPlatformConfigService.protocolPath(), order));
        protocol.setProtocolName(alipayPlatformConfigService.protocolName());
        protocols.add(protocol);
        pathInfo.setProtocols(protocols);
        model.setPathInfo(pathInfo);

        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());

        RentOrderPriceInfoDTO priceInfo = new RentOrderPriceInfoDTO();
        priceInfo.setFreight(AlipayRentConstants.ZERO_PRICE);
        priceInfo.setAdditionalPrice(AlipayRentConstants.ZERO_PRICE);

        BigDecimal totalYuan = OrderUtil.convertCentToYuan(order.getOrderTotal());
        BigDecimal totalDepositPrice = OrderUtil.convertCentToYuan(order.getOrderDeposit());
        String buyoutPrice = resolveBuyoutPriceYuan(attr);
        priceInfo.setOrderPrice(totalYuan.toString());
        priceInfo.setDepositPrice(totalDepositPrice.toString());
        priceInfo.setBuyoutPrice(buyoutPrice);
        model.setPriceInfo(priceInfo);

        RentPlanInfoDTO rentPlanInfo = new RentPlanInfoDTO();
        rentPlanInfo.setRentStartTime(OrderUtil.toDate(order.getOrderStart()));
        rentPlanInfo.setRentEndTime(OrderUtil.toDate(order.getOrderEnd()));

        List<RentInstallmentInfo> installments = RentInstallmentPlanSupport.buildRentInstallmentInfos(order, buyoutPrice);
        rentInstallmentInfoEntityMapper.delete(new QueryWrapper<RentInstallmentInfoEntity>().eq("order_id", order.getOrderId()));
        for (RentInstallmentInfo installment : installments) {
            RentInstallmentInfoEntity entity = new RentInstallmentInfoEntity();
            entity.setInstallmentPrice(installment.getInstallmentPrice());
            entity.setPlanPayTime(installment.getPlanPayTime());
            entity.setInstallmentNo(installment.getInstallmentNo());
            entity.setOrderId(order.getOrderId());
            entity.setBuyoutPrice(installment.getBuyoutPrice());
            entity.setStatus(AlipayRentConstants.INSTALLMENT_STATUS_UNPAID);
            rentInstallmentInfoEntityMapper.insert(entity);
        }
        rentPlanInfo.setInstallments(installments);
        model.setRentPlanInfo(rentPlanInfo);

        if (!reletOrder) {
            RentSignInfoDTO rentSignInfo = new RentSignInfoDTO();
            RentCreditInfoDTO creditInfo = new RentCreditInfoDTO();
            creditInfo.setZmServiceId(alipayPlatformConfigService.zmServiceId());
            creditInfo.setCategoryId(resolveRentCategoryId(good));
            rentSignInfo.setCreditInfo(creditInfo);

            RentFundAuthFreezeInfoDTO fundAuthFreezeInfo = new RentFundAuthFreezeInfoDTO();
            fundAuthFreezeInfo.setFreezeNotifyUrl(resolveTradeNotifyUrl());
            fundAuthFreezeInfo.setPayeeUserId(alipayPlatformConfigService.payeeUserId());
            fundAuthFreezeInfo.setRiskAssessmentPrice(totalDepositPrice.toString());
            rentSignInfo.setFundAuthFreezeInfo(fundAuthFreezeInfo);
            model.setRentSignInfo(rentSignInfo);
        }

        model.setTradeAppId(alipayPlatformConfigService.tradeAppId());

        List<RentGoodsDetailInfoDTO> itemInfos = new ArrayList<>();
        RentGoodsDetailInfoDTO itemInfo = new RentGoodsDetailInfoDTO();
        itemInfo.setItemName(good.getGoodTitle());
        itemInfo.setOutItemId(good.getGoodId().toString());
        itemInfo.setOutSkuId(attr.getAttrId().toString());
        itemInfo.setItemCnt(order.getAttrNum().toString());
        itemInfo.setSalePrice(OrderUtil.convertCentToYuan(attr.getAttrAmount()).toString());
        itemInfo.setItemFineness(resolveItemFineness(good));
        itemInfo.setRentModel(alipayPlatformConfigService.rentModel());
        itemInfo.setItemValue(totalDepositPrice.toString());
        itemInfo.setItemFinenessGrade(resolveItemFinenessGrade(good));
        itemInfos.add(itemInfo);
        model.setItemInfos(itemInfos);

        RentOrderReceiverAddressInfoDTO addressInfo = new RentOrderReceiverAddressInfoDTO();
        addressInfo.setDetailedAddress(order.getAddr());
        addressInfo.setTelNumber(order.getUserTel());
        addressInfo.setReceiverName(order.getUserTitle());
        model.setAddressInfo(addressInfo);

        RentOrderReceiverAddressInfoDTO defaultReceivingAddress = new RentOrderReceiverAddressInfoDTO();
        defaultReceivingAddress.setDetailedAddress(alipayPlatformConfigService.returnAddressDetail());
        defaultReceivingAddress.setTelNumber(alipayPlatformConfigService.returnMobile());
        defaultReceivingAddress.setReceiverName(alipayPlatformConfigService.returnConsignee());
        model.setDefaultReceivingAddress(defaultReceivingAddress);

        request.setBizModel(model);
        return request;
    }

    private String resolveRentCategoryId(Good good) {
        if (good != null && StringUtils.hasText(good.getAlipayRentCategoryId())) {
            return good.getAlipayRentCategoryId().trim();
        }
        return alipayPlatformConfigService.rentCategoryId();
    }

    /**
     * 解析买断价（元）。
     * 支付宝租赁接口要求 buyout_price 必须是 > 0 的合法金额，不能传 0、也不能不传。
     * 因此无论 SKU 是否支持买断，统一以 UI 设置的买断金为准，最小值 1 元（100 分）兜底。
     */
    private String resolveBuyoutPriceYuan(Attr attr) {
        int buyoutPrice = (attr != null && attr.getBuyoutval() != null && attr.getBuyoutval() > 0)
                ? attr.getBuyoutval()
                : 100; // 兜底 1 元
        return OrderUtil.convertCentToYuan(buyoutPrice).toString();
    }

    private String resolveItemFineness(Good good) {
        return alipayPlatformConfigService.itemFineness(good == null ? null : good.getItemFineness());
    }

    private String resolveItemFinenessGrade(Good good) {
        if (!"secondHand".equals(resolveItemFineness(good))) {
            return null;
        }
        return alipayPlatformConfigService.itemFinenessGrade(good == null ? null : good.getItemFinenessGrade());
    }

    /**
     * 查询支付宝租赁订单状态，并尝试把支付宝侧的分期支付状态同步到本地分期表及订单 alipayStatus。
     *
     * @param order 本地订单，需含 rentOrderId、outOrderId、userUuid，用于请求支付宝查询接口。
     * @return 支付宝「租赁订单查询」响应，含是否成功、订单状态、分期列表（statement_info）等；失败时仅打日志，仍返回该响应。
     * @throws AlipayApiException 调用支付宝查询接口异常。
     */
    @Override
    public AlipayCommerceRentOrderQueryResponse orderStatusSearch(Order order) throws AlipayApiException {
        AlipayCommerceRentOrderQueryResponse queryResponse = getRentOrderDetailByAlipay(order);
        if (queryResponse.isSuccess()) {
            try {
                updateInstallmentStatusFromAlipay(order, queryResponse);
            } catch (Exception e) {
                log.error("同步分期状态失败: {}", e.getMessage(), e);
            }
        } else {
            log.warn("查询订单{}状态失败: {}", order.getOrderId(), queryResponse.getSubMsg());
        }
        return queryResponse;
    }

    /**
     * 调用支付宝「租赁订单查询」接口，按 outOrderId + orderId + buyerId 查询，并拉取 statement_info、promo_info。
     *
     * @param order 本地订单，提供 outOrderId（orderNo）、rentOrderId、userUuid。
     * @return 支付宝查询响应，成功时含订单详情与分期列表等。
     * @throws AlipayApiException 支付宝接口异常。
     */
    private AlipayCommerceRentOrderQueryResponse getRentOrderDetailByAlipay(Order order) throws AlipayApiException {
        AlipayCommerceRentOrderQueryRequest request = new AlipayCommerceRentOrderQueryRequest();
        com.alipay.api.domain.AlipayCommerceRentOrderQueryModel model =
                new com.alipay.api.domain.AlipayCommerceRentOrderQueryModel();
        model.setOutOrderId(order.getOutOrderId());
        model.setOrderId(order.getRentOrderId());
        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
        List<String> queryOptions = new ArrayList<>();
        queryOptions.add("statement_info");
        queryOptions.add("promo_info");
        model.setQueryOptions(queryOptions);
        request.setBizModel(model);
        return alipayClientService.execute(request);
    }

    /**
     * 根据支付宝查询结果中的分期列表（rentStatementInfos），把已支付的分期同步到本地 rent_installment_info 表，
     * 若有任一分期由未付变为已付，则把订单 alipayStatus 更新为已支付。
     *
     * @param order         本地订单，用于查本地分期列表、更新订单状态。
     * @param queryResponse 支付宝订单查询响应，取 rentStatementInfos（分期号、statementStatus）。
     */
    private void updateInstallmentStatusFromAlipay(Order order, AlipayCommerceRentOrderQueryResponse queryResponse) {
        List<RentOrderStatementInfoVO> rentStatementInfos = queryResponse.getRentStatementInfos();
        if (rentStatementInfos == null || rentStatementInfos.isEmpty()) {
            return;
        }

        List<RentInstallmentInfoEntity> installmentList = rentInstallmentInfoEntityMapper.selectList(
                new QueryWrapper<RentInstallmentInfoEntity>().eq("order_id", order.getOrderId()));

        boolean hasPaidInstallment = false;
        for (RentOrderStatementInfoVO statementInfo : rentStatementInfos) {
            try {
                Long installmentNo = statementInfo.getInstallmentNo();
                String status = statementInfo.getStatementStatus();

                boolean isPaid = AlipayRentConstants.STATUS_PAID.equals(status);
                if (isPaid) {
                    hasPaidInstallment = true;
                }

                RentInstallmentInfoEntity installment = installmentList.stream()
                        .filter(item -> item.getInstallmentNo() != null && item.getInstallmentNo().equals(installmentNo))
                        .findFirst()
                        .orElse(null);

                if (installment == null) {
                    log.warn("订单{}未找到分期{}", order.getOrderId(), installmentNo);
                    if (isPaid) {
                        installmentPaymentSyncService.markPeriodPaid(order, installmentNo.intValue(), null, new Date());
                    }
                    continue;
                }

                if (isPaid) {
                    installmentPaymentSyncService.markPeriodPaid(order, installmentNo.intValue(), null, new Date());
                    log.info("订单{}分期{}已同步为已支付", order.getOrderId(), installmentNo);
                }
            } catch (Exception e) {
                log.error("处理分期状态失败: {}", e.getMessage(), e);
            }
        }

        if (hasPaidInstallment && AlipayRentConstants.STATUS_APPROVED.equals(order.getAlipayStatus())) {
            String oldStatus = order.getAlipayStatus();
            order.setAlipayStatus(AlipayRentConstants.STATUS_PAID);
            order.setUpdateTime(new Date());
            order.updateById();
            log.info("订单{}分期已付，状态 APPROVED -> PAID", order.getOrderId());
            alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "支付宝状态同步");
        }
    }

    /**
     * 商家确认审核：同意时调支付宝「租赁订单履约审核通过」接口，拒绝时不调支付宝（仅业务侧处理）。
     *
     * @param order   本地订单，提供 rentOrderId、outOrderId、userUuid，用于组请求。
     * @param isAgree 是否同意；true 时调用支付宝履约审核通过。
     * @throws AlipayApiException 调用支付宝接口异常。
     */
    @Override
    public void merchantConfirm(Order order, Boolean isAgree) throws AlipayApiException {
        String oldStatus = order.getAlipayStatus();
        if (isAgree) {
            AlipayCommerceRentOrderFulfillmentApproveRequest request =
                    new AlipayCommerceRentOrderFulfillmentApproveRequest();
            AlipayCommerceRentOrderFulfillmentApproveModel model =
                    new AlipayCommerceRentOrderFulfillmentApproveModel();
            model.setOrderId(order.getRentOrderId());
            model.setOutOrderId(order.getOutOrderId());
            AlipayUserIdentityUtil.applyUserIdentity(model, order.getUserUuid());
            request.setBizModel(model);

            AlipayCommerceRentOrderFulfillmentApproveResponse response = alipayClientService.execute(request);
            if (response.isSuccess()) {
                order.setAlipayStatus(AlipayRentConstants.STATUS_APPROVED);
            } else {
                log.error("订单审核失败: {}", response.getSubMsg());
                throw new NoUseException(response.getSubMsg());
            }
        } else {
            closeOrder(order);
            order.setAlipayStatus(AlipayRentConstants.STATUS_CLOSED);
        }
        order.setUpdateTime(new Date());
        order.updateById();
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "后台审核");
    }

    /**
     * 取消并退款（仅更新本地状态为待取消）：不调支付宝关单/退款，仅把订单 alipayStatus 置为 PENDING_CANCLE，后续由退款或关单流程处理。
     *
     * @param order 本地订单，更新其 alipayStatus 后 updateById。
     */
    @Override
    public void cancelAndRefund(Order order) {
        String oldStatus = order.getAlipayStatus();
        order.setAlipayStatus(AlipayRentConstants.STATUS_PENDING_CANCLE);
        order.setUpdateTime(new Date());
        order.updateById();
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "小程序退款申请");
    }

    /**
     * 退款：同意时调支付宝「统一收单交易退款」接口。
     *
     * 退款交易号优先使用独立的 `paymentTradeNo`，兼容历史数据时再回退到 `orderAuthNo`。
     * 这样可以避免把“冻结授权号”和“租金支付交易号”继续混用。
     *
     * @param order   本地订单，提供 paymentTradeNo / orderAuthNo、outOrderId、orderTotal（分转元）等组退款请求。
     * @param isAgree 是否同意退款；false 时只更新本地状态为 PAID，不调支付宝。
     * @throws AlipayApiException 调用支付宝退款或关单异常。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Order order, Boolean isAgree) throws AlipayApiException {
        String oldStatus = order.getAlipayStatus();
        if (!isAgree) {
            order.setAlipayStatus(AlipayRentConstants.STATUS_PAID);
            order.setUpdateTime(new Date());
            order.updateById();
            alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "后台退款驳回");
            return;
        }

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        com.alipay.api.domain.AlipayTradeRefundModel model = new com.alipay.api.domain.AlipayTradeRefundModel();
        String refundTradeNo = hasText(order.getPaymentTradeNo()) ? order.getPaymentTradeNo() : order.getOrderAuthNo();
        if (!hasText(refundTradeNo)) {
            throw new NoUseException("订单缺少可退款的支付交易号");
        }
        model.setTradeNo(refundTradeNo);
        model.setRefundReason("正常退款");
        model.setOutRequestNo(order.getOutOrderId());
        model.setRefundAmount(OrderUtil.convertCentToYuan(order.getOrderTotal()).toString());
        List<String> queryOptions = new ArrayList<>();
        queryOptions.add("refund_detail_item_list");
        model.setQueryOptions(queryOptions);
        request.setBizModel(model);

        AlipayTradeRefundResponse response = alipayClientService.execute(request);
        if (response.isSuccess()) {
            order.setAlipayStatus(AlipayRentConstants.STATUS_APPROVED);
            closeOrder(order);
            order.setAlipayStatus(AlipayRentConstants.STATUS_CLOSED);
        } else {
            log.error("退款失败: {}", response.getSubMsg());
            alipayOrderLifecycleNotifyService.notifyException(
                    "退款失败",
                    "支付宝退款接口返回失败",
                    response.getSubMsg(),
                    order,
                    null
            );
            throw new NoUseException(response.getSubMsg());
        }
        order.setUpdateTime(new Date());
        order.updateById();
        alipayOrderLifecycleNotifyService.notifyStatusChanged(order, oldStatus, order.getAlipayStatus(), "后台退款处理");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String appendOrderIdToProtocolPath(String protocolPath, Order order) {
        if (!hasText(protocolPath) || order == null || order.getOrderId() == null) {
            return protocolPath;
        }
        String joiner = protocolPath.contains("?") ? "&" : "?";
        return protocolPath + joiner + "orderId=" + order.getOrderId();
    }

    private String buildOrderDetailPath(Integer orderId) {
        String template = alipayPlatformConfigService.orderDetailPathTemplate();
        if (!hasText(template) || orderId == null) {
            return template;
        }
        String value = String.valueOf(orderId);
        if (template.contains("${orderId}")) {
            return template.replace("${orderId}", value);
        }
        if (template.contains("{orderId}")) {
            return template.replace("{orderId}", value);
        }
        return template + value;
    }

    /**
     * 支付宝交易通知地址统一从中台读取。
     *
     * @return 支付宝交易通知地址
     */
    private String resolveTradeNotifyUrl() {
        return zhongtaiConfigService.getString("alipay", "alipay.notify.trade-url");
    }

    /**
     * 调用支付宝「关闭租赁订单」接口，仅在下单/签约/审核/已发货等可关单状态下执行；关单成功后不更新本地订单状态（由调用方处理）。
     *
     * @param order 本地订单，提供 rentOrderId、outOrderId、userUuid；状态需为 CREATED/SIGNED/APPROVED/DELIVERED 之一。
     * @throws AlipayApiException 调用支付宝关单接口异常；若接口返回失败则抛 NoUseException。
     */
    @Override
    public void closeOrder(Order order) throws AlipayApiException {
        if (!AlipayRentConstants.STATUS_CREATED.equals(order.getAlipayStatus())
                && !AlipayRentConstants.STATUS_SIGNED.equals(order.getAlipayStatus())
                && !AlipayRentConstants.STATUS_APPROVED.equals(order.getAlipayStatus())
                && !AlipayRentConstants.STATUS_DELIVERED.equals(order.getAlipayStatus())) {
            throw new NoUseException("订单状态不支持关闭");
        }

        AlipayCommerceRentOrderCloseRequest request = new AlipayCommerceRentOrderCloseRequest();
        AlipayCommerceRentOrderCloseModel model = new AlipayCommerceRentOrderCloseModel();
        model.setOrderId(order.getRentOrderId());
        model.setOutOrderId(order.getOutOrderId());
        AlipayUserIdentityUtil.applyBuyerIdentity(model, order.getUserUuid());
        model.setReasonCode("3150");
        model.setReasonDesc("协商一致，订单关闭");
        request.setBizModel(model);

        AlipayCommerceRentOrderCloseResponse response = alipayClientService.execute(request);
        if (!response.isSuccess()) {
            log.error("订单关闭失败: {}", response.getSubMsg());
            throw new NoUseException(response.getSubMsg());
        }
    }

    private int calFinal(Order order) {
        return order.getOrderTotal() + order.getOrderDamage() + order.getOrderBeamo();
    }

    private Integer resolveInstallmentBillingCycle(Attr attr) {
        if (attr != null && attr.getAttrTradeType() != null && attr.getAttrTradeType() > 1) {
            return attr.getAttrTradeType();
        }
        return 2;
    }

    private Attr resolveCreateAttr(String outSkuId) {
        if (!hasText(outSkuId)) {
            return null;
        }
        if (outSkuId.matches("\\d+") && outSkuId.length() <= 9) {
            Attr attr = baseApily.getAttrMapper().selectById(Integer.valueOf(outSkuId));
            if (attr != null) {
                return attr;
            }
        }
        List<Attr> attrs = baseApily.getAttrMapper().selectList(
                new QueryWrapper<Attr>().eq("alipay_sku_id", outSkuId));
        return attrs == null || attrs.isEmpty() ? null : attrs.get(0);
    }

    /**
     * 把原订单重新从数据库读取一遍，避免调用方传入的是旧对象，
     * 导致续租时拿到过期的支付宝单号、状态或租期结束时间。
     */
    private Order reloadOriginOrder(Order originOrder) {
        if (originOrder == null || originOrder.getOrderId() == null) {
            throw new NoUseException("原订单不存在，无法续租");
        }
        Order latestOriginOrder = orderMapper.selectById(originOrder.getOrderId());
        if (latestOriginOrder == null) {
            throw new NoUseException("原订单不存在，无法续租");
        }
        return latestOriginOrder;
    }

    /**
     * 续租前置校验。
     * 当前校验重点是：
     * 1. 只能续租当前登录用户自己的订单；
     * 2. 原单必须已经挂上支付宝租赁订单号；
     * 3. 原单必须已经确认收货，避免未履约订单提前生成续租子单；
     * 4. 原单租赁结束时间必须已经到期；
     * 5. 续租后的总租期不能超过商品 SKU 当前配置的最大租期。
     */
    private void validateReletRequest(Order originOrder, Integer renewDuration, String userUuid) {
        if (renewDuration == null || renewDuration <= 0) {
            throw new NoUseException("续租时长必须大于 0");
        }
        if (!userUuid.equals(originOrder.getUserUuid())) {
            throw new NoUseException("原订单不属于当前用户，无法续租");
        }
        if (!hasText(originOrder.getRentOrderId())) {
            throw new NoUseException("原订单缺少支付宝租赁订单号，无法续租");
        }
        if (!hasText(originOrder.getOutOrderId())) {
            throw new NoUseException("原订单缺少商户订单号，无法续租");
        }
        if (originOrder.getOrderEnd() == null || originOrder.getOrderKeep() == null || originOrder.getOrderKeep() <= 0) {
            throw new NoUseException("原订单租期数据不完整，无法续租");
        }
        if (!AlipayRentConstants.STATUS_RECEIVED.equals(normalizeStatus(originOrder.getAlipayStatus()))) {
            throw new NoUseException("只有用户已确认收货的订单才支持续租");
        }
        if (originOrder.getOrderEnd() > System.currentTimeMillis()) {
            throw new NoUseException("当前订单租赁时间尚未结束，暂不能续租");
        }

        Attr attr = baseApily.getAttrMapper().selectById(originOrder.getAttrId());
        if (attr != null && attr.getMaxRent() != null && attr.getMaxRent() > 0) {
            int totalDuration = originOrder.getOrderKeep() + renewDuration;
            if (totalDuration > attr.getMaxRent()) {
                throw new NoUseException("续租后总租期超过商品可租上限");
            }
        }
    }

    /**
     * 构造本地续租子单。
     * 这里不会复用原单主键，而是新生成一笔订单记录，让本地订单、支付宝子单、支付流水三者一一对应。
     */
    private Order buildReletOrder(Order originOrder, Attr attr, Good good, Integer renewDuration, String sourceId) {
        Order reletOrder = new Order();

        reletOrder.setUserId(originOrder.getUserId());
        reletOrder.setUserUuid(originOrder.getUserUuid());
        reletOrder.setUserTitle(originOrder.getUserTitle());
        reletOrder.setUserTel(originOrder.getUserTel());
        reletOrder.setAddr(originOrder.getAddr());
        reletOrder.setReturnInfo(originOrder.getReturnInfo());
        reletOrder.setAvatar(originOrder.getAvatar());
        reletOrder.setEm(originOrder.getEm());

        reletOrder.setGoodId(originOrder.getGoodId());
        reletOrder.setGoodTitle(originOrder.getGoodTitle());
        reletOrder.setGoodCover(originOrder.getGoodCover());
        reletOrder.setAttrId(originOrder.getAttrId());
        reletOrder.setAttrTitle(originOrder.getAttrTitle());
        reletOrder.setAttrAmount(originOrder.getAttrAmount());
        reletOrder.setAttrNum(originOrder.getAttrNum());
        reletOrder.setRentToSend(originOrder.getRentToSend());
        reletOrder.setOfflinePickup(originOrder.getOfflinePickup());
        reletOrder.setPickupAddr(originOrder.getPickupAddr());

        // 续租单沿用原冻结授权号，便于后续后台查询押金/售后赔付时仍然能追到同一笔押金链路。
        // 但 paymentTradeNo 不能复制，因为每一笔续租子单会产生自己独立的租金支付交易号。
        reletOrder.setOrderAuthNo(originOrder.getOrderAuthNo());
        reletOrder.setOrderOperationNo(originOrder.getOrderOperationNo());

        reletOrder.setAlipayStatus(AlipayRentConstants.STATUS_CREATED);
        reletOrder.setSourceId(hasText(sourceId) ? sourceId.trim() : originOrder.getSourceId());
        reletOrder.setCreatetime(System.currentTimeMillis());

        long startTime = originOrder.getOrderEnd();
        long endTime = startTime + renewDuration.longValue() * 24L * 60L * 60L * 1000L;
        reletOrder.setOrderStart(startTime);
        reletOrder.setOrderEnd(endTime);
        reletOrder.setOrderKeep(renewDuration);

        // 续租金额优先沿用“原单实际成交的日均租金”，这样比直接读当前 SKU 单价更贴近真实合同价格。
        // 如果原单租期或金额异常，再回退到当前规格 daily_rent * 数量 * 续租天数。
        int totalAmount = calculateReletOrderTotal(originOrder, attr, renewDuration);
        reletOrder.setOrderTotal(totalAmount);

        // 续租不应重新冻结押金，因此本地续租子单的押金金额直接置 0；
        // 真正的押金链路仍然挂在原单的 auth_no 上，由支付宝通过 origin_order_id 关联。
        reletOrder.setOrderDeposit(0);
        reletOrder.setOrderRestDeposit(0);
        reletOrder.setOrderDamage(0);
        reletOrder.setOrderBeamo(0);

        return reletOrder;
    }

    /**
     * 多次续租时，支付宝需要区分：
     * 1. parent_order_id：当前续租所直接挂靠的上一层父单
     * 2. relet_info.origin_order_id：整条续租链最开始的根单
     * 因此这里优先从续租关系表读取根单号；如果父单本身还没有续租关系，则回退到父单自己的 rentOrderId。
     */
    private String resolveOriginRentOrderId(Order order) {
        return reletRelationService.resolveOriginRentOrderId(order);
    }

    /**
     * 计算续租子单的租金总额。
     * 这里优先复用原单“实际成交的日均租金”，防止商品现价变化影响已签约订单的续租价格。
     */
    private int calculateReletOrderTotal(Order originOrder, Attr attr, Integer renewDuration) {
        if (originOrder.getOrderTotal() != null
                && originOrder.getOrderTotal() > 0
                && originOrder.getOrderKeep() != null
                && originOrder.getOrderKeep() > 0) {
            BigDecimal dayPrice = BigDecimal.valueOf(originOrder.getOrderTotal())
                    .divide(BigDecimal.valueOf(originOrder.getOrderKeep()), 0, RoundingMode.HALF_UP);
            return dayPrice.multiply(BigDecimal.valueOf(renewDuration)).intValue();
        }
        if (attr != null && attr.getAttrAmount() != null && attr.getAttrAmount() > 0) {
            int quantity = originOrder.getAttrNum() == null || originOrder.getAttrNum() <= 0 ? 1 : originOrder.getAttrNum();
            return attr.getAttrAmount() * quantity * renewDuration;
        }
        throw new NoUseException("无法计算续租金额");
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private Order fillOrder(Attr attr, Order order) {
        if (attr == null) {
            return order;
        }
        order.setPenalAmount(attr.getPenalAmount());
        order.setOrderNo(CommonUtil.getNo());
        order.setOrderRequestNo(order.getOrderNo() + "00");
        order.setOrderRestDeposit(order.getOrderDeposit());

        Long start = order.getOrderStart();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);
        int monthBefore = calendar.get(Calendar.MONTH);
        calendar.add(Calendar.DATE, 1);
        int monthAfter = calendar.get(Calendar.MONTH);
        if (monthBefore != monthAfter) {
            order.setOrderTradeDate("月底");
        } else {
            calendar.setTimeInMillis(start);
            order.setOrderTradeDate(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        }
        return order;
    }

}
