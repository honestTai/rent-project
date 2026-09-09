package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 商品规格表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("goods_sku")
public class Attr extends Model<Attr> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "sku_id", type = IdType.AUTO)
    private Integer attrId;

    @TableField("cover")
    private String attrSlid;

    @TableField("title")
    private String attrTitle;

    @TableField("description")
    private String attrDesc;

    @TableField("daily_rent")
    private Integer attrAmount;

    @TableField("rent_periods")
    private String attrRentday;

    @TableField("stock")
    private Integer attrNum;

    @TableField("goods_id")
    private Integer goodId;

    @TableField("allow_deposit_free")
    private Integer free;

    @TableField("deposit")
    private Integer attrDeposit;

    @TableField("allow_buyout")
    private Integer buyout;

    @TableField("buyout_price")
    private Integer buyoutval;

    @TableField("min_rent_days")
    private Integer minRent;

    @TableField("max_rent_days")
    private Integer maxRent;

    @TableField("penalty_per_day")
    private Integer penalAmount;

    @TableField("billing_cycle")
    private Integer attrTradeType;

    @TableField("installment_enabled")
    private Integer installmentEnabled;

    @TableField("installment_periods")
    private String installmentPeriods;

    @TableField("rent_to_own")
    private Integer rentToSend;

    @TableField("alipay_sku_id")
    private String apilyGoodSkuId;

    @Override
    public Serializable pkVal() {
        return this.attrId;
    }
}
