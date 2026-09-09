package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 租赁订单用户寄回记录。
 * 独立保存小程序用户提交的归还物流、照片凭证和说明，避免复用商家发货字段导致后台无法区分寄出与寄回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rent_order_return_record")
public class RentOrderReturnRecord extends Model<RentOrderReturnRecord> {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 本地 rent_order.order_id，用于后台订单页和台账关联。 */
    @TableField("order_id")
    private Integer orderId;

    /** 本地业务订单号 rent_order.order_no，便于人工检索和跨表对账。 */
    @TableField("order_no")
    private String orderNo;

    /** 支付宝交易组件订单号 rent_order.alipay_order_id，可用于和支付宝履约记录对账。 */
    @TableField("rent_order_id")
    private String rentOrderId;

    /** 小程序用户支付宝 userId/openId，来源于订单 user_uuid。 */
    @TableField("user_uuid")
    private String userUuid;

    /** 寄回方式，如 ONLINE_EXPRESS、OFFLINE 或支付宝约定的归还类型。 */
    @TableField("return_type")
    private String returnType;

    /** 用户填写的快递公司编码，传给支付宝 delivery_id。 */
    @TableField("express_code")
    private String expressCode;

    /** 用户填写的快递公司名称，供后台人工识别。 */
    @TableField("express_company")
    private String expressCompany;

    /** 用户填写的寄回运单号，传给支付宝 waybill_id。 */
    @TableField("express_no")
    private String expressNo;

    /** 用户上传的寄回照片 JSON 数组字符串，存储 OSS key 或 URL。 */
    @TableField("photo_urls")
    private String photoUrls;

    /** 用户对寄回设备状态、包装或异常情况的文字说明。 */
    @TableField("detail_remark")
    private String detailRemark;

    /** 当前记录状态，SUBMITTED 表示用户已提交，SEND_SUCCESS 表示履约发货成功，SEND_FAILED 表示履约失败但记录已留存。 */
    @TableField("status")
    private String status;

    /** 履约失败时支付宝或本地返回的错误原因，用于后台排查。 */
    @TableField("fail_reason")
    private String failReason;

    /** 用户首次提交寄回信息的毫秒时间戳。 */
    @TableField("submitted_at")
    private Long submittedAt;

    /** 记录创建时间，使用数据库 datetime 便于人工查询。 */
    @TableField("created_at")
    private Date createdAt;

    /** 记录最后更新时间，用户补充信息或履约结果变化时更新。 */
    @TableField("updated_at")
    private Date updatedAt;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
