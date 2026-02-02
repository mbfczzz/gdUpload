package com.gdupload.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
public class PagedResult<T> {
    /**
     * 数据列表
     */
    private List<T> items;

    /**
     * 总数
     */
    private Integer totalCount;

    /**
     * 起始索引
     */
    private Integer startIndex;

    /**
     * 每页数量
     */
    private Integer limit;

    public PagedResult() {
    }

    public PagedResult(List<T> items, Integer totalCount, Integer startIndex, Integer limit) {
        this.items = items;
        this.totalCount = totalCount;
        this.startIndex = startIndex;
        this.limit = limit;
    }
}
