package com.fly.rent.mapper;

import com.fly.rent.entity.Good;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 Mapper 接口
 * </p>
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public interface GoodMapper extends BaseMapper<Good> {

    /**
     * 一次查询所有分类的公开商品计数，零库存商品仍计数；没有任何 SKU 的商品不属于可见目录。
     */
    @Select({
            "<script>",
            "SELECT g.category_code AS categoryCode, COUNT(DISTINCT g.goods_id) AS goodsCount",
            "FROM goods g",
            "WHERE g.status = 1 AND g.is_public = 1",
            "AND g.category_code IN",
            "<foreach collection='categoryCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach>",
            "AND EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id = g.goods_id)",
            "GROUP BY g.category_code",
            "</script>"
    })
    List<CategoryGoodsCount> countVisibleGoodsByCategoryCodes(
            @Param("categoryCodes") Collection<String> categoryCodes);

    /** 后台目录使用：统计所有绑定商品，不混入公开状态或支付宝发布状态。 */
    @Select({
            "<script>",
            "SELECT category_code AS categoryCode, COUNT(*) AS goodsCount FROM goods",
            "WHERE category_code IN",
            "<foreach collection='categoryCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach>",
            "GROUP BY category_code",
            "</script>"
    })
    List<CategoryGoodsCount> countAllGoodsByCategoryCodes(
            @Param("categoryCodes") Collection<String> categoryCodes);

    /** 单条 SQL 仅更新分类字段，避免用旧实体覆盖并发修改的库存、价格和上下架状态。 */
    @Update({
            "<script>",
            "UPDATE goods SET category_code = #{categoryCode} WHERE goods_id IN",
            "<foreach collection='goodIds' item='goodId' open='(' separator=',' close=')'>#{goodId}</foreach>",
            "</script>"
    })
    int updateCategoryCodeByIds(
            @Param("goodIds") Collection<Integer> goodIds,
            @Param("categoryCode") String categoryCode);

    @Update({
            "<script>",
            "UPDATE goods SET category_code = NULL WHERE goods_id IN",
            "<foreach collection='goodIds' item='goodId' open='(' separator=',' close=')'>#{goodId}</foreach>",
            "</script>"
    })
    int clearCategoryCodeByIds(@Param("goodIds") Collection<Integer> goodIds);
}
