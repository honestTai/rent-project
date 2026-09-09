package com.fly.rent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fly.rent.entity.RentAftersaleNotifyRecord;
import org.springframework.stereotype.Repository;

/**
 * 支付宝售后通知流水 Mapper。
 * 该表只负责通知级别的幂等和审计，不承载业务含义。
 */
@Repository
public interface RentAftersaleNotifyRecordMapper extends BaseMapper<RentAftersaleNotifyRecord> {
}
