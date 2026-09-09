package com.fly.rent.support.util;

import com.alipay.api.domain.AlipayCommerceRentOrderAftersaleConfirmModel;
import com.alipay.api.domain.AlipayCommerceRentOrderAftersaleCreateModel;
import com.alipay.api.domain.AlipayCommerceRentOrderCloseModel;
import com.alipay.api.domain.AlipayCommerceRentOrderCreateModel;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentApproveModel;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentFinishModel;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentReceiveModel;
import com.alipay.api.domain.AlipayCommerceRentOrderFulfillmentSendModel;
import com.alipay.api.domain.AlipayCommerceRentOrderQueryModel;
import com.alipay.api.domain.AlipayCommerceRentRiskConsultModel;
import com.alipay.api.domain.AlipayOpenMiniOrderInstallmentCreateModel;
import com.alipay.api.domain.AlipayTradeCreateModel;

/**
 * Applies Alipay user identity values to SDK models.
 *
 * The system stores the current miniapp user identifier in user_uuid. Depending
 * on the OAuth response, that value may be a 2088 user_id or an open_id. Alipay
 * APIs expose separate fields for those two identifier types, so callers must
 * not blindly put open_id into buyer_id/user_id.
 */
public final class AlipayUserIdentityUtil {

    private static final String ALIPAY_USER_ID_PREFIX = "2088";
    private static final int ALIPAY_USER_ID_LENGTH = 16;

    private AlipayUserIdentityUtil() {
    }

    public static boolean isAlipayUserId(String identity) {
        String value = trimToNull(identity);
        return value != null
                && value.length() == ALIPAY_USER_ID_LENGTH
                && value.startsWith(ALIPAY_USER_ID_PREFIX)
                && value.chars().allMatch(Character::isDigit);
    }

    public static void applyBuyerIdentity(AlipayCommerceRentOrderCreateModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setBuyerId(value);
        } else {
            model.setBuyerOpenId(value);
        }
    }

    public static void applyBuyerIdentity(AlipayCommerceRentOrderQueryModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setBuyerId(value);
        } else {
            model.setBuyerOpenId(value);
        }
    }

    public static void applyBuyerIdentity(AlipayCommerceRentOrderCloseModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setBuyerId(value);
        } else {
            model.setBuyerOpenId(value);
        }
    }

    public static void applyBuyerIdentity(AlipayCommerceRentOrderAftersaleCreateModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setBuyerId(value);
        } else {
            model.setBuyerOpenId(value);
        }
    }

    public static void applyBuyerIdentity(AlipayCommerceRentOrderAftersaleConfirmModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setBuyerId(value);
        } else {
            model.setBuyerOpenId(value);
        }
    }

    public static void applyBuyerIdentity(AlipayTradeCreateModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setBuyerId(value);
        } else {
            model.setBuyerOpenId(value);
        }
    }

    public static void applyUserIdentity(AlipayCommerceRentOrderFulfillmentApproveModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setUserId(value);
        } else {
            model.setOpenId(value);
        }
    }

    public static void applyUserIdentity(AlipayCommerceRentOrderFulfillmentSendModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setUserId(value);
        } else {
            model.setOpenId(value);
        }
    }

    public static void applyUserIdentity(AlipayCommerceRentOrderFulfillmentReceiveModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setUserId(value);
        } else {
            model.setOpenId(value);
        }
    }

    public static void applyUserIdentity(AlipayCommerceRentOrderFulfillmentFinishModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setUserId(value);
        } else {
            model.setOpenId(value);
        }
    }

    public static void applyUserIdentity(AlipayOpenMiniOrderInstallmentCreateModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setUserId(value);
        } else {
            model.setOpenId(value);
        }
    }

    public static void applyRiskConsultIdentity(AlipayCommerceRentRiskConsultModel model, String identity) {
        String value = trimToNull(identity);
        if (model == null || value == null) {
            return;
        }
        if (isAlipayUserId(value)) {
            model.setAlipayUserId(value);
        } else {
            model.setAlipayOpenId(value);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
