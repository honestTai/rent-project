package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 小程序正式商品分类。code 是跨端契约主键，展示名称可以独立修改。
 */
@Data
@TableName("category")
public class CatalogCategory {

    @TableId(value = "category_id", type = IdType.AUTO)
    private Integer id;

    private String code;

    @TableField(value = "parent_code", updateStrategy = FieldStrategy.ALWAYS)
    private String parentCode;

    private String name;

    @TableField("short_name")
    private String shortName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    @TableField(value = "cover_image", updateStrategy = FieldStrategy.ALWAYS)
    private String coverImage;

    @TableField("icon_name")
    private String iconName;

    @TableField("sort_order")
    private Integer sortOrder;

    private Integer status;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}
