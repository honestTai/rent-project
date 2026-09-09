package com.fly.rent.web.goods;

import com.alipay.api.domain.ItemSceneRiskInfo;
import com.alipay.api.domain.Reasons;
import com.alipay.api.response.AlipayOpenAppItemQueryResponse;
import com.fly.rent.entity.AlipayGoodsSyncLog;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.mapper.AlipayGoodsSyncLogMapper;
import com.fly.rent.mapper.GoodMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlipayGoodsSyncServiceTest {

    @Test
    void optimizeImageForUploadCompressesOversizedDetailImageBeforeAlipayUpload() throws Exception {
        AlipayGoodsSyncService service = new AlipayGoodsSyncService(null, null, null, null, null);
        try {
            byte[] content = buildOversizedPng();
            assertTrue(content.length > 5 * 1024 * 1024);
            Object image = newDownloadedImage(content, "image/png", "png", "detail.png");

            Object optimized = invokeOptimizeImageForUpload(service, image, "ITEM_DESCINFO_IMG");

            assertEquals("image/jpeg", invokeStringGetter(optimized, "getMimeType"));
            assertEquals("jpg", invokeStringGetter(optimized, "getFileType"));
            assertTrue(invokeContent(optimized).length <= 5 * 1024 * 1024);
        } finally {
            service.destroy();
        }
    }

    @Test
    void loadImageUploadCacheReadsPreviousSuccessfulSyncMapping() throws Exception {
        AlipayGoodsSyncLogMapper syncLogMapper = mock(AlipayGoodsSyncLogMapper.class);
        AlipayGoodsSyncLog syncLog = new AlipayGoodsSyncLog();
        syncLog.setDetailJson("{\"imageUploadCache\":{\"ITEM_DESCINFO_IMG|https://example.com/detail.png\":\"A*cached\"}}");
        when(syncLogMapper.selectOne(any())).thenReturn(syncLog);
        AlipayGoodsSyncService service = new AlipayGoodsSyncService(null, null, syncLogMapper, null, null);
        try {
            Map<String, String> cache = invokeLoadImageUploadCache(service, 1);

            assertEquals("A*cached", cache.get("ITEM_DESCINFO_IMG|https://example.com/detail.png"));
        } finally {
            service.destroy();
        }
    }

    @Test
    void selectStatusDetailPrefersAuditingEditDetailOverOnlineAvailableDetail() throws Exception {
        AlipayGoodsSyncService service = new AlipayGoodsSyncService(null, null, null, null, null);
        try {
            AlipayOpenAppItemQueryResponse editDetail = new AlipayOpenAppItemQueryResponse();
            editDetail.setSpuStatus("AUDITING");
            AlipayOpenAppItemQueryResponse onlineDetail = new AlipayOpenAppItemQueryResponse();
            onlineDetail.setSpuStatus("AVAILABLE");

            AlipayOpenAppItemQueryResponse selectedDetail = invokeSelectStatusDetail(service, editDetail, onlineDetail);

            assertEquals("AUDITING", selectedDetail.getSpuStatus());
        } finally {
            service.destroy();
        }
    }

    @Test
    void extractStatusReasonFromDetailReadsSceneRiskInfo() throws Exception {
        AlipayGoodsSyncService service = new AlipayGoodsSyncService(null, null, null, null, null);
        try {
            Reasons reason = new Reasons();
            reason.setRiskName("商品主图");
            reason.setRemark("商品图片有黑边/白边，请修改");
            ItemSceneRiskInfo sceneRiskInfo = new ItemSceneRiskInfo();
            sceneRiskInfo.setScene("商品主图");
            sceneRiskInfo.setRiskInfos(Collections.singletonList(reason));
            AlipayOpenAppItemQueryResponse detail = new AlipayOpenAppItemQueryResponse();
            detail.setSceneRiskInfo(Collections.singletonList(sceneRiskInfo));

            String statusReason = invokeExtractStatusReasonFromDetail(service, detail);

            assertEquals("商品主图：商品图片有黑边/白边，请修改", statusReason);
        } finally {
            service.destroy();
        }
    }

    @Test
    void patchLocalGoodRemoteStateDoesNotOverwriteExistingLocalCategory() throws Exception {
        GoodMapper goodMapper = mock(GoodMapper.class);
        AlipayGoodsSyncService service = new AlipayGoodsSyncService(goodMapper, null, null, null, null);
        try {
            Good good = new Good();
            good.setGoodId(7);
            good.setAlipayCategoryId("C001686066");

            AlipayOpenAppItemQueryResponse detail = new AlipayOpenAppItemQueryResponse();
            detail.setItemId("2026062322000535489649");
            detail.setCategoryId("C_OLD_REMOTE");
            detail.setSpuStatus("AVAILABLE");

            boolean changed = invokePatchLocalGoodRemoteState(service, good, detail);

            assertEquals("C001686066", good.getAlipayCategoryId());
            assertTrue(changed);
        } finally {
            service.destroy();
        }
    }

    @Test
    void hasLocalCategoryChangedFromRemoteDetectsExplicitLocalCategoryChange() throws Exception {
        AlipayGoodsSyncService service = new AlipayGoodsSyncService(null, null, null, null, null);
        try {
            Good good = new Good();
            good.setAlipayCategoryId("C001686066");
            AlipayOpenAppItemQueryResponse remote = new AlipayOpenAppItemQueryResponse();
            remote.setCategoryId("C_OLD_REMOTE");

            assertTrue(invokeHasLocalCategoryChangedFromRemote(service, good, remote));
        } finally {
            service.destroy();
        }
    }

    @Test
    void resolveSalePriceRejectsZeroBuyoutPriceEvenWhenBuyoutDisabled() throws Exception {
        AlipayGoodsSyncService service = new AlipayGoodsSyncService(null, null, null, null, null);
        try {
            Attr attr = new Attr();
            attr.setAttrId(4);
            attr.setAttrTitle("笔记本");
            attr.setAttrAmount(1000);
            attr.setAttrDeposit(10000);
            attr.setBuyout(0);
            attr.setBuyoutval(0);

            InvocationTargetException exception = assertThrows(
                    InvocationTargetException.class,
                    () -> invokeResolveSalePriceLong(service, attr)
            );
            assertTrue(exception.getCause() instanceof IllegalStateException);
            assertTrue(exception.getCause().getMessage().contains("买断金必须大于0"));
        } finally {
            service.destroy();
        }
    }

    private byte[] buildOversizedPng() throws Exception {
        BufferedImage image = new BufferedImage(2400, 2400, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private Object newDownloadedImage(byte[] content, String mimeType, String fileType, String fileName) throws Exception {
        Class<?> imageClass = Class.forName("com.fly.rent.web.goods.AlipayGoodsSyncService$DownloadedImage");
        Constructor<?> constructor = imageClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object image = constructor.newInstance();
        invokeSetter(image, "setContent", byte[].class, content);
        invokeSetter(image, "setMimeType", String.class, mimeType);
        invokeSetter(image, "setFileType", String.class, fileType);
        invokeSetter(image, "setFileName", String.class, fileName);
        return image;
    }

    private Object invokeOptimizeImageForUpload(
            AlipayGoodsSyncService service,
            Object image,
            String uploadScene
    ) throws Exception {
        Method method = AlipayGoodsSyncService.class.getDeclaredMethod(
                "optimizeImageForUpload",
                image.getClass(),
                String.class,
                String.class);
        method.setAccessible(true);
        return method.invoke(service, image, uploadScene, "https://example.com/detail.png");
    }

    private void invokeSetter(Object target, String methodName, Class<?> argType, Object value) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, argType);
        method.setAccessible(true);
        method.invoke(target, value);
    }

    private String invokeStringGetter(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (String) method.invoke(target);
    }

    private byte[] invokeContent(Object target) throws Exception {
        Method method = target.getClass().getDeclaredMethod("getContent");
        method.setAccessible(true);
        return (byte[]) method.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> invokeLoadImageUploadCache(AlipayGoodsSyncService service, Integer goodId) throws Exception {
        Method method = AlipayGoodsSyncService.class.getDeclaredMethod("loadImageUploadCache", Integer.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(service, goodId);
    }

    private AlipayOpenAppItemQueryResponse invokeSelectStatusDetail(
            AlipayGoodsSyncService service,
            AlipayOpenAppItemQueryResponse editDetail,
            AlipayOpenAppItemQueryResponse onlineDetail
    ) throws Exception {
        Method method = AlipayGoodsSyncService.class.getDeclaredMethod(
                "selectStatusDetail",
                AlipayOpenAppItemQueryResponse.class,
                AlipayOpenAppItemQueryResponse.class);
        method.setAccessible(true);
        return (AlipayOpenAppItemQueryResponse) method.invoke(service, editDetail, onlineDetail);
    }

    private String invokeExtractStatusReasonFromDetail(
            AlipayGoodsSyncService service,
            AlipayOpenAppItemQueryResponse detail
    ) throws Exception {
        Method method = AlipayGoodsSyncService.class.getDeclaredMethod(
                "extractStatusReasonFromDetail",
                AlipayOpenAppItemQueryResponse.class);
        method.setAccessible(true);
        return (String) method.invoke(service, detail);
    }

    private boolean invokePatchLocalGoodRemoteState(
            AlipayGoodsSyncService service,
            Good good,
            AlipayOpenAppItemQueryResponse detail
    ) throws Exception {
        Method method = AlipayGoodsSyncService.class.getDeclaredMethod(
                "patchLocalGoodRemoteState",
                Good.class,
                AlipayOpenAppItemQueryResponse.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(service, good, detail);
    }

    private boolean invokeHasLocalCategoryChangedFromRemote(
            AlipayGoodsSyncService service,
            Good good,
            AlipayOpenAppItemQueryResponse remote
    ) throws Exception {
        Method method = AlipayGoodsSyncService.class.getDeclaredMethod(
                "hasLocalCategoryChangedFromRemote",
                Good.class,
                AlipayOpenAppItemQueryResponse.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(service, good, remote);
    }

    private Long invokeResolveSalePriceLong(AlipayGoodsSyncService service, Attr attr) throws Exception {
        Method method = AlipayGoodsSyncService.class.getDeclaredMethod("resolveSalePriceLong", Attr.class);
        method.setAccessible(true);
        return (Long) method.invoke(service, attr);
    }
}
