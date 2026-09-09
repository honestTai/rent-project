package com.fly.rent.capability.risk;

import com.alipay.api.domain.ShipGoodsRiskVO;
import com.alipay.api.request.AlipayCloudTraasCloudriskRentriskQueryRequest;
import com.alipay.api.response.AlipayCloudTraasCloudriskRentriskQueryResponse;
import com.alipay.api.response.AlipayCommerceRentRiskConsultResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.capability.CapabilityConfigService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.User;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.legacy.service.RentOrderCheckService;
import com.fly.rent.mapper.AttrMapper;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRiskServiceTest {

    @Mock
    private RentOrderCheckService rentOrderCheckService;
    @Mock
    private AlipayClientService alipayClientService;
    @Mock
    private CapabilityConfigService configService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private GoodMapper goodMapper;
    @Mock
    private AttrMapper attrMapper;

    @Test
    void assessUsesCommerceRentRiskConsultResponse() throws Exception {
        Order order = order();
        AlipayCommerceRentRiskConsultResponse response = new AlipayCommerceRentRiskConsultResponse();
        response.setCode("10000");
        response.setMsg("Success");
        response.setProductEdition("PRO");
        AlipayCommerceRentRiskConsultResponse shipResponse = new AlipayCommerceRentRiskConsultResponse();
        shipResponse.setCode("10000");
        shipResponse.setMsg("Success");
        ShipGoodsRiskVO shipRisk = new ShipGoodsRiskVO();
        shipRisk.setCanShipFlag(Boolean.TRUE);
        shipRisk.setRiskCode("CAN_SHIP");
        shipResponse.setShipGoodsRiskModels(Collections.singletonList(shipRisk));
        when(rentOrderCheckService.queryUserRisk(order)).thenReturn(response);
        when(rentOrderCheckService.queryUserRisk(eq(order), eq(Collections.singletonList("VERTICAL_RENT_RISK"))))
                .thenReturn(shipResponse);

        Map<String, Object> result = service().assess(order);

        verify(rentOrderCheckService).queryUserRisk(order);
        verify(rentOrderCheckService).queryUserRisk(eq(order), eq(Collections.singletonList("VERTICAL_RENT_RISK")));
        verify(alipayClientService, never()).execute(any(AlipayCloudTraasCloudriskRentriskQueryRequest.class));
        assertEquals("rent-shield", result.get("riskProvider"));
        assertEquals("PRO", result.get("productEdition"));
        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) result.get("risk");
        assertEquals("PRO", risk.get("productEdition"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shipRisks = (List<Map<String, Object>>) risk.get("shipGoodsRiskModels");
        assertEquals(1, shipRisks.size());
        assertEquals("CAN_SHIP", shipRisks.get(0).get("riskCode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> alipayRisk = (Map<String, Object>) result.get("alipayRisk");
        assertTrue((Boolean) alipayRisk.get("available"));
        assertSame(risk, alipayRisk.get("raw"));
    }

    @Test
    void assessMarksCommerceRentRiskConsultFailureUnavailable() {
        Order order = order();
        AlipayCommerceRentRiskConsultResponse response = new AlipayCommerceRentRiskConsultResponse();
        response.setCode("40004");
        response.setMsg("Business Failed");
        response.setSubCode("ORDER_NOT_SUPPORT");
        response.setSubMsg("该订单不支持咨询");
        AlipayCommerceRentRiskConsultResponse shipResponse = new AlipayCommerceRentRiskConsultResponse();
        shipResponse.setCode("10000");
        shipResponse.setMsg("Success");
        when(rentOrderCheckService.queryUserRisk(order)).thenReturn(response);
        when(rentOrderCheckService.queryUserRisk(eq(order), eq(Collections.singletonList("VERTICAL_RENT_RISK"))))
                .thenReturn(shipResponse);

        Map<String, Object> result = service().assess(order);

        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) result.get("risk");
        assertFalse((Boolean) risk.get("available"));
        assertEquals("该订单不支持咨询", risk.get("errorMessage"));
        @SuppressWarnings("unchecked")
        Map<String, Object> alipayRisk = (Map<String, Object>) result.get("alipayRisk");
        assertFalse((Boolean) alipayRisk.get("available"));
        assertEquals("ORDER_NOT_SUPPORT", alipayRisk.get("subCode"));
    }

    @Test
    void assessSkipsCommerceRentRiskConsultForTerminalOrder() throws Exception {
        Order order = order();
        order.setAlipayStatus(AlipayRentConstants.STATUS_FINISHED);

        Map<String, Object> result = service().assess(order);

        verify(rentOrderCheckService, never()).queryUserRisk(order);
        verify(rentOrderCheckService, never()).queryUserRisk(eq(order), anyList());
        verify(alipayClientService, never()).execute(any(AlipayCloudTraasCloudriskRentriskQueryRequest.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) result.get("risk");
        assertFalse((Boolean) risk.get("available"));
        assertTrue((Boolean) risk.get("skipped"));
        assertEquals("ORDER_STATUS_NOT_SUPPORT", risk.get("subCode"));
        assertEquals(AlipayRentConstants.STATUS_FINISHED, risk.get("orderStatus"));
        @SuppressWarnings("unchecked")
        Map<String, Object> alipayRisk = (Map<String, Object>) result.get("alipayRisk");
        assertFalse((Boolean) alipayRisk.get("available"));
        assertEquals("ORDER_STATUS_NOT_SUPPORT", alipayRisk.get("subCode"));
    }

    @Test
    void assessUsesCloudRentRiskWhenConfigured() throws Exception {
        Order order = cloudOrder();
        User user = new User();
        user.setUuid("OPEN-ID-001");
        user.setUserTel("13900001111");
        user.setIdCard("510000199001010000");
        Good good = new Good();
        good.setGoodTitle("MacBook Air");
        good.setAlipayRentCategoryId("RENT_COMPUTER");
        Attr attr = new Attr();
        attr.setAttrAmount(6000);
        AlipayCloudTraasCloudriskRentriskQueryResponse response = new AlipayCloudTraasCloudriskRentriskQueryResponse();
        response.setCode("10000");
        response.setMsg("Success");
        response.setRiskRank("LOW");
        response.setRiskName("低风险");
        response.setRiskDesc("暂无明显风险");

        when(configService.value("alipay.risk.provider", "rent-shield")).thenReturn("cloud-rent-risk");
        when(configService.value("alipay.cloud-rent-risk.risk-biz-scene", "RENT_ORDER")).thenReturn("RENT_ORDER");
        when(configService.value("alipay.cloud-rent-risk.source", "ALIPAY")).thenReturn("ALIPAY");
        when(configService.value("alipay.cloud-rent-risk.user-authorization", "1")).thenReturn("1");
        when(configService.value("alipay.cloud-rent-risk.customer-type", "MOBILE")).thenReturn("MOBILE");
        when(userMapper.selectById(9)).thenReturn(user);
        when(goodMapper.selectById(6)).thenReturn(good);
        when(attrMapper.selectById(3)).thenReturn(attr);
        when(alipayClientService.execute(any(AlipayCloudTraasCloudriskRentriskQueryRequest.class))).thenReturn(response);

        Map<String, Object> result = service().assess(order);

        verify(rentOrderCheckService, never()).queryUserRisk(order);
        verify(rentOrderCheckService, never()).queryUserRisk(eq(order), anyList());
        ArgumentCaptor<AlipayCloudTraasCloudriskRentriskQueryRequest> captor =
                ArgumentCaptor.forClass(AlipayCloudTraasCloudriskRentriskQueryRequest.class);
        verify(alipayClientService).execute(captor.capture());
        AlipayCloudTraasCloudriskRentriskQueryRequest request = captor.getValue();
        com.alipay.api.domain.AlipayCloudTraasCloudriskRentriskQueryModel model =
                (com.alipay.api.domain.AlipayCloudTraasCloudriskRentriskQueryModel) request.getBizModel();
        assertEquals("OUT-31", model.getOutBizNo());
        assertEquals("RENT_ORDER", model.getRiskBizScene());
        assertEquals("MOBILE", model.getCustomerType());
        assertEquals("13900001111", model.getCustomerId());
        assertEquals("ALIPAY", model.getSource());
        assertEquals("1", model.getUserAuthorization());
        assertEquals("OPEN-ID-001", model.getCustomerDetail().getAlipayOpenId());

        assertEquals("cloud-rent-risk", result.get("riskProvider"));
        @SuppressWarnings("unchecked")
        Map<String, Object> cloudRisk = (Map<String, Object>) result.get("cloudRentRisk");
        assertEquals("LOW", cloudRisk.get("riskRank"));
        @SuppressWarnings("unchecked")
        Map<String, Object> alipayRisk = (Map<String, Object>) result.get("alipayRisk");
        assertTrue((Boolean) alipayRisk.get("available"));
    }

    private OrderRiskService service() {
        return new OrderRiskService(
                rentOrderCheckService,
                alipayClientService,
                configService,
                userMapper,
                goodMapper,
                attrMapper,
                new ObjectMapper()
        );
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId(31);
        order.setOrderNo("ORDER-31");
        order.setOutOrderId("OUT-31");
        order.setUserUuid("OPEN-ID-001");
        return order;
    }

    private Order cloudOrder() {
        Order order = order();
        order.setUserId(9);
        order.setGoodId(6);
        order.setAttrId(3);
        order.setGoodTitle("MacBook Air");
        order.setAttrTitle("16G");
        order.setAttrNum(1);
        order.setAttrAmount(8000);
        order.setOrderDeposit(100);
        order.setOrderTotal(12000);
        order.setOrderFirstAmount(12000);
        order.setOrderEveryAmount(12000);
        order.setOrderTotalRentPeriods(1);
        order.setOrderStart(1782748800000L);
        order.setOrderEnd(1793059200000L);
        return order;
    }
}
