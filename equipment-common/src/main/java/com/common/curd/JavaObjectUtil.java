package com.common.curd;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 通用查询构造器（建造者模式）
 * 通过注解 + 反射自动构造 QueryWrapper，消除各 Service 中重复的查询条件拼装代码
 *
 * 支持的注解：
 *  - @EqSearch      等于查询
 *  - @LikeSearch    模糊查询
 *  - @InSearch       IN 查询（字段需为 List 类型）
 *  - @BetweenSearch  区间查询（字段需为 List 类型，取 [0] 和 [1]）
 *  - @OrderByAsc     升序排序
 *  - @OrderByDesc    降序排序
 */
public class JavaObjectUtil {

    /**
     * 反射构造通用的查询器方法
     *
     * @param t 传入进来的参数（VTO/VO 对象）
     * @return 构造好的 QueryWrapper
     */
    public static <T> QueryWrapper<T> queryWrapper(T t) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();

        for (Field declaredField : t.getClass().getDeclaredFields()) {
            declaredField.setAccessible(true);

            String columnName = resolveColumnName(declaredField);
            Object value = getFieldValue(declaredField, t);

            // @EqSearch - 等于
            if (declaredField.isAnnotationPresent(EqSearch.class)) {
                if (isNotEmpty(value)) {
                    queryWrapper.eq(columnName, value);
                }
            }

            // @LikeSearch - 模糊
            if (declaredField.isAnnotationPresent(LikeSearch.class)) {
                if (isNotEmpty(value)) {
                    queryWrapper.like(columnName, String.valueOf(value));
                }
            }

            // @InSearch - IN 查询
            if (declaredField.isAnnotationPresent(InSearch.class)) {
                if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
                    InSearch ann = declaredField.getAnnotation(InSearch.class);
                    String col = ann.column().isEmpty() ? columnName : ann.column();
                    queryWrapper.in(col, (Collection<?>) value);
                }
            }

            // @BetweenSearch - 区间查询
            if (declaredField.isAnnotationPresent(BetweenSearch.class)) {
                if (value instanceof List) {
                    List<?> range = (List<?>) value;
                    if (range.size() >= 2 && range.get(0) != null && range.get(1) != null) {
                        BetweenSearch ann = declaredField.getAnnotation(BetweenSearch.class);
                        String col = ann.column().isEmpty() ? columnName : ann.column();
                        queryWrapper.between(col, range.get(0), range.get(1));
                    }
                }
            }

            // @OrderByAsc - 升序
            if (declaredField.isAnnotationPresent(OrderByAsc.class)) {
                queryWrapper.orderByAsc(columnName);
            }

            // @OrderByDesc - 降序
            if (declaredField.isAnnotationPresent(OrderByDesc.class)) {
                queryWrapper.orderByDesc(columnName);
            }
        }

        return queryWrapper;
    }

    /**
     * 解析数据库列名：优先使用 @TableField 的 value，其次使用字段名
     */
    private static String resolveColumnName(Field field) {
        if (field.isAnnotationPresent(TableField.class)) {
            String tableFieldValue = field.getAnnotation(TableField.class).value();
            if (tableFieldValue != null && !tableFieldValue.isEmpty()) {
                return tableFieldValue;
            }
        }
        return field.getName();
    }

    /**
     * 安全获取字段值
     */
    private static Object getFieldValue(Field field, Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("反射获取字段值失败: " + field.getName(), e);
        }
    }

    /**
     * 判断值是否非空且非空字符串
     */
    private static boolean isNotEmpty(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }
        return true;
    }
}
