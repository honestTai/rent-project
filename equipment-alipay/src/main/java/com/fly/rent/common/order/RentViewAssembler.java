package com.fly.rent.common.order;

import com.fly.rent.common.dto.RentViews;
import com.fly.rent.common.model.RentAddressSnapshot;
import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.model.RentUserProfileExtra;
import com.fly.rent.common.support.RentTimeSupport;
import com.fly.rent.entity.Attr;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.User;
import com.fly.rent.support.util.RentInstallmentPlanSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 负责把数据库实体转换成接口视图对象。
 */
@Component
public class RentViewAssembler {

    public RentAddressSnapshot toAddressSnapshot(RentViews.AddressView view) {
        if (view == null) {
            return null;
        }
        RentAddressSnapshot snapshot = new RentAddressSnapshot();
        snapshot.setCity(view.getCity());
        snapshot.setDistrict(view.getDistrict());
        snapshot.setDetail(view.getDetail());
        snapshot.setConsignee(view.getConsignee());
        snapshot.setMobile(view.getMobile());
        return snapshot;
    }

    public RentViews.AddressView toAddressView(RentAddressSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        RentViews.AddressView view = new RentViews.AddressView();
        view.setCity(snapshot.getCity());
        view.setDistrict(snapshot.getDistrict());
        view.setDetail(snapshot.getDetail());
        view.setConsignee(snapshot.getConsignee());
        view.setMobile(snapshot.getMobile());
        return view;
    }

    public RentViews.UserProfileView toUserProfileView(User user, RentUserProfileExtra extra) {
        RentViews.UserProfileView view = new RentViews.UserProfileView();
        view.setUserAvatar(user == null ? null : user.getUserAvatar());
        view.setUserTitle(user == null ? null : user.getUserTitle());
        view.setUserTel(user == null ? null : user.getUserTel());
        view.setUserBirth(user == null ? null : RentTimeSupport.formatDate(user.getUserBirth()));
        // 实名认证：用户表姓名+身份证均非空，或扩展里已认证且信息完整
        boolean fromUser = user != null && StringUtils.hasText(user.getRealName()) && StringUtils.hasText(user.getIdCard());
        boolean fromExtra = extra != null && Boolean.TRUE.equals(extra.getVerified())
                && StringUtils.hasText(extra.getRealName()) && StringUtils.hasText(extra.getIdCard());
        view.setVerified(fromUser || fromExtra);
        view.setRiskLevel(extra != null ? extra.getRiskLevel() : "低风险");
        view.setCreditScore(extra != null ? extra.getCreditScore() : 650);
        view.setCity(extra != null ? extra.getCity() : null);
        view.setIdCardTail(extra != null && StringUtils.hasText(extra.getIdCardTail())
                ? extra.getIdCardTail()
                : (user != null && StringUtils.hasText(user.getIdCard()) ? RentTimeSupport.tailIdCard(user.getIdCard()) : ""));
        String realNameVal = (user != null && StringUtils.hasText(user.getRealName()))
                ? user.getRealName()
                : (extra != null ? extra.getRealName() : null);
        view.setRealName(StringUtils.hasText(realNameVal) ? realNameVal : null);
        view.setBlocked(user != null && Integer.valueOf(1).equals(user.getIsNoRequest()));
        return view;
    }

    public RentViews.RentalGoodView toRentalGoodView(Good good, Attr attr) {
        List<Attr> attrs = attr == null ? Collections.<Attr>emptyList() : Collections.singletonList(attr);
        return toRentalGoodView(good, attr, attrs);
    }

    public RentViews.RentalGoodView toRentalGoodView(
            Good good,
            Attr attr,
            List<Attr> allAttrs
    ) {
        RentViews.RentalGoodView view = new RentViews.RentalGoodView();
        view.setGoodId(String.valueOf(good.getGoodId()));
        view.setSkuId(attr == null ? null : String.valueOf(attr.getAttrId()));
        view.setGoodTitle(good.getGoodTitle());
        view.setGoodSubTitle(StringUtils.hasText(good.getGoodAct()) ? good.getGoodAct() : good.getGoodDesc());
        int minDailyCents = attr != null && attr.getAttrAmount() != null ? attr.getAttrAmount() : safeInt(good.getGoodMinamo());
        view.setDailyPrice(minDailyCents);
        view.setMonthlyPrice(minDailyCents * 30);
        view.setDeposit(attr != null && attr.getAttrDeposit() != null ? attr.getAttrDeposit() : safeInt(good.getGoodDeposit()));
        view.setBuyoutEnabled(isBuyoutEnabled(attr));
        view.setBuyoutPrice(resolveBuyoutPrice(attr));
        view.setRentToOwn(isRentToOwn(attr));
        view.setCover(good.getGoodCover());
        view.setGoodLabel(null);
        view.setServiceTags(buildServiceTags(good, attr));
        view.setInventory(attr == null ? 0 : safeInt(attr.getAttrNum()));
        view.setMinRent(attr == null ? 1 : safeInt(attr.getMinRent(), 1));
        view.setInstallmentEnabled(attr != null && attr.getInstallmentEnabled() != null && attr.getInstallmentEnabled() == 1);
        view.setInstallmentPeriods(attr == null
                ? new ArrayList<Integer>()
                : RentInstallmentPlanSupport.availablePaymentPeriods(attr));
        RentViews.RentalGoodView.DeliveryInfoView deliveryInfo = new RentViews.RentalGoodView.DeliveryInfoView();
        deliveryInfo.setSupportExpress(true);
        deliveryInfo.setSupportStorePickup(good.getOfflinePickup() != null && good.getOfflinePickup() == 0);
        view.setDeliveryInfo(deliveryInfo);
        view.setDeliveryLeadTime("当日18:00前下单，次日发出");
        view.setSummary(StringUtils.hasText(good.getGoodDesc()) ? good.getGoodDesc() : good.getGoodCon());
        view.setCategoryCode(good.getCategoryCode());
        view.setBrand(good.getBrand());
        view.setDeviceType(good.getDeviceType());
        view.setSupportedRentUnits(resolveSupportedRentUnits(allAttrs));
        view.setDefaultRentUnit(resolveDefaultRentUnit(good, attr));
        view.setFeatured(Integer.valueOf(1).equals(good.getFeatured()));
        view.setFeaturedSort(good.getFeaturedSort());
        return view;
    }

    private List<String> resolveSupportedRentUnits(List<Attr> attrs) {
        boolean supportsDay = false;
        boolean supportsMonth = false;
        if (attrs != null) {
            for (Attr current : attrs) {
                if (current != null && current.getAttrTradeType() != null && current.getAttrTradeType() > 1) {
                    supportsMonth = true;
                } else if (current != null) {
                    supportsDay = true;
                }
            }
        }
        List<String> units = new ArrayList<>();
        if (supportsDay) units.add("DAY");
        if (supportsMonth) units.add("MONTH");
        return units;
    }

    private String resolveDefaultRentUnit(Good good, Attr primaryAttr) {
        if (StringUtils.hasText(good.getDefaultRentUnit())) {
            return good.getDefaultRentUnit().trim().toUpperCase();
        }
        return primaryAttr != null && primaryAttr.getAttrTradeType() != null
                && primaryAttr.getAttrTradeType() > 1 ? "MONTH" : "DAY";
    }

    public RentViews.RentalOrderView toRentalOrderView(
            Order order,
            Good good,
            RentOrderExtension extension,
            RentUserProfileExtra extra
    ) {
        return toRentalOrderView(order, good, null, extension, extra);
    }

    public RentViews.RentalOrderView toRentalOrderView(
            Order order,
            Good good,
            Attr attr,
            RentOrderExtension extension,
            RentUserProfileExtra extra
    ) {
        RentViews.RentalOrderView view = new RentViews.RentalOrderView();
        view.setOrderId(order.getOrderNo());
        view.setRentOrderId(order.getRentOrderId());
        view.setSourceId(order.getSourceId());
        view.setGoodId(order.getGoodId() == null ? null : String.valueOf(order.getGoodId()));
        view.setGoodTitle(order.getGoodTitle());
        view.setCover(order.getGoodCover());
        view.setAlipayStatus(normalizeOrderStatus(order.getAlipayStatus()));
        view.setOrderKeep(order.getOrderKeep());
        view.setOrderRentUnit(extension != null && StringUtils.hasText(extension.getRentUnit())
                ? RentTimeSupport.normalizeRentUnit(extension.getRentUnit())
                : "天");
        view.setOrderDeposit(order.getOrderDeposit());
        view.setOrderFirstAmount(order.getOrderFirstAmount() != null && order.getOrderFirstAmount() > 0
                ? order.getOrderFirstAmount()
                : order.getOrderTotal());
        view.setOrderEveryAmount(order.getOrderEveryAmount());
        view.setOrderEndAmount(order.getOrderEndAmount());
        view.setCurrentPeriod(order.getOrderRentPeriods());
        view.setTotalPeriods(order.getOrderTotalRentPeriods());
        view.setOrderTotal(order.getOrderTotal());
        view.setOrderTradeType(order.getOrderTradeType());
        view.setBuyoutEnabled(isBuyoutEnabled(attr));
        view.setBuyoutPrice(resolveBuyoutPrice(attr));
        view.setRentToOwn(order.getRentToSend() != null && order.getRentToSend() == 1 || isRentToOwn(attr));
        view.setBuyoutPaid(Boolean.FALSE);
        view.setCreatedAt(RentTimeSupport.formatDateTime(order.getCreatetime()));
        view.setDeliverAt(RentTimeSupport.formatDateTime(order.getOrderSendTime()));
        view.setRentStartAt(RentTimeSupport.formatDateTime(order.getOrderStart()));
        view.setRentEndAt(RentTimeSupport.formatDateTime(order.getOrderEnd()));
        view.setPickupType(resolvePickupType(order, extension));
        view.setAddressInfo(resolveAddress(order, extension, extra));
        view.setMerchantName(null);
        view.setNote(StringUtils.hasText(order.getRemark()) ? order.getRemark() : buildOrderNote(order.getAlipayStatus()));
        view.setAvatar(StringUtils.hasText(order.getAvatar()) ? order.getAvatar() : (extra == null ? null : extra.getRealName()));
        view.setEm(StringUtils.hasText(order.getEm()) ? order.getEm() : (extra == null ? null : RentTimeSupport.maskIdCard(extra.getIdCard())));
        view.setCourierNo(order.getCourno());
        view.setCourierName(order.getCourName());
        view.setCourierCode(order.getCourCode());
        view.setIdCardPhotoRequired(extension != null && Boolean.TRUE.equals(extension.getIdCardPhotoRequired()));
        view.setEsignRequired(extension != null && Boolean.TRUE.equals(extension.getEsignRequired()));
        if (extension != null && extension.getIdCardPhotoUrls() != null) {
            view.setIdCardPhotoUrls(extension.getIdCardPhotoUrls());
        }
        view.setIdCardPhotoUploadedAt(extension == null ? null : extension.getIdCardPhotoUploadedAt());
        view.setIdCardPhotoReviewStatus(resolveIdCardPhotoReviewStatus(extension));
        view.setIdCardPhotoReviewRemark(extension == null ? null : extension.getIdCardPhotoReviewRemark());
        view.setIdCardPhotoReviewedAt(extension == null ? null : extension.getIdCardPhotoReviewedAt());
        view.setContractNo(order.getContractNo());
        view.setContractPdfUrl(order.getContractPdfUrl());
        view.setContractPdfPath(order.getContractPdfPath());
        view.setContractAgreedAt(RentTimeSupport.formatDateTime(order.getContractAgreedAt()));
        return view;
    }

    /**
     * 兼容旧扩展数据的身份证照片审核状态。
     * 旧数据只有 required 和照片列表时，按是否已上传正反面推导待上传或待审核。
     */
    private String resolveIdCardPhotoReviewStatus(RentOrderExtension extension) {
        if (extension == null || !Boolean.TRUE.equals(extension.getIdCardPhotoRequired())) {
            return "NOT_REQUIRED";
        }
        if (StringUtils.hasText(extension.getIdCardPhotoReviewStatus())) {
            return extension.getIdCardPhotoReviewStatus();
        }
        return extension.getIdCardPhotoUrls() != null && extension.getIdCardPhotoUrls().size() >= 2
                ? "PENDING_REVIEW"
                : "PENDING_UPLOAD";
    }

    private RentViews.AddressView resolveAddress(Order order, RentOrderExtension extension, RentUserProfileExtra extra) {
        if (extension != null && extension.getAddressInfo() != null) {
            RentViews.AddressView view = toAddressView(extension.getAddressInfo());
            view.setCity(null);
            view.setDistrict(null);
            return view;
        }
        RentViews.AddressView fallback = new RentViews.AddressView();
        fallback.setDetail(order.getAddr());
        fallback.setConsignee(order.getUserTitle());
        fallback.setMobile(order.getUserTel());
        return fallback;
    }

    private String resolvePickupType(Order order, RentOrderExtension extension) {
        if (extension != null && StringUtils.hasText(extension.getPickupType())) {
            return extension.getPickupType();
        }
        return order.getOfflinePickup() != null && order.getOfflinePickup() == 0 ? "store" : "express";
    }

    private List<String> buildServiceTags(Good good, Attr attr) {
        List<String> tags = new ArrayList<>();
        if (attr != null && attr.getFree() != null && attr.getFree() == 1) {
            tags.add("支持信用免押");
        }
        if (isBuyoutEnabled(attr)) {
            tags.add("支持买断");
        }
        if (isRentToOwn(attr)) {
            tags.add("租完即送");
        }
        if (good.getOfflinePickup() != null && good.getOfflinePickup() == 0) {
            tags.add("支持门店自提");
        }
        if (attr != null && attr.getMaxRent() != null && attr.getMaxRent() > safeInt(attr.getMinRent(), 1)) {
            tags.add("支持续租");
        }
        if (attr != null && attr.getInstallmentEnabled() != null && attr.getInstallmentEnabled() == 1) {
            tags.add("支持分期");
        }
        if (tags.isEmpty()) {
            tags.add("支持灵活租期");
        }
        return tags;
    }

    private boolean isBuyoutEnabled(Attr attr) {
        return attr != null && attr.getBuyout() != null && attr.getBuyout() == 1;
    }

    private boolean isRentToOwn(Attr attr) {
        return attr != null && attr.getRentToSend() != null && attr.getRentToSend() == 1;
    }

    private Integer resolveBuyoutPrice(Attr attr) {
        if (!isBuyoutEnabled(attr)) {
            return 0;
        }
        if (attr.getBuyoutval() != null && attr.getBuyoutval() > 0) {
            return attr.getBuyoutval();
        }
        return attr.getAttrDeposit() == null ? 0 : Math.max(attr.getAttrDeposit(), 0);
    }

    private String buildOrderNote(String status) {
        String normalized = normalizeOrderStatus(status);
        if ("CREATED".equals(normalized)) {
            return "下单完成，等待签约";
        }
        if ("PAID".equals(normalized)) {
            return "已支付，等待商家发货";
        }
        if ("DELIVERED".equals(normalized)) {
            return "商家已发货";
        }
        if ("FINISHED".equals(normalized)) {
            return "订单已完成";
        }
        return "订单状态已更新";
    }

    private String normalizeOrderStatus(String status) {
        if ("PENDINGCANCLE".equalsIgnoreCase(status) || "PENDING_CANCEL".equalsIgnoreCase(status)) {
            return "PENDING_CANCEL";
        }
        return status;
    }

    private int safeInt(Integer value) {
        return safeInt(value, 0);
    }

    private int safeInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
