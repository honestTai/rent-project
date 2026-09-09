package com.fly.rent.legacy.service.impl;

import com.alipay.api.domain.AlipayCommerceRentOrderCreateModel;
import com.alipay.api.domain.RentPathInfoDTO;
import com.alipay.api.request.AlipayCommerceRentOrderCreateRequest;
import com.alipay.api.response.AlipayCommerceRentOrderCreateResponse;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.User;
import com.fly.rent.legacy.dto.apilyRentUtil.AddressInfo;
import com.fly.rent.legacy.dto.apilyRentUtil.BaseApily;
import com.fly.rent.legacy.dto.apilyRentUtil.OrderRentParams;
import com.fly.rent.legacy.dto.apilyRentUtil.RentOrderCreateParam;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.ApiResponse;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.BizParamData;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.CostInfo;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.RentOrderVto;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RentOrderServiceImplTest {

    private static final String ORDER_DETAIL_TEMPLATE = "/pages/order-detail/order-detail?orderId={orderId}";
    private static final String ORDER_DETAIL_PATH = "/pages/order-detail/order-detail?orderId=32";

    @Test
    void buildOrderDetailPathUsesRentNewOrderDetailRoute() {
        RentOrderServiceImpl service = new RentOrderServiceImpl();
        AlipayPlatformConfigService configService = mock(AlipayPlatformConfigService.class);
        when(configService.orderDetailPathTemplate()).thenReturn(ORDER_DETAIL_TEMPLATE);
        ReflectionTestUtils.setField(service, "alipayPlatformConfigService", configService);

        String path = ReflectionTestUtils.invokeMethod(service, "buildOrderDetailPath", 32);

        assertEquals(ORDER_DETAIL_PATH, path);
    }

    @Test
    void buildOrderUsesStorePickupWhenRequestedAndSupported() {
        RentOrderServiceImpl service = new RentOrderServiceImpl();
        ZhongtaiConfigService platformConfig = mock(ZhongtaiConfigService.class);
        String returnAddress = "[{\"name\":\"演示归还点\",\"addr\":\"演示地址\"}]";
        when(platformConfig.getString("alipay", "rent.return.address.json")).thenReturn(returnAddress);
        ReflectionTestUtils.setField(service, "zhongtaiConfigService", platformConfig);
        RentOrderCreateParam param = buildCreateParam();
        param.setPickupType("store");
        User user = buildUser();
        Attr attr = buildAttr();
        Good good = buildGood();
        ApiResponse apiResponse = buildApiResponse();

        Order order = ReflectionTestUtils.invokeMethod(service, "buildOrder", param, user, attr, good, apiResponse);

        assertEquals(0, order.getOfflinePickup());
        assertEquals("成都市高新区天府大道自提点", order.getPickupAddr());
        assertEquals(0, order.getFreight());
        assertEquals(returnAddress, order.getReturnInfo());
    }

    @Test
    void createAlipayRentOrderReturnsRentNewOrderDetailPathAfterSuccess() throws Exception {
        RentOrderServiceImpl service = new RentOrderServiceImpl();
        BaseApily baseApily = new BaseApily();
        GoodMapper goodMapper = mock(GoodMapper.class);
        AttrMapper attrMapper = mock(AttrMapper.class);
        baseApily.setGoodMapper(goodMapper);
        baseApily.setAttrMapper(attrMapper);

        Good good = new Good();
        good.setGoodId(7);
        good.setGoodTitle("MacBook Air");
        good.setAlipayRentCategoryId("RENT_COMPUTER");
        good.setItemFineness("wholeNew");
        Attr attr = new Attr();
        attr.setAttrId(8);
        attr.setAttrAmount(100);
        attr.setBuyoutval(100);

        when(goodMapper.selectById(7)).thenReturn(good);
        when(attrMapper.selectById(8)).thenReturn(attr);

        AlipayPlatformConfigService configService = mock(AlipayPlatformConfigService.class);
        when(configService.orderDetailPathTemplate()).thenReturn(ORDER_DETAIL_TEMPLATE);
        when(configService.protocolPath()).thenReturn("/pages/agreement/agreement");
        when(configService.protocolName()).thenReturn("租赁协议");
        when(configService.zmServiceId()).thenReturn("zm-service-id");
        when(configService.payeeUserId()).thenReturn("2088000000000000");
        when(configService.tradeAppId()).thenReturn("2021000000000000");
        when(configService.rentModel()).thenReturn("DAILY");
        when(configService.itemFineness(eq("wholeNew"))).thenReturn("wholeNew");
        when(configService.returnAddressDetail()).thenReturn("测试归还地址");
        when(configService.returnMobile()).thenReturn("10000000000");
        when(configService.returnConsignee()).thenReturn("测试收件人");

        ZhongtaiConfigService zhongtaiConfigService = mock(ZhongtaiConfigService.class);
        when(zhongtaiConfigService.getString("alipay", "alipay.notify.trade-url")).thenReturn("https://example.com/notify");

        AlipayCommerceRentOrderCreateResponse response = mock(AlipayCommerceRentOrderCreateResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(response.getOrderId()).thenReturn("202606280001");
        when(response.getOutOrderId()).thenReturn("NO202606280001");
        AlipayClientService alipayClientService = mock(AlipayClientService.class);
        when(alipayClientService.execute(any(AlipayCommerceRentOrderCreateRequest.class))).thenReturn(response);

        OrderMapper orderMapper = mock(OrderMapper.class);
        RentInstallmentInfoEntityMapper installmentMapper = mock(RentInstallmentInfoEntityMapper.class);
        AlipayOrderLifecycleNotifyService notifyService = mock(AlipayOrderLifecycleNotifyService.class);

        ReflectionTestUtils.setField(service, "baseApily", baseApily);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "rentInstallmentInfoEntityMapper", installmentMapper);
        ReflectionTestUtils.setField(service, "alipayClientService", alipayClientService);
        ReflectionTestUtils.setField(service, "alipayOrderLifecycleNotifyService", notifyService);
        ReflectionTestUtils.setField(service, "zhongtaiConfigService", zhongtaiConfigService);
        ReflectionTestUtils.setField(service, "alipayPlatformConfigService", configService);

        RentOrderVto result = service.createAlipayRentOrder(buildOrder());

        assertTrue(result.isSuccess());
        assertEquals("202606280001", result.getOrderId());
        assertEquals("NO202606280001", result.getOutOrderId());
        assertEquals(ORDER_DETAIL_PATH, result.getPath());

        ArgumentCaptor<AlipayCommerceRentOrderCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(AlipayCommerceRentOrderCreateRequest.class);
        verify(alipayClientService).execute(requestCaptor.capture());
        AlipayCommerceRentOrderCreateModel model =
                (AlipayCommerceRentOrderCreateModel) requestCaptor.getValue().getBizModel();
        RentPathInfoDTO pathInfo = model.getPathInfo();
        assertEquals(ORDER_DETAIL_PATH, pathInfo.getDetailPath());
        assertEquals(ORDER_DETAIL_PATH, pathInfo.getReturnPath());
        assertEquals(ORDER_DETAIL_PATH, pathInfo.getReletPath());
        assertEquals(ORDER_DETAIL_PATH, pathInfo.getBuyoutPath());
    }

    private Order buildOrder() {
        Order order = new Order();
        order.setOrderId(32);
        order.setGoodId(7);
        order.setAttrId(8);
        order.setOrderNo("NO202606280001");
        order.setAttrTitle("MacBook Air");
        order.setSourceId("source-1");
        order.setUserUuid("2088000000000000");
        order.setOrderTotal(12_000);
        order.setOrderDeposit(1_000);
        order.setAttrNum(1);
        order.setOrderStart(1_782_628_800_000L);
        order.setOrderEnd(1_793_232_000_000L);
        order.setOrderTotalRentPeriods(1);
        order.setOrderTradeType(1);
        order.setAddr("测试收货地址");
        order.setUserTel("10000000000");
        order.setUserTitle("测试用户");
        return order;
    }

    private RentOrderCreateParam buildCreateParam() {
        AddressInfo address = new AddressInfo();
        address.setAddress("成都市高新区天府大道自提点");
        address.setFullname("测试用户");
        address.setMobilePhone("10000000000");

        OrderRentParams rentParams = new OrderRentParams();
        rentParams.setDuration(3);
        rentParams.setQuantity(1);
        rentParams.setRentStartTime("2026-07-02 10:00:00");
        rentParams.setRentEndTime("2026-07-05 10:00:00");
        rentParams.setAddressInfo(address);

        RentOrderCreateParam param = new RentOrderCreateParam();
        param.setSourceId("source-1");
        param.setOutSkuId("8");
        param.setAddressInfo(address);
        param.setOrderRentParams(rentParams);
        param.setApiResponse(buildApiResponse());
        return param;
    }

    private User buildUser() {
        User user = new User();
        user.setUserId(6);
        user.setUuid("2088000000000000");
        user.setUserTitle("测试用户");
        user.setRealName("测试用户");
        user.setUserTel("10000000000");
        return user;
    }

    private Attr buildAttr() {
        Attr attr = new Attr();
        attr.setAttrId(8);
        attr.setGoodId(7);
        attr.setAttrTitle("MacBook Air");
        attr.setAttrAmount(100);
        attr.setAttrDeposit(1000);
        attr.setAttrNum(5);
        attr.setRentToSend(0);
        return attr;
    }

    private Good buildGood() {
        Good good = new Good();
        good.setGoodId(7);
        good.setGoodTitle("MacBook Air");
        good.setGoodCover("cover.jpg");
        good.setOfflinePickup(0);
        good.setFreight(0);
        return good;
    }

    private ApiResponse buildApiResponse() {
        CostInfo costInfo = new CostInfo();
        costInfo.deposit = "10.00";
        BizParamData bizParamData = new BizParamData();
        bizParamData.costInfo = costInfo;
        ApiResponse response = new ApiResponse();
        response.success = true;
        response.bizParamData = bizParamData;
        return response;
    }
}
