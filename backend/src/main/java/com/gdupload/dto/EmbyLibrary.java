package com.gdupload.dto;

import lombok.Data;
import java.util.List;

/**
 * Emby媒体库信息
 */
@Data
public class EmbyLibrary {

    /**
     * 媒体库ID
     */
    private String id;

    /**
     * 媒体库名称
     */
    private String name;

    /**
     * 媒体库类型（movies, tvshows, music等）
     */
    private String collectionType;

    /**
     * 媒体库路径
     */
    private List<String> locations;

    /**
     * 媒体项数量
     */
    private Integer itemCount;

    /**
     * 创建时间
     */
    private String dateCreated;

    /**
     * 最后修改时间
     */
    private String dateModified;

    /**
     * 服务器ID
     */
    private String serverId;
}
