package com.fly.rent.mapper;

import lombok.Data;

/** 订单表按商品聚合的有效租赁件数。 */
@Data
public class GoodsSalesCount {
    private Integer goodId;
    private Long salesCount;
}
