package com.gdupload.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * GD文件条目DTO（对应 rclone lsjson 输出字段）
 */
@Data
public class GdFileItem {

    @JsonAlias("Name")
    private String name;

    @JsonAlias("Path")
    private String path;

    @JsonAlias("IsDir")
    private Boolean isDir;

    @JsonAlias("Size")
    private Long size;

    @JsonAlias("ModTime")
    private String modTime;

    @JsonAlias("MimeType")
    private String mimeType;
}
