package com.common.repair;

/**
 * 维修服务抽象模板（模板方法模式）
 * 定义维修业务的标准流程，子类实现差异部分
 *
 * @param <E> 维修实体类型
 * @param <V> 查询条件类型
 * @param <R> 列表返回类型（如 PageInfo 或 Page）
 */
public interface AbstractRepairService<E, V, R> {

    /**
     * 维修列表查询
     */
    R list(V queryVo);

    /**
     * 新增维修
     */
    void add(E entity);

    /**
     * 完成维修（修改状态）
     */
    void update(E entity);

    /**
     * 获取单个维修详情
     */
    Object get(Object param);

    /**
     * 继续维修
     */
    void again(E entity);

    /**
     * 删除维修记录
     */
    void delete(E entity);

    /**
     * 维修记录修改
     */
    void infoUpdate(E entity);
}
