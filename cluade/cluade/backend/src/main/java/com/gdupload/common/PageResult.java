package com.gdupload.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应结果
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 数据列表
     */
    private List<T> records;

    public PageResult() {
    }

    public PageResult(Long total, Long current, Long size, List<T> records) {
        this.total = total;
        this.current = current;
        this.size = size;
        this.records = records;
        this.pages = (total + size - 1) / size;
    }

    /**
     * 从MyBatis Plus的Page对象转换
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return new PageResult<>(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords()
        );
    }
}
