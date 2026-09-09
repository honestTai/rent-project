package com.fly.rent.miniapp.order;

import com.alipay.api.request.AlipayOpenFileUploadRequest;
import com.alipay.api.response.AlipayOpenFileUploadResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.notify.AlipayOrderLifecycleNotifyService;
import com.fly.rent.web.support.WebOperLogHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentContractAlipaySyncServiceTest {

    private static final byte[] SMALL_PDF = "%PDF-1.4\ncontract".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RentContractService rentContractService;
    @Mock
    private com.fly.rent.legacy.service.AlipayClientService alipayClientService;
    @Mock
    private AlipayOrderLifecycleNotifyService lifecycleNotifyService;
    @Mock
    private WebOperLogHelper operLogHelper;

    @Test
    void syncSkipsAlreadySuccessfulContract() throws Exception {
        Order order = receivedOrder();
        order.setContractAlipaySyncStatus(RentContractAlipaySyncService.STATUS_SUCCESS);
        order.setContractAlipayFileId("FILE-OLD");
        when(orderMapper.selectById(7)).thenReturn(order);

        service().syncContractAfterReceive(7, "履约收货");

        verify(rentContractService, never()).generateSignedContractNow(any(), any());
        verify(rentContractService, never()).renderSignedContractPdfBytes(any());
        verify(alipayClientService, never()).execute(any());
        verify(orderMapper, never()).updateById(any());
        verify(operLogHelper).logSuccess(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncSkipsOrderBeforeMerchantDeliveryReceived() throws Exception {
        Order order = receivedOrder();
        order.setAlipayStatus("SIGNED");
        when(orderMapper.selectById(7)).thenReturn(order);

        service().syncContractAfterReceive(7, "后台补生成协议PDF");

        verify(rentContractService, never()).renderSignedContractPdfBytes(any());
        verify(alipayClientService, never()).execute(any());
        verify(orderMapper, never()).updateById(any());
        verify(operLogHelper).logSuccess(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncUploadsExistingContractPdfAndBackfillsFileId() throws Exception {
        Order order = receivedOrder();
        when(orderMapper.selectById(7)).thenReturn(order);
        when(rentContractService.renderSignedContractPdfBytes(7)).thenReturn(SMALL_PDF);
        when(alipayClientService.execute(any(AlipayOpenFileUploadRequest.class))).thenReturn(uploadResponse("FILE-7"));
        when(alipayClientService.execute(any(AlipayCommerceRentAdditionalUploadRequest.class))).thenReturn(additionalSuccess());

        service().syncContractAfterReceive(7, "履约收货");

        verify(rentContractService, never()).generateSignedContractNow(any(), any());
        ArgumentCaptor<com.alipay.api.AlipayRequest> requestCaptor =
                ArgumentCaptor.forClass(com.alipay.api.AlipayRequest.class);
        verify(alipayClientService, times(2)).execute(requestCaptor.capture());
        AlipayOpenFileUploadRequest uploadRequest = requestCaptor.getAllValues().stream()
                .filter(AlipayOpenFileUploadRequest.class::isInstance)
                .map(AlipayOpenFileUploadRequest.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing file upload request"));
        assertEquals("rent_material_upload", uploadRequest.getBizCode());
        assertEquals("rent-contract-7.pdf", uploadRequest.getFileContent().getFileName());

        AlipayCommerceRentAdditionalUploadRequest additionalRequest = requestCaptor.getAllValues().stream()
                .filter(AlipayCommerceRentAdditionalUploadRequest.class::isInstance)
                .map(AlipayCommerceRentAdditionalUploadRequest.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing additional upload request"));
        JsonNode bizContent = new ObjectMapper().readTree(additionalRequest.getBizContent());
        assertEquals("RENT-7", bizContent.get("order_id").asText());
        assertEquals("RENT_CONTRACT", bizContent.get("fulfillment_additional_media_list").get(0).get("type").asText());
        assertEquals("FILE-7", bizContent.get("fulfillment_additional_media_list").get(0).get("value").asText());

        assertEquals("FILE-7", order.getContractAlipayFileId());
        assertEquals(RentContractAlipaySyncService.STATUS_SUCCESS, order.getContractAlipaySyncStatus());
        verify(orderMapper).updateById(order);
        verify(operLogHelper).logSuccess(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncGeneratesContractPdfWhenMissingBeforeUpload() throws Exception {
        Order missingPdf = receivedOrder();
        missingPdf.setContractPdfUrl(null);
        Order generatedPdf = receivedOrder();
        when(orderMapper.selectById(7)).thenReturn(missingPdf, generatedPdf);
        when(rentContractService.renderSignedContractPdfBytes(7)).thenReturn(SMALL_PDF);
        when(alipayClientService.execute(any(AlipayOpenFileUploadRequest.class))).thenReturn(uploadResponse("FILE-7"));
        when(alipayClientService.execute(any(AlipayCommerceRentAdditionalUploadRequest.class))).thenReturn(additionalSuccess());

        service().syncContractAfterReceive(7, "履约收货");

        verify(rentContractService).generateSignedContractNow(7, "履约收货-合同回传补生成");
        assertEquals(RentContractAlipaySyncService.STATUS_SUCCESS, generatedPdf.getContractAlipaySyncStatus());
        verify(operLogHelper).logSuccess(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncRecordsFailureWhenUploadReturnsNoFileId() throws Exception {
        Order order = receivedOrder();
        when(orderMapper.selectById(7)).thenReturn(order);
        when(rentContractService.renderSignedContractPdfBytes(7)).thenReturn(SMALL_PDF);
        when(alipayClientService.execute(any(AlipayOpenFileUploadRequest.class))).thenReturn(uploadResponse(null));

        service().syncContractAfterReceive(7, "履约收货");

        verify(alipayClientService, never()).execute(any(AlipayCommerceRentAdditionalUploadRequest.class));
        assertEquals(RentContractAlipaySyncService.STATUS_FAILED, order.getContractAlipaySyncStatus());
        assertTrue(order.getContractAlipaySyncError().contains("file_id"));
        verify(orderMapper).updateById(order);
        verify(lifecycleNotifyService).notifyException(any(), any(), any(), any(), any());
        verify(operLogHelper).logFailure(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncRecordsFailureWhenPdfExceedsFiveMb() throws Exception {
        Order order = receivedOrder();
        when(orderMapper.selectById(7)).thenReturn(order);
        when(rentContractService.renderSignedContractPdfBytes(7)).thenReturn(new byte[5 * 1024 * 1024 + 1]);

        service().syncContractAfterReceive(7, "履约收货");

        verify(alipayClientService, never()).execute(any());
        assertEquals(RentContractAlipaySyncService.STATUS_FAILED, order.getContractAlipaySyncStatus());
        assertTrue(order.getContractAlipaySyncError().contains("5MB"));
        verify(orderMapper).updateById(order);
        verify(operLogHelper).logFailure(any(), any(), any(), any(), any(), any(), any());
    }

    private RentContractAlipaySyncService service() {
        return new RentContractAlipaySyncService(
                orderMapper,
                rentContractService,
                alipayClientService,
                lifecycleNotifyService,
                new ObjectMapper(),
                operLogHelper
        );
    }

    private Order receivedOrder() {
        Order order = new Order();
        order.setOrderId(7);
        order.setOrderNo("ORD-7");
        order.setRentOrderId("RENT-7");
        order.setAlipayStatus("RECEIVED");
        order.setContractPdfUrl("https://example.test/uploads/contracts/rent-contract-7.pdf");
        order.setContractPdfPath("contracts/rent-contract-7.pdf");
        return order;
    }

    private AlipayOpenFileUploadResponse uploadResponse(String fileId) {
        AlipayOpenFileUploadResponse response = new AlipayOpenFileUploadResponse();
        response.setCode("10000");
        response.setMsg("Success");
        response.setFileId(fileId);
        return response;
    }

    private AlipayCommerceRentAdditionalUploadResponse additionalSuccess() {
        AlipayCommerceRentAdditionalUploadResponse response = new AlipayCommerceRentAdditionalUploadResponse();
        response.setCode("10000");
        response.setMsg("Success");
        return response;
    }
}
