package com.fly.rent.miniapp.order;

import com.fly.rent.common.dto.RentViews;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RentContractServicePdfTest {

    @Test
    void rendersBrandedDemoTemplateWithMultiplePages() throws Exception {
        RentContractService service = new RentContractService(null, null, null, null, null, null);
        RentViews.RentalContractView contract = demoContract();

        byte[] pdf = service.renderContractPdf(contract);

        assertTrue(pdf.length > 3_000);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
        Path output = Paths.get("target", "demo-contract-template.pdf");
        Files.createDirectories(output.getParent());
        Files.write(output, pdf);
    }

    private RentViews.RentalContractView demoContract() {
        RentViews.RentalContractView view = new RentViews.RentalContractView();
        view.setContractNo("DEMO-CONTRACT-20260712-001");
        view.setContractTitle("电子设备租赁协议");
        view.setContractSubtitle("演示协议");
        view.setSignedAt("2026-07-12 11:30:00");
        view.setMerchantName("HONESTTAI 演示环境");
        view.setMerchantCreditCode("仅供演示");
        view.setMerchantLegalRepresentative("演示管理员");
        view.setBuyerName("演示用户");
        view.setBuyerPhone("13800000000");
        view.setBuyerAddress("演示城市 创新大道 88 号（虚拟地址）");
        view.setOrderNo("DEMO-20260712-009");
        view.setGoodTitle("HONESTTAI Demo · 全画幅微单相机");
        view.setSkuTitle("机身 + 24-70mm 镜头");
        view.setQuantity(1);
        view.setOrderKeep(30);
        view.setOrderRentUnit("天");
        view.setRentStartAt("2026-07-09 11:30:00");
        view.setRentEndAt("2026-08-08 11:30:00");
        view.setDepositText("¥12,000.00");
        view.setRentText("¥477.00");
        view.setTotalText("¥477.00");
        RentViews.ContractSectionView section = new RentViews.ContractSectionView();
        section.setTitle("一、租赁内容");
        section.setItems(Arrays.asList(
                "出租方按照演示订单向承租方提供电子设备及配件，设备品牌、型号、成色、数量、租期、日租金、押金和配送方式以订单详情为准。",
                "本协议仅供软件演示，所有姓名、电话、地址、订单号和金额均为虚拟数据。",
                "承租方应按照设备说明合理使用，不得擅自拆机、改装、转租或用于违法活动。",
                "租期届满后，承租方可选择快递归还、线下归还或按订单规则申请续租。",
                "系统可展示押金扣减、退款审批、分期账单、归还验收和订单完结等业务流程。"));
        RentViews.ContractSectionView section2 = new RentViews.ContractSectionView();
        section2.setTitle("二、演示声明与留档");
        section2.setItems(Arrays.asList(
                "该 Demo 文件不接入真实电子签名，不具备真实签名、时间戳、存证或司法证明效力。",
                "演示数据不得使用真实身份证号、真实手机号或真实收货地址。",
                "正式客户应由其法务人员结合业务主体、地区和平台规则审核合同文本。"));
        view.setSections(Arrays.asList(section, section2, section, section2, section, section2));
        return view;
    }
}
