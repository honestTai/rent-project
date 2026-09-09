package com.fly.rent.miniapp.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.common.oss.OssFileStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.model.RentUserProfileExtra;
import com.fly.rent.common.support.RentExtensionStore;
import com.fly.rent.common.support.RentTimeSupport;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.UserMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 租赁协议服务：组装签约快照、生成 PDF、上传 OSS，并把附件结果回写订单表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentContractService {

    private static final String CONTRACT_VERSION = "2026-05";

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final RentExtensionStore rentExtensionStore;
    private final AlipayPlatformConfigService alipayPlatformConfigService;
    private final OssFileStorage ossFileStorage;
    private final ObjectMapper objectMapper;

    /**
     * 签约完成后异步生成协议 PDF。方法内部按订单字段做幂等，重复触发不会反复生成。
     *
     * @param orderId 本地订单主键
     * @param source 触发来源
     */
    @Async("contractExecutor")
    public void generateSignedContractAsync(Integer orderId, String source) {
        if (orderId == null) {
            return;
        }
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                return;
            }
            if (StringUtils.hasText(order.getContractPdfUrl())) {
                return;
            }
            generateAndSave(order, source);
        } catch (Exception e) {
            log.error("异步生成租赁协议PDF失败: orderId={}, source={}", orderId, source, e);
        }
    }

    /**
     * 同步生成或返回已有协议 PDF，用于后台人工补生成。
     *
     * @param orderId 本地订单主键
     * @param source 触发来源
     * @return 最新协议视图
     * @throws Exception PDF 生成或上传失败
     */
    public RentViews.RentalContractView generateSignedContractNow(Integer orderId, String source) throws Exception {
        return generateSignedContractNow(orderId, source, false);
    }

    /**
     * 同步生成协议 PDF。force=true 时会覆盖已有 PDF 和快照，用于后台修正协议内容后重新生成。
     *
     * @param orderId 本地订单主键
     * @param source 触发来源
     * @param force 是否强制重生成
     * @return 最新协议视图
     * @throws Exception PDF 生成或上传失败
     */
    public RentViews.RentalContractView generateSignedContractNow(Integer orderId, String source, boolean force) throws Exception {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId不能为空");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (force || !StringUtils.hasText(order.getContractPdfUrl())) {
            generateAndSave(order, source);
            order = orderMapper.selectById(orderId);
        }
        return buildContractView(order);
    }

    /**
     * 渲染当前订单的租赁协议 PDF 字节流，供 e签宝文件直传使用。
     * 该方法不会把密钥或第三方状态写入合同快照，只复用当前合同视图。
     */
    public byte[] renderSignedContractPdfNow(Integer orderId, String source) throws Exception {
        RentViews.RentalContractView contract = generateSignedContractNow(orderId, source);
        return renderContractPdf(contract);
    }

    /**
     * 将协议视图渲染为 PDF 字节流。
     */
    public byte[] renderContractPdf(RentViews.RentalContractView contract) throws Exception {
        return renderPdf(contract);
    }

    /**
     * 重新渲染已签署合同 PDF 字节，用于回传支付宝文件上传接口。
     *
     * @param orderId 本地订单主键
     * @return PDF 字节
     * @throws Exception 订单不存在或 PDF 渲染失败
     */
    public byte[] renderSignedContractPdfBytes(Integer orderId) throws Exception {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId不能为空");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return renderPdf(buildContractView(order));
    }

    /**
     * 构建小程序协议预览数据。
     *
     * @param order 订单
     * @return 协议视图
     */
    public RentViews.RentalContractView buildContractView(Order order) {
        RentViews.RentalContractView snapshot = loadSnapshot(order);
        User user = loadUser(order.getUserUuid());
        RentUserProfileExtra extra = rentExtensionStore.loadUserProfileExtra(order.getUserUuid());
        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        if (snapshot != null) {
            completeContractView(snapshot, order, user, extra, extension);
            return snapshot;
        }

        RentViews.RentalContractView view = new RentViews.RentalContractView();
        completeContractView(view, order, user, extra, extension);
        return view;
    }

    private RentViews.RentalContractView buildFreshContractView(Order order) {
        User user = loadUser(order.getUserUuid());
        RentUserProfileExtra extra = rentExtensionStore.loadUserProfileExtra(order.getUserUuid());
        RentOrderExtension extension = rentExtensionStore.loadOrderExtension(order.getOrderNo());
        RentViews.RentalContractView view = new RentViews.RentalContractView();
        completeContractView(view, order, user, extra, extension);
        return view;
    }

    private void completeContractView(RentViews.RentalContractView view,
                                      Order order,
                                      User user,
                                      RentUserProfileExtra extra,
                                      RentOrderExtension extension) {
        view.setContractNo(resolveContractNo(order));
        view.setContractVersion(StringUtils.hasText(order.getContractVersion()) ? order.getContractVersion() : CONTRACT_VERSION);
        view.setContractTitle(alipayPlatformConfigService.contractTitle());
        view.setContractSubtitle(alipayPlatformConfigService.contractSubtitle());
        view.setOrderId(String.valueOf(order.getOrderId()));
        view.setOrderNo(order.getOrderNo());
        view.setRentOrderId(order.getRentOrderId());
        view.setSignedAt(RentTimeSupport.formatDateTime(resolveAgreedAt(order)));
        view.setMerchantName(alipayPlatformConfigService.contractMerchantName());
        view.setMerchantCreditCode(alipayPlatformConfigService.contractMerchantCreditCode());
        view.setMerchantLegalRepresentative(alipayPlatformConfigService.contractMerchantLegalRepresentative());
        view.setMerchantRegisteredAddress(alipayPlatformConfigService.contractMerchantRegisteredAddress());
        view.setMerchantAddress(alipayPlatformConfigService.contractMerchantAddress());
        view.setBuyerName(resolveBuyerName(user, extra, order));
        view.setBuyerIdCard(resolveBuyerIdCard(user, extra, order));
        view.setBuyerPhone(resolveBuyerPhone(user, order));
        view.setBuyerAddress(resolveBuyerAddress(order, extension));
        view.setGoodTitle(order.getGoodTitle());
        view.setSkuTitle(order.getAttrTitle());
        view.setSourceId(order.getSourceId());
        view.setAuthNo(order.getOrderAuthNo());
        view.setQuantity(order.getAttrNum());
        view.setOrderKeep(order.getOrderKeep());
        view.setOrderRentUnit(extension != null && StringUtils.hasText(extension.getRentUnit())
                ? RentTimeSupport.normalizeRentUnit(extension.getRentUnit())
                : "天");
        view.setRentStartAt(RentTimeSupport.formatDateTime(order.getOrderStart()));
        view.setRentEndAt(RentTimeSupport.formatDateTime(order.getOrderEnd()));
        view.setDepositText(formatCent(order.getOrderDeposit()));
        view.setRentText(formatCent(order.getOrderFirstAmount() != null && order.getOrderFirstAmount() > 0
                ? order.getOrderFirstAmount()
                : order.getOrderTotal()));
        view.setTotalText(formatCent(order.getOrderTotal()));
        view.setContractPdfUrl(order.getContractPdfUrl());
        view.setContractPdfPath(order.getContractPdfPath());
        if (view.getSections() == null || view.getSections().isEmpty()) {
            view.setSections(resolveSections(view));
        } else {
            view.setSections(applyTemplateVariables(view.getSections(), view));
        }
    }

    private void generateAndSave(Order order, String source) throws Exception {
        Long agreedAt = resolveAgreedAt(order);
        order.setContractAgreedAt(agreedAt);
        RentViews.RentalContractView contract = buildFreshContractView(order);
        byte[] pdfBytes = renderPdf(contract);
        String filename = "rent-contract-" + order.getOrderId() + ".pdf";
        String url = ossFileStorage.upload(new ByteArrayInputStream(pdfBytes), filename, "contracts/");
        String relativePath = extractOssRelativePath(url);

        order.setContractNo(contract.getContractNo());
        order.setContractVersion(CONTRACT_VERSION);
        order.setContractAgreedAt(agreedAt);
        order.setContractPdfUrl(url);
        order.setContractPdfPath(relativePath);
        order.setContractSnapshotJson(toJson(contract));
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);
        log.info("租赁协议PDF已生成: orderId={}, contractNo={}, source={}, url={}",
                order.getOrderId(), contract.getContractNo(), source, url);
    }

    private byte[] renderPdf(RentViews.RentalContractView contract) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 68, 68, 66, 58);
        BaseFont baseFont = createChineseBaseFont();
        boolean demoContract = isDemoContract(contract);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        writer.setPageEvent(new ContractPageEvent(baseFont, demoContract));
        document.open();

        Color brandBlue = new Color(49, 87, 245);
        Color muted = new Color(103, 116, 143);
        Font titleFont = new Font(baseFont, 22, Font.BOLD, new Color(24, 29, 38));
        Font subtitleFont = new Font(baseFont, 10, Font.NORMAL, muted);
        Font sectionFont = new Font(baseFont, 13, Font.BOLD, brandBlue);
        Font bodyFont = new Font(baseFont, 10, Font.NORMAL, new Color(35, 39, 47));
        Font labelFont = new Font(baseFont, 9, Font.NORMAL, muted);

        String contractTitle = safe(contract.getContractTitle());
        Paragraph title = new Paragraph(contractTitle.startsWith("HONESTTAI")
                ? contractTitle : "HONESTTAI " + contractTitle, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        document.add(title);
        if (demoContract) {
            Paragraph demoNotice = new Paragraph("DEMO 版本 V1.0 · 仅用于后台功能、合同预览和业务流程演示", subtitleFont);
            demoNotice.setAlignment(Element.ALIGN_CENTER);
            demoNotice.setSpacingAfter(16);
            document.add(demoNotice);
        } else if (StringUtils.hasText(contract.getContractSubtitle())) {
            Paragraph subtitle = new Paragraph(contract.getContractSubtitle(), subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(16);
            document.add(subtitle);
        }

        PdfPTable parties = new PdfPTable(new float[]{1.25f, 4.15f});
        parties.setWidthPercentage(100);
        parties.setSpacingAfter(14);
        addSummaryRow(parties, "协议编号", safe(contract.getContractNo()), labelFont, bodyFont);
        addSummaryRow(parties, "出租方", safe(contract.getMerchantName()), labelFont, bodyFont);
        addSummaryRow(parties, "承租方", safe(contract.getBuyerName()), labelFont, bodyFont);
        addSummaryRow(parties, "租赁物", safe(contract.getGoodTitle()) + " · " + safe(contract.getSkuTitle()), labelFont, bodyFont);
        addSummaryRow(parties, "签署时间", safe(contract.getSignedAt()), labelFont, bodyFont);
        document.add(parties);

        Paragraph orderHeading = new Paragraph("订单与租赁信息", sectionFont);
        orderHeading.setSpacingAfter(7);
        document.add(orderHeading);
        PdfPTable orderTable = new PdfPTable(new float[]{1.15f, 2.15f, 1.15f, 2.15f});
        orderTable.setWidthPercentage(100);
        orderTable.setSpacingAfter(6);
        addOrderPair(orderTable, "订单号", safe(contract.getOrderNo()), "数量", safe(contract.getQuantity()), labelFont, bodyFont);
        addOrderPair(orderTable, "租赁期限", safe(contract.getOrderKeep()) + safe(contract.getOrderRentUnit()), "起止时间",
                safe(contract.getRentStartAt()) + " 至 " + safe(contract.getRentEndAt()), labelFont, bodyFont);
        addOrderPair(orderTable, "押金", safe(contract.getDepositText()), "租金/总额",
                safe(contract.getRentText()) + " / " + safe(contract.getTotalText()), labelFont, bodyFont);
        document.add(orderTable);

        addLine(document, bodyFont, "承租方联系电话：" + safe(contract.getBuyerPhone()) + "    收货地址：" + safe(contract.getBuyerAddress()));
        if (!demoContract) {
            addLine(document, bodyFont, "统一社会信用代码：" + safe(contract.getMerchantCreditCode())
                    + "    法定代表人：" + safe(contract.getMerchantLegalRepresentative()));
        }

        for (RentViews.ContractSectionView section : contract.getSections()) {
            Paragraph sectionTitle = new Paragraph(section.getTitle(), sectionFont);
            sectionTitle.setSpacingBefore(12);
            sectionTitle.setSpacingAfter(6);
            document.add(sectionTitle);
            for (String item : section.getItems()) {
                Paragraph p = new Paragraph(item, bodyFont);
                p.setFirstLineIndent(18);
                p.setLeading(16);
                document.add(p);
            }
        }

        Paragraph footer = new Paragraph("承租方通过支付宝租赁组件完成签约，即表示已阅读、理解并同意本协议全部内容。", bodyFont);
        footer.setSpacingBefore(14);
        document.add(footer);
        document.close();
        return output.toByteArray();
    }

    private boolean isDemoContract(RentViews.RentalContractView contract) {
        return startsWithIgnoreCase(contract.getContractNo(), "DEMO-")
                || startsWithIgnoreCase(contract.getOrderNo(), "DEMO-")
                || (StringUtils.hasText(contract.getMerchantName()) && contract.getMerchantName().contains("演示"));
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font bodyFont) {
        table.addCell(styledCell(label, labelFont, new Color(238, 243, 255), 8));
        table.addCell(styledCell(value, bodyFont, new Color(255, 254, 250), 8));
    }

    private void addOrderPair(PdfPTable table, String label1, String value1, String label2, String value2,
                              Font labelFont, Font bodyFont) {
        table.addCell(styledCell(label1, labelFont, new Color(238, 243, 255), 7));
        table.addCell(styledCell(value1, bodyFont, Color.WHITE, 7));
        table.addCell(styledCell(label2, labelFont, new Color(238, 243, 255), 7));
        table.addCell(styledCell(value2, bodyFont, Color.WHITE, 7));
    }

    private PdfPCell styledCell(String value, Font font, Color background, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(value), font));
        cell.setBackgroundColor(background);
        cell.setBorderColor(new Color(211, 220, 239));
        cell.setPadding(padding);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static class ContractPageEvent extends PdfPageEventHelper {
        private final BaseFont baseFont;
        private final boolean demo;

        private ContractPageEvent(BaseFont baseFont, boolean demo) {
            this.baseFont = baseFont;
            this.demo = demo;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            PdfContentByte canvas = writer.getDirectContent();
            canvas.saveState();
            canvas.setColorFill(new Color(49, 87, 245));
            canvas.rectangle(0, page.getTop() - 20, page.getWidth(), 20);
            canvas.fill();
            canvas.restoreState();

            if (demo) {
                PdfContentByte under = writer.getDirectContentUnder();
                under.saveState();
                under.setColorFill(new Color(232, 237, 252));
                ColumnText.showTextAligned(under, Element.ALIGN_CENTER,
                        new Phrase("DEMO 演示", new Font(baseFont, 54, Font.BOLD, new Color(232, 237, 252))),
                        page.getWidth() / 2, page.getHeight() / 2, 35);
                under.restoreState();
            }

            Font footerFont = new Font(baseFont, 8, Font.NORMAL, new Color(103, 116, 143));
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                    new Phrase("HONESTTAI · 电子设备租赁协议", footerFont),
                    document.left(), 26, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase("第 " + writer.getPageNumber() + " 页", footerFont),
                    document.right(), 26, 0);
        }
    }

    private void addLine(Document document, Font font, String value) throws Exception {
        Paragraph p = new Paragraph(value, font);
        p.setLeading(16);
        document.add(p);
    }

    private BaseFont createChineseBaseFont() throws Exception {
        try (InputStream bundledFont = RentContractService.class.getResourceAsStream(
                "/fonts/wqy-microhei.ttc")) {
            if (bundledFont != null) {
                return BaseFont.createFont("wqy-microhei.ttc,0", BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED, true, StreamUtils.copyToByteArray(bundledFont), null);
            }
        }
        List<String> candidates = Arrays.asList(
                "C:/Windows/Fonts/simsun.ttc,0",
                "C:/Windows/Fonts/msyh.ttc,0",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/System/Library/Fonts/STHeiti Medium.ttc,0",
                "/System/Library/Fonts/Supplemental/Songti.ttc,0",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/truetype/arphic/uming.ttc,0",
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc,0"
        );
        for (String candidate : candidates) {
            String filePath = candidate.contains(",") ? candidate.substring(0, candidate.indexOf(',')) : candidate;
            if (new File(filePath).exists()) {
                return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
        try {
            return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            log.warn("未找到可用中文字体，租赁协议PDF可能无法正确显示中文: {}", e.getMessage());
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        }
    }

    private List<RentViews.ContractSectionView> resolveSections(RentViews.RentalContractView contract) {
        String sectionsJson = alipayPlatformConfigService.contractSectionsJson();
        try {
            List<RentViews.ContractSectionView> sections = objectMapper.readValue(
                    sectionsJson,
                    new TypeReference<List<RentViews.ContractSectionView>>() {
                    });
            if (sections != null && !sections.isEmpty()) {
                return applyTemplateVariables(sections, contract);
            }
        } catch (Exception e) {
            throw new IllegalStateException("中台租赁协议条款JSON配置不合法: rent.contract.sections.json", e);
        }
        throw new IllegalStateException("中台租赁协议条款JSON配置为空: rent.contract.sections.json");
    }

    private List<RentViews.ContractSectionView> applyTemplateVariables(List<RentViews.ContractSectionView> sections,
                                                                       RentViews.RentalContractView contract) {
        List<RentViews.ContractSectionView> result = new ArrayList<RentViews.ContractSectionView>();
        for (RentViews.ContractSectionView source : sections) {
            RentViews.ContractSectionView target = new RentViews.ContractSectionView();
            target.setTitle(replaceVariables(source.getTitle(), contract));
            List<String> items = new ArrayList<String>();
            if (source.getItems() != null) {
                for (String item : source.getItems()) {
                    items.add(replaceVariables(item, contract));
                }
            }
            target.setItems(items);
            result.add(target);
        }
        return result;
    }

    private String replaceVariables(String value, RentViews.RentalContractView contract) {
        if (value == null) {
            return null;
        }
        return value
                .replace("${merchantName}", safe(contract.getMerchantName()))
                .replace("${merchantCreditCode}", safe(contract.getMerchantCreditCode()))
                .replace("${merchantLegalRepresentative}", safe(contract.getMerchantLegalRepresentative()))
                .replace("${merchantRegisteredAddress}", safe(contract.getMerchantRegisteredAddress()))
                .replace("${merchantAddress}", safe(contract.getMerchantAddress()))
                .replace("${buyerName}", safe(contract.getBuyerName()))
                .replace("${buyerIdCard}", safe(contract.getBuyerIdCard()))
                .replace("${buyerPhone}", safe(contract.getBuyerPhone()))
                .replace("${buyerAddress}", safe(contract.getBuyerAddress()))
                .replace("${orderNo}", safe(contract.getOrderNo()))
                .replace("${contractNo}", safe(contract.getContractNo()))
                .replace("${signedAt}", safe(contract.getSignedAt()))
                .replace("${goodTitle}", safe(contract.getGoodTitle()))
                .replace("${skuTitle}", safe(contract.getSkuTitle()))
                .replace("${quantity}", safe(contract.getQuantity()))
                .replace("${sourceId}", safe(contract.getSourceId()))
                .replace("${authNo}", safe(contract.getAuthNo()))
                .replace("${orderKeep}", safe(contract.getOrderKeep()))
                .replace("${orderRentUnit}", safe(contract.getOrderRentUnit()))
                .replace("${rentStartAt}", safe(contract.getRentStartAt()))
                .replace("${rentEndAt}", safe(contract.getRentEndAt()))
                .replace("${depositText}", safe(contract.getDepositText()))
                .replace("${rentText}", safe(contract.getRentText()))
                .replace("${totalText}", safe(contract.getTotalText()));
    }

    private User loadUser(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            return null;
        }
        return userMapper.selectOne(new QueryWrapper<User>().eq("uuid", userUuid).last("LIMIT 1"));
    }

    private String resolveContractNo(Order order) {
        if (StringUtils.hasText(order.getContractNo())) {
            return order.getContractNo();
        }
        return "HT" + (StringUtils.hasText(order.getOrderNo()) ? order.getOrderNo() : String.valueOf(order.getOrderId()));
    }

    private Long resolveAgreedAt(Order order) {
        if (order.getContractAgreedAt() != null && order.getContractAgreedAt() > 0) {
            return order.getContractAgreedAt();
        }
        if (order.getCreatetime() != null && order.getCreatetime() > 0) {
            return order.getCreatetime();
        }
        if (order.getUpdateTime() != null) {
            return order.getUpdateTime().getTime();
        }
        return System.currentTimeMillis();
    }

    private String resolveBuyerName(User user, RentUserProfileExtra extra, Order order) {
        if (user != null && StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        if (extra != null && StringUtils.hasText(extra.getRealName())) {
            return extra.getRealName();
        }
        return order.getAvatar();
    }

    private String resolveBuyerIdCard(User user, RentUserProfileExtra extra, Order order) {
        if (user != null && StringUtils.hasText(user.getIdCard())) {
            return user.getIdCard();
        }
        if (extra != null && StringUtils.hasText(extra.getIdCard())) {
            return extra.getIdCard();
        }
        return order.getEm();
    }

    private String resolveBuyerPhone(User user, Order order) {
        if (user != null && StringUtils.hasText(user.getUserTel())) {
            return user.getUserTel();
        }
        return order.getUserTel();
    }

    private String resolveBuyerAddress(Order order, RentOrderExtension extension) {
        if (extension != null && extension.getAddressInfo() != null
                && StringUtils.hasText(extension.getAddressInfo().getDetail())) {
            return extension.getAddressInfo().getDetail();
        }
        return order.getAddr();
    }

    private RentViews.RentalContractView loadSnapshot(Order order) {
        if (order == null || !StringUtils.hasText(order.getContractSnapshotJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(order.getContractSnapshotJson(), RentViews.RentalContractView.class);
        } catch (Exception e) {
            log.warn("租赁协议快照解析失败，将实时组装: orderId={}, msg={}", order.getOrderId(), e.getMessage());
            return null;
        }
    }

    private String formatCent(Integer cent) {
        int value = cent == null ? 0 : cent;
        return "¥" + BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String toJson(RentViews.RentalContractView contract) {
        try {
            return objectMapper.writeValueAsString(contract);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String extractOssRelativePath(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int index = url.indexOf("/uploads/");
        if (index >= 0) {
            return url.substring(index + "/uploads/".length());
        }
        return url;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
