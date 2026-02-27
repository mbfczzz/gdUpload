package com.gdupload.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.ArchiveExecuteRequest;
import com.gdupload.dto.ArchiveTmdbItem;
import com.gdupload.dto.MediaInfoDto;
import com.gdupload.entity.ArchiveHistory;
import com.gdupload.mapper.ArchiveHistoryMapper;
import com.gdupload.service.IArchiveService;
import com.gdupload.service.ISmartSearchConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 归档服务实现
 * 支持正则解析 → TMDB匹配 → AI识别 → 人工标记的完整流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl implements IArchiveService {

    private final ArchiveHistoryMapper archiveHistoryMapper;
    private final ISmartSearchConfigService smartSearchConfigService;

    @Value("${app.archive.target-path:/video2}")
    private String archiveTargetPath;

    @Value("${tmdb.api.key:}")
    private String defaultTmdbApiKey;

    @Value("${app.ai.api-key:}")
    private String aiApiKey;

    @Value("${app.ai.model:claude-3-5-sonnet-20241022}")
    private String aiModel;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.rclone.path:/usr/bin/rclone}")
    private String rclonePath;

    @Value("${app.rclone.config-path:/root/.config/rclone/rclone.conf}")
    private String rcloneConfigPath;

    // ─── TMDB ────────────────────────────────────────────────────────────────

    private static final String TMDB_SEARCH_URL =
            "https://api.themoviedb.org/3/search/%s?api_key=%s&query=%s&language=%s&page=1";

    // ─── 正则模式 ─────────────────────────────────────────────────────────────

    /** S01E01 / S01E001 */
    private static final Pattern SE_PATTERN =
            Pattern.compile("S(\\d{1,2})E(\\d{1,4})", Pattern.CASE_INSENSITIVE);

    /** 中文 第01集/第1话 */
    private static final Pattern CN_EP_PATTERN =
            Pattern.compile("第\\s*(\\d{1,4})\\s*[集话話期]");

    /** 日本动漫 - 01 [ 格式 */
    private static final Pattern JP_EP_PATTERN =
            Pattern.compile("(?:^|\\s)[-－]\\s*(\\d{1,4})\\s*(?:\\[|$|\\()");

    /** 分辨率 */
    private static final Pattern RES_PATTERN =
            Pattern.compile("(?i)\\b(4[Kk]|2160[pP]|1080[piP]|720[pP]|480[pP]|576[pP])\\b");

    /** 视频编码 */
    private static final Pattern VIDEO_CODEC_PATTERN =
            Pattern.compile("(?i)\\b(HEVC|H[._]?265|x265|AVC|H[._]?264|x264|AV1|VP9|MPEG[_-]?2)\\b");

    /** 音频编码 */
    private static final Pattern AUDIO_CODEC_PATTERN =
            Pattern.compile("(?i)\\b(AAC|AC3|DTS-HD\\s?MA|DTS-HD|DTSHD|DTS|TrueHD|FLAC|MP3|EAC3|DD\\+?|Opus)\\b");

    /** 片源 */
    private static final Pattern SOURCE_PATTERN =
            Pattern.compile("(?i)\\b(Blu-?Ray|BDRip|BD|WEBRip|WEB-DL|WEB|HDTV|DVDRip|DVD|NF|AMZN|DSNP|ATVP)\\b");

    /** 年份（1980-2029） */
    private static final Pattern YEAR_PATTERN =
            Pattern.compile("\\b(19[89]\\d|20[0-2]\\d)\\b");

    /** 文件扩展名 */
    private static final Pattern EXT_PATTERN =
            Pattern.compile("\\.([a-zA-Z0-9]{2,4})$");

    /** 方括号内容 */
    private static final Pattern BRACKET_PATTERN =
            Pattern.compile("\\[([^\\]]+)\\]");

    // ─── 文件名解析 ───────────────────────────────────────────────────────────

    @Override
    public ArchiveAnalyzeResult analyzeFilename(String filename) {
        ArchiveAnalyzeResult result = new ArchiveAnalyzeResult();
        result.setOriginalFilename(filename);
        result.setAnalyzeSource("regex");

        String work = filename;

        // 1. 提取扩展名
        Matcher extM = EXT_PATTERN.matcher(work);
        if (extM.find()) {
            result.setExtension(extM.group(1).toLowerCase());
            work = work.substring(0, work.lastIndexOf('.'));
        }

        // 2. 提取 S01E1109 格式
        Matcher seM = SE_PATTERN.matcher(work);
        if (seM.find()) {
            result.setSeason(String.format("%02d", Integer.parseInt(seM.group(1))));
            result.setEpisode(seM.group(2));
            result.setTitle(cleanTitle(work.substring(0, seM.start())));
            work = work.substring(seM.end());
            result.setMediaType("tv");
        } else {
            // 3. 尝试中文集数
            Matcher cnM = CN_EP_PATTERN.matcher(work);
            if (cnM.find()) {
                result.setEpisode(String.format("%02d", Integer.parseInt(cnM.group(1))));
                result.setTitle(cleanTitle(work.substring(0, cnM.start())));
                work = work.substring(cnM.end());
                result.setMediaType("tv");
            } else {
                // 4. 尝试日本动漫 - 01 格式
                Matcher jpM = JP_EP_PATTERN.matcher(work);
                if (jpM.find()) {
                    result.setEpisode(String.format("%02d", Integer.parseInt(jpM.group(1))));
                    String beforeEp = work.substring(0, jpM.start()).trim();
                    // 去掉开头的字幕组方括号
                    beforeEp = beforeEp.replaceAll("^\\s*\\[[^\\]]*\\]\\s*", "").trim();
                    result.setTitle(cleanTitle(beforeEp));
                    work = work.substring(jpM.end());
                    result.setMediaType("tv");
                } else {
                    // 5. 当作电影处理
                    result.setMediaType("movie");
                    // 尝试提取年份
                    Matcher yearM = YEAR_PATTERN.matcher(work);
                    if (yearM.find()) {
                        result.setYear(yearM.group(1));
                        result.setTitle(cleanTitle(work.substring(0, yearM.start())));
                    } else {
                        result.setTitle(cleanTitle(work));
                    }
                    work = "";
                }
            }
        }

        // 6. 从剩余部分提取技术参数（同时也从完整文件名中搜索，更可靠）
        String searchScope = work + " " + filename;

        Matcher resM = RES_PATTERN.matcher(searchScope);
        if (resM.find()) {
            String res = resM.group(1);
            result.setResolution(
                    res.equalsIgnoreCase("4K") || res.equalsIgnoreCase("2160p") ? "4K"
                            : res.replaceAll("(?i)i$", "p").toLowerCase()
            );
        }

        Matcher videoM = VIDEO_CODEC_PATTERN.matcher(searchScope);
        if (videoM.find()) {
            result.setVideoCodec(normalizeVideoCodec(videoM.group(1)));
        }

        Matcher audioM = AUDIO_CODEC_PATTERN.matcher(searchScope);
        if (audioM.find()) {
            result.setAudioCodec(audioM.group(1).toUpperCase().replaceAll("\\s", "-"));
        }

        Matcher srcM = SOURCE_PATTERN.matcher(searchScope);
        if (srcM.find()) {
            result.setSource(srcM.group(1));
        }

        // 7. 提取字幕组
        result.setSubtitleGroup(extractSubtitleGroup(filename, result));

        // 8. 生成建议文件名
        result.setSuggestedFilename(buildFilename(result));

        return result;
    }

    private String normalizeVideoCodec(String raw) {
        String upper = raw.toUpperCase().replaceAll("[._]", "");
        if (upper.contains("265") || upper.contains("HEVC")) return "HEVC";
        if (upper.contains("264") || upper.contains("AVC")) return "AVC";
        return raw.toUpperCase();
    }

    /** 已知技术/语言标签（大写，去除空格和分隔符后匹配） */
    private static final java.util.Set<String> TECH_TAGS = new java.util.HashSet<>(java.util.Arrays.asList(
            // 分辨率
            "4K","2160P","1080P","1080I","720P","480P","576P","360P",
            // 视频编码
            "HEVC","H265","H264","X265","X264","AVC","AV1","VP9","VP8","MPEG2",
            // 音频编码
            "AAC","AC3","EAC3","DTS","DTSHD","DTSHDMA","FLAC","MP3","TRUEHD","OPUS","VORBIS",
            // 片源
            "BD","BDRIP","BLURAY","WEBRIP","WEBDL","WEB","HDTV","DVDRIP","DVD",
            // 流媒体
            "NF","AMZN","DSNP","ATVP","HULU","HBO","APPLE",
            // HDR
            "SDR","HDR","HDR10","DOLBY","DV","DOLBYVISION",
            // 语言/字幕语言标签（不是字幕组）
            "CHT","CHS","JPN","ENG","KOR","BIG5","GB","UTF8",
            "SUB","DUB","DUBBED","SUBBED","MULTI","MULTILANG"
    ));

    private boolean isTechTag(String s) {
        if (s == null || s.isEmpty()) return true;
        if (s.matches("\\d+"))           return true;  // 纯数字（集数等）
        if (s.matches("\\d+[xX]\\d+"))  return true;  // 分辨率 1920x1080
        if (s.matches("(19|20)\\d{2}")) return true;  // 年份
        // 去掉空格/点/横线后查表
        String normalized = s.toUpperCase().replaceAll("[\\s._\\-]", "");
        return TECH_TAGS.contains(normalized);
    }

    private String extractSubtitleGroup(String filename, ArchiveAnalyzeResult r) {
        // 去掉扩展名
        String base = filename;
        int dotIdx = base.lastIndexOf('.');
        if (dotIdx > 0) base = base.substring(0, dotIdx);

        // 策略1：文件名开头的方括号 — 动漫最常见格式
        // [ANi] 剧名 - 01 [1080p][HEVC][AAC].mkv → ANi
        Matcher firstBm = Pattern.compile("^\\s*\\[([^\\]]+)\\]").matcher(base);
        if (firstBm.find()) {
            String candidate = firstBm.group(1).trim();
            if (!isTechTag(candidate)) {
                return candidate;
            }
        }

        // 策略2：最后一个 - 后面的内容
        // 剧名.S01E01.1080p.HEVC-SubGroup.mkv → SubGroup
        int lastHyphen = base.lastIndexOf('-');
        if (lastHyphen > 0) {
            String after = base.substring(lastHyphen + 1).trim();
            if (!after.isEmpty() && !isTechTag(after)) {
                return after;
            }
        }

        // 策略3：最后一个方括号（非技术标签）
        // 剧名 第01集 [桜都字幕组].mkv → 桜都字幕组
        Matcher bm = BRACKET_PATTERN.matcher(base);
        String lastBracket = null;
        while (bm.find()) lastBracket = bm.group(1);
        if (lastBracket != null && !isTechTag(lastBracket)) {
            return lastBracket;
        }

        return null;
    }

    private String cleanTitle(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        // 去掉开头字幕组方括号
        raw = raw.replaceAll("^\\s*\\[[^\\]]*\\]\\s*", "");
        // 去掉年份括号
        raw = raw.replaceAll("\\s*[（(]\\d{4}[)）]\\s*", " ");
        // 将点/下划线替换为空格（英文文件名风格）
        raw = raw.replaceAll("[._]", " ");
        // 整理多余空格
        return raw.trim().replaceAll("\\s+", " ");
    }

    private String buildFilename(ArchiveAnalyzeResult r) {
        StringBuilder sb = new StringBuilder();
        if (r.getTitle() != null && !r.getTitle().isEmpty()) sb.append(r.getTitle());

        if (r.getSeason() != null && r.getEpisode() != null) {
            sb.append(" S").append(r.getSeason()).append("E").append(r.getEpisode());
        } else if (r.getEpisode() != null) {
            sb.append(" E").append(r.getEpisode());
        } else if ("movie".equals(r.getMediaType()) && r.getYear() != null) {
            sb.append(" (").append(r.getYear()).append(")");
        }

        if (r.getResolution() != null) sb.append(" ").append(r.getResolution());

        List<String> codecs = new ArrayList<>();
        if (r.getVideoCodec() != null) codecs.add(r.getVideoCodec());
        if (r.getAudioCodec() != null) codecs.add(r.getAudioCodec());
        if (!codecs.isEmpty()) sb.append(".").append(String.join(".", codecs));

        if (r.getSubtitleGroup() != null && !r.getSubtitleGroup().isEmpty()) {
            sb.append("-").append(r.getSubtitleGroup());
        }

        if (r.getExtension() != null) sb.append(".").append(r.getExtension());

        return sb.toString();
    }

    // ─── AI 解析 ──────────────────────────────────────────────────────────────

    @Override
    public ArchiveAnalyzeResult aiAnalyzeFilename(String filename) {
        String key = getEffectiveAiApiKey();
        if (key == null || key.isEmpty()) {
            log.warn("AI API Key 未配置，跳过AI解析");
            ArchiveAnalyzeResult fallback = analyzeFilename(filename);
            fallback.setAnalyzeSource("regex");
            return fallback;
        }

        String provider = getEffectiveAiProvider();
        String model = getEffectiveAiModel();
        String apiUrl = getEffectiveAiApiUrl(provider);
        String prompt = buildAiPrompt(filename);
        try {
            String aiJson = "openai".equals(provider)
                    ? callOpenAiApi(key, model, apiUrl, prompt)
                    : callClaudeApi(key, model, prompt);
            ArchiveAnalyzeResult result = parseAiJson(filename, aiJson);
            result.setAnalyzeSource("ai");
            result.setSuggestedFilename(buildFilename(result));
            log.info("AI解析文件名成功: {} -> title={}", filename, result.getTitle());
            return result;
        } catch (Exception e) {
            log.error("AI解析文件名失败，回退到正则解析: {}", e.getMessage());
            ArchiveAnalyzeResult fallback = analyzeFilename(filename);
            fallback.setAnalyzeSource("regex");
            return fallback;
        }
    }

    private String buildAiPrompt(String filename) {
        return "请分析以下媒体文件名，提取其中的信息。\n" +
                "文件名：" + filename + "\n\n" +
                "请以JSON格式返回以下字段（无法确定的字段返回null）：\n" +
                "{\n" +
                "  \"title\": \"作品中文名称\",\n" +
                "  \"season\": \"季数数字字符串（如01），电影则null\",\n" +
                "  \"episode\": \"集数数字字符串（如1109），电影则null\",\n" +
                "  \"resolution\": \"分辨率（如1080p, 4K, 720p），无则null\",\n" +
                "  \"videoCodec\": \"视频编码（HEVC/AVC/AV1等），无则null\",\n" +
                "  \"audioCodec\": \"音频编码（AAC/DTS/FLAC等），无则null\",\n" +
                "  \"subtitleGroup\": \"字幕组名称，无则null\",\n" +
                "  \"mediaType\": \"tv 或 movie\",\n" +
                "  \"year\": \"年份四位数字字符串，无则null\"\n" +
                "}\n\n" +
                "只返回JSON对象，不要包含任何其他内容或说明。";
    }

    private String callClaudeApi(String apiKey, String model, String prompt) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("max_tokens", 512);
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");
        userMsg.set("content", prompt);
        messages.add(userMsg);
        body.set("messages", messages);

        String response = HttpRequest.post("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .body(body.toString())
                .timeout(30000)
                .execute()
                .body();

        JSONObject resp = JSONUtil.parseObj(response);
        JSONArray content = resp.getJSONArray("content");
        if (content != null && !content.isEmpty()) {
            return content.getJSONObject(0).getStr("text");
        }
        throw new RuntimeException("Claude API返回内容为空");
    }

    private String callOpenAiApi(String apiKey, String model, String baseUrl, String prompt) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("max_tokens", 512);
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");
        userMsg.set("content", prompt);
        messages.add(userMsg);
        body.set("messages", messages);

        String endpoint = (baseUrl != null ? baseUrl : "https://api.openai.com") + "/v1/chat/completions";
        log.info("调用OpenAI接口: {}, model: {}", endpoint, model);
        String response = HttpRequest.post(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .body(body.toString())
                .timeout(30000)
                .execute()
                .body();

        log.info("OpenAI API原始响应: {}", response);
        JSONObject resp = JSONUtil.parseObj(response);
        // 优先检查错误
        if (resp.containsKey("error")) {
            throw new RuntimeException("OpenAI API错误: " + resp.getJSONObject("error").getStr("message"));
        }
        JSONArray choices = resp.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            return choices.getJSONObject(0).getJSONObject("message").getStr("content");
        }
        throw new RuntimeException("OpenAI API返回内容为空，完整响应: " + response);
    }

    private ArchiveAnalyzeResult parseAiJson(String originalFilename, String aiJson) {
        // 提取JSON（防止AI在JSON前后加了多余文字）
        String json = aiJson.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        JSONObject obj = JSONUtil.parseObj(json);
        ArchiveAnalyzeResult result = new ArchiveAnalyzeResult();
        result.setOriginalFilename(originalFilename);
        result.setTitle(obj.getStr("title"));
        result.setSeason(obj.getStr("season"));
        result.setEpisode(obj.getStr("episode"));
        result.setResolution(obj.getStr("resolution"));
        result.setVideoCodec(obj.getStr("videoCodec"));
        result.setAudioCodec(obj.getStr("audioCodec"));
        result.setSubtitleGroup(obj.getStr("subtitleGroup"));
        result.setMediaType(obj.getStr("mediaType"));
        result.setYear(obj.getStr("year"));

        // 从原始文件名补充扩展名
        Matcher extM = EXT_PATTERN.matcher(originalFilename);
        if (extM.find()) result.setExtension(extM.group(1).toLowerCase());

        return result;
    }

    // ─── TMDB搜索 ─────────────────────────────────────────────────────────────

    @Override
    public List<ArchiveTmdbItem> searchTmdb(String title, String year, String type) {
        String apiKey = getEffectiveTmdbApiKey();
        String language = getTmdbLanguage();

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("TMDB API Key 未配置");
            return Collections.emptyList();
        }
        if (title == null || title.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String primaryType = "movie".equals(type) ? "movie" : "tv";
        String fallbackType = "movie".equals(primaryType) ? "tv" : "movie";

        try {
            List<ArchiveTmdbItem> items = doTmdbSearch(title, year, primaryType, apiKey, language);
            if (!items.isEmpty()) {
                return items;
            }
            // 主类型没结果，自动换另一种类型重试
            log.info("TMDB搜索[{}]无结果，自动用[{}]重试: {}", primaryType, fallbackType, title);
            return doTmdbSearch(title, year, fallbackType, apiKey, language);
        } catch (Exception e) {
            log.error("TMDB搜索失败: title={}, error={}", title, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ArchiveTmdbItem> doTmdbSearch(String title, String year, String searchType,
                                                String apiKey, String language) throws Exception {
        String url = String.format(TMDB_SEARCH_URL, searchType, apiKey,
                java.net.URLEncoder.encode(title.trim(), "UTF-8"), language);
        if (year != null && !year.isEmpty()) {
            url += "&year=" + year;
        }
        String body = HttpRequest.get(url).timeout(15000).execute().body();
        JSONObject json = JSONUtil.parseObj(body);
        JSONArray results = json.getJSONArray("results");
        List<ArchiveTmdbItem> items = new ArrayList<>();
        if (results != null) {
            int limit = Math.min(results.size(), 8);
            for (int i = 0; i < limit; i++) {
                items.add(buildTmdbItem(results.getJSONObject(i), searchType));
            }
        }
        return items;
    }

    private ArchiveTmdbItem buildTmdbItem(JSONObject r, String type) {
        ArchiveTmdbItem item = new ArchiveTmdbItem();
        item.setTmdbId(r.getInt("id"));
        item.setType(type);

        String title = r.getStr("title") != null ? r.getStr("title") : r.getStr("name");
        String originalTitle = r.getStr("original_title") != null
                ? r.getStr("original_title") : r.getStr("original_name");
        item.setTitle(title);
        item.setOriginalTitle(originalTitle);
        item.setOriginalLanguage(r.getStr("original_language"));
        item.setOverview(r.getStr("overview"));
        item.setPosterPath(r.getStr("poster_path"));

        // 提取年份
        String dateStr = r.getStr("release_date") != null
                ? r.getStr("release_date") : r.getStr("first_air_date");
        if (dateStr != null && dateStr.length() >= 4) {
            item.setYear(dateStr.substring(0, 4));
        }

        // 类型ID
        JSONArray genreIds = r.getJSONArray("genre_ids");
        if (genreIds != null) {
            List<Integer> genres = new ArrayList<>();
            for (int i = 0; i < genreIds.size(); i++) genres.add(genreIds.getInt(i));
            item.setGenreIds(genres);
        }

        // 建议分类
        item.setSuggestedCategory(suggestCategory(type, item.getOriginalLanguage(), item.getGenreIds()));

        // 建议目录名
        String dirTitle = title != null ? title : originalTitle;
        String dirName = dirTitle != null ? dirTitle : "未知";
        if (item.getYear() != null) dirName += "-" + item.getYear();
        if (item.getTmdbId() != null) dirName += "-[tmdbid=" + item.getTmdbId() + "]";
        item.setSuggestedDirName(dirName);

        return item;
    }

    /**
     * 根据媒体类型、原始语言、类型ID推断分类
     *
     * TMDB Genre ID 参考：
     * 16=动画, 99=纪录片, 10764=真人秀, 10767=脱口秀
     */
    private String suggestCategory(String type, String lang, List<Integer> genreIds) {
        boolean isAnimation  = genreIds != null && genreIds.contains(16);
        boolean isDocumentary = genreIds != null && genreIds.contains(99);
        boolean isVariety    = genreIds != null
                && (genreIds.contains(10764) || genreIds.contains(10767));

        if (isDocumentary) return "纪录片";
        if (isVariety)     return "综艺";

        if (isAnimation) {
            return "movie".equals(type) ? "动画电影" : "动画剧集";
        }

        if ("movie".equals(type)) {
            if ("zh".equals(lang) || "cn".equals(lang)) return "华语电影";
            return "外语电影";
        } else {
            if ("zh".equals(lang) || "cn".equals(lang)) return "国产剧集";
            if ("ja".equals(lang) || "ko".equals(lang)) return "日韩剧集";
            if ("en".equals(lang)) return "欧美剧集";
            return "其他剧集";
        }
    }

    // ─── 执行归档 ─────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> executeArchive(ArchiveExecuteRequest req) {
        Map<String, Object> result = new HashMap<>();
        ArchiveHistory history = new ArchiveHistory();

        String originalFilename = Paths.get(req.getOriginalPath()).getFileName().toString();
        history.setOriginalPath(req.getOriginalPath());
        history.setOriginalFilename(originalFilename);
        history.setTmdbId(req.getTmdbId());
        history.setTmdbTitle(req.getTmdbTitle());
        history.setCategory(req.getCategory());
        history.setSeasonDir(req.getSeasonDir());
        history.setProcessMethod(req.getProcessMethod() != null ? req.getProcessMethod() : "manual");

        try {
            // 构建目标目录
            StringBuilder targetDir = new StringBuilder(archiveTargetPath);
            if (notBlank(req.getCategory()))  targetDir.append("/").append(req.getCategory());
            if (notBlank(req.getDirName()))   targetDir.append("/").append(req.getDirName());
            if (notBlank(req.getSeasonDir())) targetDir.append("/").append(req.getSeasonDir());

            String newFilename = req.getNewFilename();
            String targetPath  = targetDir + "/" + newFilename;

            history.setNewPath(targetPath);
            history.setNewFilename(newFilename);

            if (notBlank(req.getRcloneConfigName())) {
                // 云盘文件：用 rclone moveto 移动
                // archiveTargetPath 可能有前导斜杠（如 /video2），去掉作为云盘路径
                String cloudTarget = targetDir.toString().replaceAll("^/+", "");
                String remote = req.getRcloneConfigName();
                String srcRemote  = remote + ":" + req.getOriginalPath();
                String destRemote = remote + ":" + cloudTarget + "/" + newFilename;
                log.info("归档（云盘）: {} → {}", srcRemote, destRemote);
                runRcloneMoveto(srcRemote, destRemote);
            } else {
                // 本地文件：直接 Files.move
                Files.createDirectories(Paths.get(targetDir.toString()));
                Path src  = Paths.get(req.getOriginalPath());
                Path dest = Paths.get(targetPath);
                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("归档成功: {} → {}", req.getOriginalPath(), targetPath);
            history.setStatus("success");

            result.put("success", true);
            result.put("targetPath", targetPath);
            result.put("message", "归档成功");

        } catch (Exception e) {
            log.error("归档失败: path={}, error={}", req.getOriginalPath(), e.getMessage(), e);
            history.setStatus("failed");
            history.setFailReason(e.getMessage());

            result.put("success", false);
            result.put("message", "归档失败: " + e.getMessage());
        }

        archiveHistoryMapper.insert(history);
        result.put("historyId", history.getId());
        return result;
    }

    private void runRcloneMoveto(String src, String dest) throws Exception {
        String cmd = String.format(
            "%s moveto --config '%s' '%s' '%s'",
            rclonePath, rcloneConfigPath, src, dest
        );
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = readOutput(process);
        boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("rclone moveto 超时");
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("rclone moveto 失败(exit=" + exitCode + "): " + output);
        }
    }

    // ─── 标记人工处理 ─────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> markManual(String originalPath, String originalFilename, String remark) {
        ArchiveHistory history = new ArchiveHistory();
        history.setOriginalPath(originalPath);
        history.setOriginalFilename(originalFilename != null ? originalFilename
                : Paths.get(originalPath).getFileName().toString());
        history.setStatus("manual_required");
        history.setProcessMethod("manual");
        history.setRemark(remark);
        archiveHistoryMapper.insert(history);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("historyId", history.getId());
        result.put("message", "已标记为需要人工处理");
        return result;
    }

    // ─── 分类列表 ─────────────────────────────────────────────────────────────

    @Override
    public List<String> getCategories() {
        return Arrays.asList(
                "动画电影", "动画剧集", "儿童剧集", "国产动漫", "国产剧集",
                "日韩剧集", "华语电影", "纪录片", "欧美剧集", "外语电影",
                "其他剧集", "综艺", "合集", "演唱会"
        );
    }

    // ─── 历史记录 ─────────────────────────────────────────────────────────────

    @Override
    public IPage<ArchiveHistory> getHistory(Page<ArchiveHistory> page, String status) {
        LambdaQueryWrapper<ArchiveHistory> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ArchiveHistory::getStatus, status);
        }
        wrapper.orderByDesc(ArchiveHistory::getCreateTime);
        return archiveHistoryMapper.selectPage(page, wrapper);
    }

    @Override
    public void updateRemark(Long historyId, String remark) {
        ArchiveHistory history = archiveHistoryMapper.selectById(historyId);
        if (history != null) {
            history.setRemark(remark);
            archiveHistoryMapper.updateById(history);
        }
    }

    // ─── ffprobe 媒体信息 ──────────────────────────────────────────────────────

    @Override
    public MediaInfoDto getMediaInfo(String filePath, String rcloneConfigName) {
        if (filePath == null || filePath.trim().isEmpty()) return null;
        log.info("getMediaInfo: filePath={}, rcloneConfigName={}", filePath, rcloneConfigName);

        MediaInfoDto result;
        if (rcloneConfigName != null && !rcloneConfigName.trim().isEmpty()) {
            result = getMediaInfoViaRclone(rcloneConfigName, filePath);
        } else {
            result = getMediaInfoLocal(filePath);
        }

        if (result != null) {
            log.info("getMediaInfo 成功: resolution={}, videoCodec={}, audioCodec={}", result.getResolution(), result.getVideoCodec(), result.getAudioCodec());
        } else {
            log.warn("getMediaInfo 返回空（ffprobe未安装或文件无法读取）: filePath={}", filePath);
        }
        return result;
    }

    /** 本地文件：直接运行 ffprobe */
    private MediaInfoDto getMediaInfoLocal(String filePath) {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("getMediaInfo: 本地文件不存在 {}", filePath);
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "quiet",
                "-print_format", "json", "-show_streams",
                filePath
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();
            String json = readOutput(process);
            process.waitFor();
            return json.isEmpty() ? null : parseMediaInfo(json);
        } catch (Exception e) {
            log.warn("ffprobe 本地执行失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 云端文件：rclone cat | head -c 15MB | ffprobe
     * 读取文件头部 15MB，足以拿到 MKV/MP4(faststart) 的编解码信息
     */
    private MediaInfoDto getMediaInfoViaRclone(String configName, String cloudPath) {
        try {
            // bash -c 方式串联管道
            String cmd = String.format(
                "%s cat --config '%s' '%s:%s' 2>/dev/null | head -c 15728640 | " +
                "ffprobe -v quiet -print_format json -show_streams " +
                "-probesize 15728640 -analyzeduration 0 -i pipe:0 2>/dev/null",
                rclonePath, rcloneConfigPath, configName, cloudPath
            );
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            String json = readOutput(process);
            process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (process.isAlive()) process.destroyForcibly();
            return json.isEmpty() ? null : parseMediaInfo(json);
        } catch (Exception e) {
            log.warn("ffprobe via rclone 失败: configName={}, path={}, err={}", configName, cloudPath, e.getMessage());
            return null;
        }
    }

    private String readOutput(Process process) throws Exception {
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
        );
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString().trim();
    }

    private MediaInfoDto parseMediaInfo(String json) {
        try {
            JSONObject root = JSONUtil.parseObj(json);
            JSONArray streams = root.getJSONArray("streams");
            if (streams == null || streams.isEmpty()) return null;

            MediaInfoDto dto = new MediaInfoDto();

            for (int i = 0; i < streams.size(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                String codecType = stream.getStr("codec_type");
                String codecName = stream.getStr("codec_name", "");

                if ("video".equals(codecType) && dto.getVideoCodec() == null) {
                    dto.setVideoCodec(normalizeVideoCodecName(codecName));
                    Integer w = stream.getInt("width");
                    Integer h = stream.getInt("height");
                    if (w != null && h != null) {
                        dto.setWidth(w);
                        dto.setHeight(h);
                        dto.setResolution(widthToResolution(w));
                    }
                }
                if ("audio".equals(codecType) && dto.getAudioCodec() == null) {
                    dto.setAudioCodec(normalizeAudioCodecName(codecName, stream));
                }
            }

            return (dto.getVideoCodec() != null || dto.getAudioCodec() != null) ? dto : null;
        } catch (Exception e) {
            log.error("解析 ffprobe JSON 失败", e);
            return null;
        }
    }

    private String normalizeVideoCodecName(String raw) {
        if (raw == null) return null;
        switch (raw.toLowerCase()) {
            case "hevc": return "HEVC";
            case "h264": return "AVC";
            case "av1":  return "AV1";
            case "vp9":  return "VP9";
            case "vp8":  return "VP8";
            case "mpeg2video": return "MPEG-2";
            default: return raw.toUpperCase();
        }
    }

    private String normalizeAudioCodecName(String raw, JSONObject stream) {
        if (raw == null) return null;
        switch (raw.toLowerCase()) {
            case "aac":    return "AAC";
            case "ac3":    return "AC3";
            case "eac3":   return "EAC3";
            case "truehd": return "TrueHD";
            case "flac":   return "FLAC";
            case "mp3":    return "MP3";
            case "opus":   return "Opus";
            case "dts": {
                // DTS 子类型通过 profile 区分
                String profile = stream.getStr("profile", "");
                if (profile.contains("MA")) return "DTS-HD-MA";
                if (profile.contains("HRA")) return "DTS-HD";
                return "DTS";
            }
            case "vorbis": return "Vorbis";
            default: return raw.toUpperCase();
        }
    }

    private String widthToResolution(int width) {
        if (width >= 3840) return "4K";
        if (width >= 1920) return "1080p";
        if (width >= 1280) return "720p";
        if (width >= 854)  return "480p";
        if (width >= 640)  return "360p";
        return width + "p";
    }

    // ─── 内部工具 ─────────────────────────────────────────────────────────────

    private String getEffectiveTmdbApiKey() {
        try {
            Map<String, Object> cfg = smartSearchConfigService.getFullConfig("default");
            if (cfg != null && cfg.containsKey("tmdbApiKey")) {
                String key = (String) cfg.get("tmdbApiKey");
                if (key != null && !key.isEmpty()) return key;
            }
        } catch (Exception ignored) {}
        return defaultTmdbApiKey;
    }

    private String getEffectiveAiApiKey() {
        try {
            Map<String, Object> cfg = smartSearchConfigService.getFullConfig("default");
            if (cfg != null && cfg.containsKey("aiApiKey")) {
                String key = (String) cfg.get("aiApiKey");
                if (key != null && !key.isEmpty()) return key;
            }
        } catch (Exception ignored) {}
        return aiApiKey;
    }

    private String getEffectiveAiProvider() {
        try {
            Map<String, Object> cfg = smartSearchConfigService.getFullConfig("default");
            if (cfg != null && cfg.containsKey("aiProvider")) {
                String provider = (String) cfg.get("aiProvider");
                if (provider != null && !provider.isEmpty()) return provider;
            }
        } catch (Exception ignored) {}
        return "claude"; // 默认 Claude
    }

    private String getEffectiveAiModel() {
        try {
            Map<String, Object> cfg = smartSearchConfigService.getFullConfig("default");
            if (cfg != null && cfg.containsKey("aiModel")) {
                String model = (String) cfg.get("aiModel");
                if (model != null && !model.isEmpty()) return model;
            }
        } catch (Exception ignored) {}
        return aiModel;
    }

    private String getEffectiveAiApiUrl(String provider) {
        try {
            Map<String, Object> cfg = smartSearchConfigService.getFullConfig("default");
            if (cfg != null && cfg.containsKey("aiApiUrl")) {
                String url = (String) cfg.get("aiApiUrl");
                if (url != null && !url.trim().isEmpty()) {
                    // 去掉末尾斜杠
                    return url.trim().replaceAll("/+$", "");
                }
            }
        } catch (Exception ignored) {}
        // 默认地址
        return "openai".equals(provider) ? "https://api.openai.com" : null;
    }

    private String getTmdbLanguage() {
        try {
            Map<String, Object> cfg = smartSearchConfigService.getFullConfig("default");
            if (cfg != null && cfg.containsKey("tmdbLanguage")) {
                String lang = (String) cfg.get("tmdbLanguage");
                if (lang != null && !lang.isEmpty()) return lang;
            }
        } catch (Exception ignored) {}
        return "zh-CN";
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
