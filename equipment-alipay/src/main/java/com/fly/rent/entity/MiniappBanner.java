package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 小程序首页轮播图。
 */
@Data
@TableName("miniapp_banner")
public class MiniappBanner {

    @TableId(value = "banner_id", type = IdType.AUTO)
    private Integer bannerId;

    private String title;

    @TableField("description")
    private String description;

    private String badge;

    private String image;

    @TableField("link_type")
    private String linkType;

    @TableField("link_value")
    private String linkValue;

    @TableField("sort_order")
    private Integer sortOrder;

    private Integer status;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}
