package com.fly.rent.mapper;

import com.fly.rent.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fly.rent.miniapp.order.dto.MonthlyOrderAnalyticsAggregate;
import com.fly.rent.miniapp.order.dto.YearlyOrderAnalyticsAggregate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <p>
 * 订单表 Mapper 接口
 * </p>
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Repository
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按当前用户和自然月范围在数据库内完成月度订单聚合。
     * created_at 是毫秒时间戳，范围必须由调用方按业务时区换算为左闭右开区间。
     */
    @Select({
            "<script>",
            "SELECT",
            "COUNT(*) AS orderCount,",
            "COALESCE(SUM(CASE WHEN o.alipay_status IN",
            "<foreach collection='activeStatuses' item='status' open='(' separator=',' close=')'>#{status}</foreach>",
            "THEN 1 ELSE 0 END), 0) AS activeCount,",
            "COALESCE(SUM(CASE WHEN o.alipay_status = #{finishedStatus} THEN 1 ELSE 0 END), 0) AS finishedCount,",
            "COALESCE(SUM(CASE WHEN o.alipay_status IN",
            "<foreach collection='closedStatuses' item='status' open='(' separator=',' close=')'>#{status}</foreach>",
            "THEN 1 ELSE 0 END), 0) AS closedCount,",
            "COALESCE(SUM(CASE WHEN EXISTS (",
            "SELECT 1 FROM rent_order_relet_relation relation",
            "WHERE relation.child_order_id = o.order_id AND relation.user_uuid = #{userUuid}",
            ") THEN 1 ELSE 0 END), 0) AS renewOrderCount,",
            "0 AS rentAmountCents,",
            "COALESCE(SUM(CASE WHEN o.created_at &lt; #{bucket2StartMs} THEN 1 ELSE 0 END), 0) AS bucket1Count,",
            "COALESCE(SUM(CASE WHEN o.created_at &gt;= #{bucket2StartMs} AND o.created_at &lt; #{bucket3StartMs}",
            "THEN 1 ELSE 0 END), 0) AS bucket2Count,",
            "COALESCE(SUM(CASE WHEN o.created_at &gt;= #{bucket3StartMs} AND o.created_at &lt; #{bucket4StartMs}",
            "THEN 1 ELSE 0 END), 0) AS bucket3Count,",
            "COALESCE(SUM(CASE WHEN o.created_at &gt;= #{bucket4StartMs} THEN 1 ELSE 0 END), 0) AS bucket4Count",
            "FROM rent_order o",
            "WHERE o.user_uuid = #{userUuid}",
            "AND o.created_at &gt;= #{monthStartMs}",
            "AND o.created_at &lt; #{nextMonthStartMs}",
            "</script>"
    })
    MonthlyOrderAnalyticsAggregate selectMonthlyAnalytics(
            @Param("userUuid") String userUuid,
            @Param("monthStartMs") long monthStartMs,
            @Param("nextMonthStartMs") long nextMonthStartMs,
            @Param("bucket2StartMs") long bucket2StartMs,
            @Param("bucket3StartMs") long bucket3StartMs,
            @Param("bucket4StartMs") long bucket4StartMs,
            @Param("activeStatuses") List<String> activeStatuses,
            @Param("finishedStatus") String finishedStatus,
            @Param("closedStatuses") List<String> closedStatuses
    );

    /** 一次查询完成当前用户年度订单的逐月计数、状态和续租聚合。 */
    @Select({
            "<script>",
            "SELECT DATE_FORMAT(CONVERT_TZ(FROM_UNIXTIME(o.created_at / 1000), @@session.time_zone, #{businessTimezone}), '%Y-%m') AS month,",
            "COUNT(*) AS orderCount,",
            "COALESCE(SUM(CASE WHEN o.alipay_status IN",
            "<foreach collection='activeStatuses' item='status' open='(' separator=',' close=')'>#{status}</foreach>",
            "THEN 1 ELSE 0 END), 0) AS activeCount,",
            "COALESCE(SUM(CASE WHEN o.alipay_status = #{finishedStatus} THEN 1 ELSE 0 END), 0) AS finishedCount,",
            "COALESCE(SUM(CASE WHEN o.alipay_status IN",
            "<foreach collection='closedStatuses' item='status' open='(' separator=',' close=')'>#{status}</foreach>",
            "THEN 1 ELSE 0 END), 0) AS closedCount,",
            "COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM rent_order_relet_relation relation",
            "WHERE relation.child_order_id = o.order_id AND relation.user_uuid = #{userUuid}) THEN 1 ELSE 0 END), 0) AS renewOrderCount",
            "FROM rent_order o",
            "WHERE o.user_uuid = #{userUuid}",
            "AND o.created_at &gt;= #{yearStartMs}",
            "AND o.created_at &lt; #{nextYearStartMs}",
            "GROUP BY month ORDER BY month",
            "</script>"
    })
    List<YearlyOrderAnalyticsAggregate> selectYearlyAnalytics(
            @Param("userUuid") String userUuid,
            @Param("yearStartMs") long yearStartMs,
            @Param("nextYearStartMs") long nextYearStartMs,
            @Param("businessTimezone") String businessTimezone,
            @Param("activeStatuses") List<String> activeStatuses,
            @Param("finishedStatus") String finishedStatus,
            @Param("closedStatuses") List<String> closedStatuses
    );

    /**
     * 按商品汇总已付款并进入履约的租赁件数。quantity 为空或小于 1 的历史订单按 1 件计算。
     */
    @Select({
            "<script>",
            "SELECT o.goods_id AS goodId,",
            "COALESCE(SUM(CASE WHEN o.quantity IS NULL OR o.quantity &lt; 1 THEN 1 ELSE o.quantity END), 0) AS salesCount",
            "FROM rent_order o",
            "WHERE o.goods_id IN",
            "<foreach collection='goodIds' item='goodId' open='(' separator=',' close=')'>#{goodId}</foreach>",
            "AND UPPER(TRIM(o.alipay_status)) IN",
            "<foreach collection='includedStatuses' item='status' open='(' separator=',' close=')'>#{status}</foreach>",
            "GROUP BY o.goods_id",
            "</script>"
    })
    List<GoodsSalesCount> countEffectiveSalesByGoodIds(
            @Param("goodIds") List<Integer> goodIds,
            @Param("includedStatuses") List<String> includedStatuses
    );
}
