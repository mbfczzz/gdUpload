package com.gdupload.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdupload.dto.EmbyLibraryFileNode;
import com.gdupload.dto.EmbyLibraryFileNode.Issue;
import com.gdupload.dto.GdFileItem;
import com.gdupload.service.IEmbyLibraryInspectService;
import com.gdupload.util.RcloneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Emby库检查服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbyLibraryInspectServiceImpl implements IEmbyLibraryInspectService {

    private final RcloneUtil rcloneUtil;
    private final ObjectMapper objectMapper;

    // ── 正则模式 ────────────────────────────────────────────────────
    private static final Pattern TMDBID_PATTERN = Pattern.compile("(?i)\\[tmdbid[=\\-](\\d+)]");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\(((?:19|20)\\d{2})\\)");
    private static final Pattern SE_PATTERN = Pattern.compile("(?i)S(\\d{1,2})E(\\d{1,4})");
    private static final Pattern SEASON_DIR_PATTERN = Pattern.compile("(?i)^Season\\s+(\\d+)$");
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("(?i)(4K|2160p|1080p|720p|480p|360p)");
    private static final Pattern VIDEO_CODEC_PATTERN = Pattern.compile("(?i)(HEVC|H\\.?265|x265|H\\.?264|x264|AV1|VP9|MPEG4)");
    private static final Pattern AUDIO_CODEC_PATTERN = Pattern.compile("(?i)(AAC|FLAC|DTS|AC3|EAC3|TrueHD|Atmos|OPUS|MP3|DDP)");
    private static final Pattern NULL_PATTERN = Pattern.compile("(?i)\\bnull\\b");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[<>:\"|?*]");
    private static final Pattern EP_ONLY_PATTERN = Pattern.compile("(?i)E(\\d{1,4})(?!\\d)");

    /** 已知的 Emby 媒体分类目录名 */
    private static final Set<String> KNOWN_CATEGORIES = new HashSet<>(Arrays.asList(
            "日语动漫", "国产动漫", "韩国动漫", "欧美动漫",
            "国产剧", "港台剧", "日剧", "韩剧", "欧美剧",
            "华语电影", "日本电影", "韩国电影", "欧美电影",
            "纪录片", "综艺", "动漫电影"
    ));

    /** 电影分类 */
    private static final Set<String> MOVIE_CATEGORIES = new HashSet<>(Arrays.asList(
            "华语电影", "日本电影", "韩国电影", "欧美电影", "动漫电影"
    ));

    // ── 公开方法 ────────────────────────────────────────────────────

    @Override
    public List<EmbyLibraryFileNode> inspectLibrary(String rcloneRemote, String path) {
        log.info("开始Emby库检查: remote={}, path={}", rcloneRemote, path);
        long start = System.currentTimeMillis();

        List<GdFileItem> topItems = listDirectory(rcloneRemote, path);
        List<EmbyLibraryFileNode> tree = new ArrayList<>();

        for (GdFileItem item : topItems) {
            String fullPath = buildPath(path, item.getName());
            EmbyLibraryFileNode node = buildNode(rcloneRemote, item, fullPath, 0, null);
            tree.add(node);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Emby库检查完成: remote={}, path={}, 耗时={}ms, 顶层节点={}", rcloneRemote, path, elapsed, tree.size());
        return tree;
    }

    @Override
    public Map<String, Object> getInspectSummary(String rcloneRemote, String path) {
        List<EmbyLibraryFileNode> tree = inspectLibrary(rcloneRemote, path);

        int[] counts = new int[5]; // totalFiles, totalDirs, error, warning, info
        Map<String, Integer> categoryStats = new LinkedHashMap<>();
        collectSummary(tree, counts, categoryStats);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalFiles", counts[0]);
        summary.put("totalDirs", counts[1]);
        summary.put("errorCount", counts[2]);
        summary.put("warningCount", counts[3]);
        summary.put("infoCount", counts[4]);
        summary.put("categoryStats", categoryStats);
        return summary;
    }

    // ── 核心递归构建 ──────────────────────────────────────────────

    /**
     * 递归构建文件��节点
     *
     * @param depth         当前深度（0=分类层, 1=作品层, 2=Season层, 3=文件层）
     * @param parentCategory 父级分类名（用于判断是电影还是电视剧）
     */
    private EmbyLibraryFileNode buildNode(String rcloneRemote, GdFileItem item,
                                          String fullPath, int depth, String parentCategory) {
        EmbyLibraryFileNode node = new EmbyLibraryFileNode();
        node.setName(item.getName());
        node.setPath(fullPath);
        node.setDir(Boolean.TRUE.equals(item.getIsDir()));
        node.setSize(item.getSize());
        node.setModTime(item.getModTime());

        if (node.isDir()) {
            // ── 判断目录类型 ──
            String category = parentCategory;
            if (depth == 0) {
                // 第0层：分类目录
                node.setFileType("category");
                category = item.getName();
                validateCategoryDir(node);
            } else if (depth == 1) {
                // 第1层：作品目录
                node.setFileType("show");
                validateShowDir(node, parentCategory);
            } else if (SEASON_DIR_PATTERN.matcher(item.getName()).matches()) {
                // Season 目录
                node.setFileType("season");
            } else {
                node.setFileType("show"); // 可能是子目录
                validateShowDir(node, parentCategory);
            }

            // ── 递归子节点 ──
            List<GdFileItem> children = listDirectory(rcloneRemote, fullPath);
            List<EmbyLibraryFileNode> childNodes = new ArrayList<>();
            for (GdFileItem child : children) {
                String childPath = buildPath(fullPath, child.getName());
                EmbyLibraryFileNode childNode = buildNode(rcloneRemote, child, childPath, depth + 1, category);
                childNodes.add(childNode);
            }
            node.setChildren(childNodes);

            // 检查同目录下命名一致性（仅对作品目录和Season目录检查）
            if ("show".equals(node.getFileType()) || "season".equals(node.getFileType())) {
                validateNamingConsistency(node, childNodes);
            }

            // ── 汇总子树问题统计 ──
            aggregateIssueStats(node);
        } else {
            // ── 文件节点 ──
            boolean isMovieCategory = parentCategory != null && MOVIE_CATEGORIES.contains(parentCategory);
            String name = item.getName();

            if (isMovieCategory) {
                node.setFileType("movie");
            } else if (SE_PATTERN.matcher(name).find() || EP_ONLY_PATTERN.matcher(name).find()) {
                node.setFileType("episode");
            } else {
                node.setFileType("unknown");
            }

            validateFile(node, isMovieCategory);
        }

        return node;
    }

    // ── 验证规则 ─────────────────────────────────────────────────

    /** 验证分类目录 */
    private void validateCategoryDir(EmbyLibraryFileNode node) {
        if (!KNOWN_CATEGORIES.contains(node.getName())) {
            node.getIssues().add(new Issue(
                    "UNKNOWN_CATEGORY", "info",
                    "非标准分类目录名: " + node.getName() + "，可能影响Emby媒体库分类",
                    "目录规范"
            ));
        }
    }

    /** 验证作品目录（如 名侦探柯南 (1996) [tmdbid=30984]） */
    private void validateShowDir(EmbyLibraryFileNode node, String parentCategory) {
        String name = node.getName();

        // 检查 null
        if (NULL_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "CONTAINS_NULL", "error",
                    "目录名包含 \"null\"，可能导致Emby无法正确识别: " + name,
                    "包含null"
            ));
        }

        // 检查 tmdbid
        if (!TMDBID_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "MISSING_TMDBID", "warning",
                    "目录名缺少 [tmdbid=xxx]，Emby可能无法精确匹配元数据",
                    "缺少TMDB ID"
            ));
        }

        // 检查年份
        if (!YEAR_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "MISSING_YEAR", "warning",
                    "目录名缺少年份 (2024)，可能影响Emby搜索匹配",
                    "缺少年份"
            ));
        }

        // 检查空目录名
        if (name.trim().isEmpty()) {
            node.getIssues().add(new Issue(
                    "EMPTY_FILENAME", "error",
                    "目录名为空",
                    "文件名为空"
            ));
        }

        // 检查特殊字符
        if (SPECIAL_CHAR_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "SPECIAL_CHARACTERS", "warning",
                    "目录名包含特殊字符 (<>:\"|?*)，可能导致兼容性问题",
                    "特���字符"
            ));
        }
    }

    /** 验证文件（剧集或电影文件） */
    private void validateFile(EmbyLibraryFileNode node, boolean isMovie) {
        String name = node.getName();

        // ── error 级别 ──

        // 空文件名
        if (name.trim().isEmpty()) {
            node.getIssues().add(new Issue(
                    "EMPTY_FILENAME", "error",
                    "文件名为空",
                    "文件名为空"
            ));
            return; // 后续检查无意义
        }

        // 包含 null
        if (NULL_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "CONTAINS_NULL", "error",
                    "文件名包含 \"null\"，通常是解析异常导致，影响Emby识别: " + name,
                    "包含null"
            ));
        }

        // 扩展名检查
        String lowerName = name.toLowerCase();
        if (!lowerName.endsWith(".strm") && !lowerName.endsWith(".nfo")
                && !lowerName.endsWith(".jpg") && !lowerName.endsWith(".png")) {
            // 在 strm 库中出现非 strm/nfo/图片 文件是异常的
            node.getIssues().add(new Issue(
                    "INVALID_EXTENSION", "warning",
                    "非标准Emby库文件类型: " + name,
                    "扩展名异常"
            ));
        }

        // 仅对 .strm 文件做剧集命名检查
        if (!lowerName.endsWith(".strm")) {
            return;
        }

        if (!isMovie) {
            // 电视剧集：检查季号和集号
            Matcher seMatcher = SE_PATTERN.matcher(name);
            if (!seMatcher.find()) {
                // 没有 S01E01 格式
                boolean hasEpOnly = EP_ONLY_PATTERN.matcher(name).find();
                if (!hasEpOnly) {
                    node.getIssues().add(new Issue(
                            "MISSING_EPISODE", "error",
                            "文件名缺少集号 (如 S01E01 或 E01)，Emby无法识别为剧集",
                            "缺少集号"
                    ));
                }
                // 有 E01 但没有 S01
                if (hasEpOnly) {
                    node.getIssues().add(new Issue(
                            "MISSING_SEASON", "error",
                            "文件名缺少季号 (如 S01)，Emby无法正确分配到季",
                            "缺少季号"
                    ));
                }
            } else {
                // 有 S01E01，检查集标题
                String afterSE = name.substring(seMatcher.end());
                // 去掉扩展名和编码信息后检查是否有标题
                String cleaned = afterSE.replaceAll("(?i)\\.(strm|mkv|mp4)$", "")
                        .replaceAll("(?i)(4K|2160p|1080p|720p|480p)", "")
                        .replaceAll("(?i)(HEVC|H\\.?265|x265|H\\.?264|x264)", "")
                        .replaceAll("(?i)(AAC|FLAC|DTS|AC3|EAC3)", "")
                        .replaceAll("[.\\s]+", " ").trim();
                if (cleaned.isEmpty()) {
                    node.getIssues().add(new Issue(
                            "NO_EPISODE_TITLE", "info",
                            "文件名缺少集标题（S01E01后无标题），建议补充以便快速识别",
                            "缺少集标题"
                    ));
                }
            }
        }

        // ── warning 级别 ──

        // 缺少分辨率
        if (!RESOLUTION_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "MISSING_RESOLUTION", "warning",
                    "文件名缺少分辨率信息 (1080p/4K等)，不影响播放但影响筛选",
                    "缺少分辨率"
            ));
        }

        // 特殊字符
        // 文件名中去掉 [] () 中的内容后检查
        String nameForSpecial = name.replaceAll("\\[.*?]", "").replaceAll("\\(.*?\\)", "");
        if (SPECIAL_CHAR_PATTERN.matcher(nameForSpecial).find()) {
            node.getIssues().add(new Issue(
                    "SPECIAL_CHARACTERS", "warning",
                    "文件名包含特殊字符 (<>:\"|?*)，可能导致兼容性问题",
                    "特殊字符"
            ));
        }

        // ── info 级别 ──

        // 缺少视频编码
        if (!VIDEO_CODEC_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "MISSING_CODEC_INFO", "info",
                    "文件名缺少视频编码信息 (HEVC/x264等)",
                    "缺少编码信息"
            ));
        }

        // 缺少音频编码
        if (!AUDIO_CODEC_PATTERN.matcher(name).find()) {
            node.getIssues().add(new Issue(
                    "MISSING_AUDIO_CODEC", "info",
                    "文件名缺少音频编码信息 (AAC/FLAC等)",
                    "缺少编码信息"
            ));
        }
    }

    /** 检查同目录下文件命名一致性 */
    private void validateNamingConsistency(EmbyLibraryFileNode parentNode, List<EmbyLibraryFileNode> childNodes) {
        List<EmbyLibraryFileNode> strmFiles = childNodes.stream()
                .filter(n -> !n.isDir() && n.getName().toLowerCase().endsWith(".strm"))
                .collect(Collectors.toList());

        if (strmFiles.size() < 2) return;

        // 检查是否混用 S01E01 和 E01 格式
        long hasSE = strmFiles.stream().filter(n -> SE_PATTERN.matcher(n.getName()).find()).count();
        long hasEOnly = strmFiles.stream().filter(n -> !SE_PATTERN.matcher(n.getName()).find()
                && EP_ONLY_PATTERN.matcher(n.getName()).find()).count();

        if (hasSE > 0 && hasEOnly > 0) {
            parentNode.getIssues().add(new Issue(
                    "INCONSISTENT_NAMING", "warning",
                    String.format("目录内文件命名不统一：%d个使用S01E01格式，%d个仅使用E01格式", hasSE, hasEOnly),
                    "命名不统一"
            ));
        }
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    /** 列出指定目录下的文件 */
    private List<GdFileItem> listDirectory(String rcloneRemote, String path) {
        try {
            String json = rcloneUtil.listJson(rcloneRemote, path);
            List<GdFileItem> items = objectMapper.readValue(json, new TypeReference<List<GdFileItem>>() {});
            // 排序：目录在前，名称升序
            items.sort((a, b) -> {
                boolean aDir = Boolean.TRUE.equals(a.getIsDir());
                boolean bDir = Boolean.TRUE.equals(b.getIsDir());
                if (aDir != bDir) return aDir ? -1 : 1;
                return String.CASE_INSENSITIVE_ORDER.compare(
                        a.getName() != null ? a.getName() : "",
                        b.getName() != null ? b.getName() : "");
            });
            return items;
        } catch (Exception e) {
            log.error("列目录失败: remote={}, path={}, error={}", rcloneRemote, path, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 构建路径 */
    private String buildPath(String parent, String child) {
        if (StrUtil.isBlank(parent) || "/".equals(parent)) return child;
        return parent.endsWith("/") ? parent + child : parent + "/" + child;
    }

    /** 递归汇总子树 issue 统计 */
    private void aggregateIssueStats(EmbyLibraryFileNode node) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("error", 0);
        stats.put("warning", 0);
        stats.put("info", 0);

        // 自身的 issues
        for (Issue issue : node.getIssues()) {
            stats.merge(issue.getSeverity(), 1, Integer::sum);
        }

        // 子节点的统计
        if (node.getChildren() != null) {
            for (EmbyLibraryFileNode child : node.getChildren()) {
                for (Issue issue : child.getIssues()) {
                    stats.merge(issue.getSeverity(), 1, Integer::sum);
                }
                if (child.getIssueStats() != null) {
                    for (Map.Entry<String, Integer> entry : child.getIssueStats().entrySet()) {
                        stats.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    }
                }
            }
        }

        node.setIssueStats(stats);
    }

    /** 递归收集汇总信息 */
    private void collectSummary(List<EmbyLibraryFileNode> nodes, int[] counts, Map<String, Integer> categoryStats) {
        for (EmbyLibraryFileNode node : nodes) {
            if (node.isDir()) {
                counts[1]++;
            } else {
                counts[0]++;
            }
            for (Issue issue : node.getIssues()) {
                switch (issue.getSeverity()) {
                    case "error": counts[2]++; break;
                    case "warning": counts[3]++; break;
                    case "info": counts[4]++; break;
                }
                categoryStats.merge(issue.getCategory(), 1, Integer::sum);
            }
            if (node.getChildren() != null) {
                collectSummary(node.getChildren(), counts, categoryStats);
            }
        }
    }
}
