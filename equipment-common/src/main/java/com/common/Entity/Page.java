package com.common.Entity;

import lombok.Data;

/**
 * 分页参数
 */
@Data
public class Page {
    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 页码
     */
    private Integer page;
}
