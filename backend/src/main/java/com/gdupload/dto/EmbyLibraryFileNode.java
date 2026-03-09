package com.gdupload.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emby库检查 - 文件树节点
 */
@Data
public class EmbyLibraryFileNode {

    /** 文件/目录名 */
    private String name;

    /** 完整相对路径 */
    private String path;

    /** 是否为目录 */
    @JsonProperty("dir")
    private boolean isDir;

    /**
     * 节点类型（用于前端图标展示）
     * category  - 分类目录（动漫/电视剧/电影等）
     * show      - 作品目录（含tmdbid的那层）
     * season    - Season目录
     * movie     - 电影文件
     * episode   - 剧集文件
     * unknown   - 无法识别
     */
    private String fileType;

    /** 文件大小（字节） */
    private Long size;

    /** 修改时间 */
    private String modTime;

    /** 本节点的问题列表 */
    private List<Issue> issues = new ArrayList<>();

    /** 子节点（目录时有值） */
    private List<EmbyLibraryFileNode> children;

    /** 子树各严重级别问题数量汇总：{ "error": 5, "warning": 10, "info": 3 } */
    private Map<String, Integer> issueStats = new HashMap<>();

    // ─── 内嵌问题类 ──────────────────────────────

    @Data
    public static class Issue {
        /**
         * 问题编码，枚举值：
         * MISSING_SEASON, MISSING_EPISODE, CONTAINS_NULL,
         * INVALID_EXTENSION, EMPTY_FILENAME,
         * MISSING_TMDBID, MISSING_YEAR, MISSING_RESOLUTION,
         * SPECIAL_CHARACTERS, INCONSISTENT_NAMING,
         * MISSING_CODEC_INFO, MISSING_AUDIO_CODEC, NO_EPISODE_TITLE
         */
        private String code;

        /** 严重程度：error / warning / info */
        private String severity;

        /** 人类可读描述 */
        private String message;

        /** 问题分类（用于前端筛选） */
        private String category;

        public Issue() {}

        public Issue(String code, String severity, String message, String category) {
            this.code = code;
            this.severity = severity;
            this.message = message;
            this.category = category;
        }
    }
}
