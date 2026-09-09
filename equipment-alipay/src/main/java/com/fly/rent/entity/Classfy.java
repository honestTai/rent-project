package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品分类表。
 */
@Data
@TableName("category")
public class Classfy extends Model<Classfy> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "category_id", type = IdType.AUTO)
    private Integer classfyId;

    @TableField("name")
    private String classfyTitle;

    @TableField(exist = false)
    private String classfyCover;

    @TableField(exist = false)
    private Long classfySort;

    private Integer status;

    @TableField(exist = false)
    private Long createtime;

    @Override
    public Serializable pkVal() {
        return this.classfyId;
    }
}
