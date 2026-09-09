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
 * 租赁订单续租关系表。
 * 持久化保存续租子单与父单、根单之间的关系，Redis 只作为热点缓存，不能作为续租链路的唯一数据来源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rent_order_relet_relation")
public class RentOrderReletRelation extends Model<RentOrderReletRelation> {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 续租子单本地订单 ID，对应 rent_order.order_id。 */
    @TableField("child_order_id")
    private Integer childOrderId;

    /** 续租子单本地订单号，对应 rent_order.order_no。 */
    @TableField("child_order_no")
    private String childOrderNo;

    /** 续租子单支付宝租赁订单号，建单成功后回写。 */
    @TableField("child_rent_order_id")
    private String childRentOrderId;

    /** 直接父单本地订单 ID。 */
    @TableField("parent_order_id")
    private Integer parentOrderId;

    /** 直接父单本地订单号。 */
    @TableField("parent_order_no")
    private String parentOrderNo;

    /** 直接父单支付宝租赁订单号，用于支付宝 parent_order_id。 */
    @TableField("parent_rent_order_id")
    private String parentRentOrderId;

    /** 根单本地订单 ID，多次续租时始终指向第一笔原始订单。 */
    @TableField("origin_order_id")
    private Integer originOrderId;

    /** 根单本地订单号。 */
    @TableField("origin_order_no")
    private String originOrderNo;

    /** 根单支付宝租赁订单号，用于支付宝 relet_info.origin_order_id。 */
    @TableField("origin_rent_order_id")
    private String originRentOrderId;

    /** 用户 UUID，用于按用户查询续租链路和隔离数据。 */
    @TableField("user_uuid")
    private String userUuid;

    /** 本次续租天数。 */
    @TableField("renew_duration")
    private Integer renewDuration;

    /** 原父单租赁结束时间，毫秒时间戳。 */
    @TableField("original_rent_end")
    private Long originalRentEnd;

    /** 续租子单租赁开始时间，毫秒时间戳。 */
    @TableField("relet_rent_start")
    private Long reletRentStart;

    /** 续租子单租赁结束时间，毫秒时间戳。 */
    @TableField("relet_rent_end")
    private Long reletRentEnd;

    /** 关系状态，ACTIVE 表示有效，CLOSED 表示续租子单建单失败或已关闭。 */
    @TableField("status")
    private String status;

    /** 创建时间。 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private Date updatedAt;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
