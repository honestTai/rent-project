package com.fly.rent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fly.rent.entity.RentOrderReturnRecord;
import org.springframework.stereotype.Repository;

/**
 * 租赁订单用户寄回记录 Mapper。
 * 通过 MyBatis-Plus 提供单表新增、更新和查询能力，服务层负责按订单维度做唯一记录合并。
 */
@Repository
public interface RentOrderReturnRecordMapper extends BaseMapper<RentOrderReturnRecord> {
}
