package com.gdupload.dto;

import lombok.Data;

/**
 * Emby类型/标签信息
 */
@Data
public class EmbyGenre {

    /**
     * ID
     */
    private String id;

    /**
     * 名称
     */
    private String name;

    /**
     * 媒体项数量
     */
    private Integer itemCount;

    /**
     * 类型（Genre, Tag, Studio等）
     */
    private String type;
}
