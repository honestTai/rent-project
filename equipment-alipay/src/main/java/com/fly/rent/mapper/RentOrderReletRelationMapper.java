package com.fly.rent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fly.rent.entity.RentOrderReletRelation;
import org.springframework.stereotype.Repository;

/**
 * 租赁订单续租关系 Mapper。
 * 基于 MyBatis-Plus 提供续租关系的单表保存、查询和更新能力。
 */
@Repository
public interface RentOrderReletRelationMapper extends BaseMapper<RentOrderReletRelation> {
}
