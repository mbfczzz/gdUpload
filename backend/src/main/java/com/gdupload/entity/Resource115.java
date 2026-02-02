package com.gdupload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 115资源实体
 */
@Data
@TableName("115_resource")
public class Resource115 {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 资源名称
     */
    private String name;

    /**
     * TMDB ID
     */
    private String tmdbId;

    /**
     * 资源类型
     */
    private String type;

    /**
     * 资源大小
     */
    private String size;

    /**
     * 115分享链接
     */
    private String url;

    /**
     * 访问码
     */
    private String code;
}
