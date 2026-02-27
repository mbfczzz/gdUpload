package com.gdupload.dto;

import lombok.Data;

/**
 * ffprobe 探测到的媒体技术信息
 */
@Data
public class MediaInfoDto {

    /** 视频编码，如 HEVC / AVC / AV1 */
    private String videoCodec;

    /** 音频编码，如 AAC / AC3 / FLAC */
    private String audioCodec;

    /** 分辨率标准名，如 4K / 1080p / 720p */
    private String resolution;

    /** 原始宽度（像素） */
    private Integer width;

    /** 原始高度（像素） */
    private Integer height;
}
