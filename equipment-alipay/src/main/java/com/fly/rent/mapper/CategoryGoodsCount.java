package com.fly.rent.mapper;

import lombok.Data;

/** 单次分组查询返回的分类商品数。 */
@Data
public class CategoryGoodsCount {
    private String categoryCode;
    private Long goodsCount;
}
