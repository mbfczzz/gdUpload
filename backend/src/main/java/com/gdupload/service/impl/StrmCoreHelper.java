package com.gdupload.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gdupload.dto.ArchiveAnalyzeResult;
import com.gdupload.dto.ArchiveTmdbItem;
import com.gdupload.service.IArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * STRM 核心处理帮助组件
 *
 * 封装 STRM 生成、NFO 写入、图片下载、GD 文件列举等可复用逻辑，
 * 供 StrmServiceImpl（一次性生成）和 StrmWatchServiceImpl（监控同步）共享。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrmCoreHelper {

    private final IArchiveService archiveService;

    @Value("${app.rclone.path:/usr/bin/rclone}")
    private String rclonePath;

    @Value("${app.rclone.config-path:/root/.config/rclone/rclone.conf}")
    private String rcloneConfigPath;

    // append_to_response 一次请求同时拿演员表和分级，不增加额外 API 调用
    private static final String TMDB_DETAILS_TV_URL    = "https://api.themoviedb.org/3/tv/%d?api_key=%s&language=%s&append_to_response=credits,content_ratings";
    private static final String TMDB_DETAILS_MOVIE_URL = "https://api.themoviedb.org/3/movie/%d?api_key=%s&language=%s&append_to_response=credits,release_dates";
    private static final String TMDB_SEASON_URL        = "https://api.themoviedb.org/3/tv/%d/season/%d?api_key=%s&language=%s";
    static final String TMDB_IMG_PROFILE = "https://image.tmdb.org/t/p/w185";
    static final String TMDB_IMG_W500   = "https://image.tmdb.org/t/p/w500";
    static final String TMDB_IMG_ORIG   = "https://image.tmdb.org/t/p/original";

    static final Set<String> VIDEO_EXTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "mkv", "mp4", "avi", "ts", "m2ts", "mov", "wmv", "flv", "rmvb", "rm", "m4v"
    )));

    /** 图片下载专用线程池（32线程，守护，不阻塞主处理循环） */
    private final ExecutorService imgPool = Executors.newFixedThreadPool(32, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("strm-img-dl");
        return t;
    });

    /**
     * TMDB API 令牌桶：最多 35 个并发令牌，每 10 秒补满。
     * TMDB 官方限制 40 次/10s，留 5 个余量防 429。
     */
    private final Semaphore tmdbPermits = new Semaphore(35, true);

    @PostConstruct
    public void initRateLimiter() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tmdb-rate-refill");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            int deficit = 35 - tmdbPermits.availablePermits();
            if (deficit > 0) tmdbPermits.release(deficit);
        }, 10, 10, TimeUnit.SECONDS);
    }

    /** 匹配目录名中的 [tmdbid=12345] 标记（大小写不敏感） */
    private static final Pattern TMDB_ID_PATTERN =
            Pattern.compile("\\[tmdbid=(\\d+)\\]", Pattern.CASE_INSENSITIVE);

    // ─── 公开结果类 ───────────────────────────────────────────────────────────

    public static class StrmFileResult {
        public String  strmLocalPath;
        public String  nfoLocalPath;
        public String  showDir;
        public Integer tmdbId;
    }

    // ─── TMDB 缓存结构（供调用方持有，传入同一 Map 可跨文件复用） ─────────────

    public static class ActorInfo {
        public String name;
        public String role;
        public String profilePath; // TMDB profile_path，写入 NFO 的 <thumb>
        public int    order;
    }

    public static class ShowCache {
        public Integer          tmdbId;
        public String           type;              // tv / movie
        public String           title;
        public String           originalTitle;
        public String           year;
        public String           premiered;         // first_air_date / release_date
        public String           overview;
        public String           tagline;           // 电影口号
        public String           imdbId;            // 电影 IMDB ID
        public String           posterPath;
        public String           backdropPath;
        public double           rating;
        public int              voteCount;
        public String           statusStr;
        public Integer          runtime;
        public String           contentRating;     // MPAA / TV 分级（US）
        public List<String>     genres    = new ArrayList<>();
        public List<String>     studios   = new ArrayList<>();
        public List<String>     directors = new ArrayList<>();  // 电影导演
        public List<String>     countries = new ArrayList<>();
        public List<ActorInfo>  actors    = new ArrayList<>();  // 演员表（前15位）
        public String           category;
        public String           dirName;
        public volatile boolean metadataWritten = false;
        public Map<String, List<EpInfo>> seasonEpsCache    = new HashMap<>();
        public Map<String, String>       seasonPosterPaths = new HashMap<>(); // "s1" → poster_path
    }

    public static class EpInfo {
        public int          number;
        public String       title;
        public String       overview;
        public String       airDate;
        public String       stillPath;
        public double       rating;
        public List<String> directors = new ArrayList<>();
        public List<String> writers   = new ArrayList<>();
    }

    // ─── 列出 GD 文件（带 mtime） ────────────────────────────────────────────

    /**
     * 使用 rclone lsf --format "pt" 列出目录下所有视频文件及其修改时间。
     *
     * @return Map: 相对路径 → mtime 字符串
     */
    /**
     * 使用 rclone lsf --format "pt" 列出目录下所有视频文件及其修改时间。
     *
     * 采用逐行流式读取，避免将海量输出缓冲进单个 String（百万级文件时节省数 GB 内存）。
     * rclone 运行时间不设上限——大目录可能需要数小时，调用方线程在此阻塞直至完成。
     *
     * @return Map: 相对路径 → mtime 字符串
     */
    public Map<String, String> listGdFilesWithMtime(String remote, String path) throws Exception {
        String remotePath = remote + ":" + path.replaceAll("^/+", "");
        Process p = new ProcessBuilder(
                rclonePath, "lsf", "--config", rcloneConfigPath,
                remotePath, "-R", "--files-only", "--format", "pt", "--separator", "|"
        ).redirectErrorStream(true).start();

        // 流式逐行读取：rclone 输出一行即处理一行，无需等到全部输出再拼 String
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 8192)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int sep = line.lastIndexOf('|');
                if (sep <= 0) continue;
                String relPath = line.substring(0, sep).trim();
                String mtime   = line.substring(sep + 1).trim();
                int dot = relPath.lastIndexOf('.');
                if (dot > 0 && VIDEO_EXTS.contains(relPath.substring(dot + 1).toLowerCase())) {
                    result.put(relPath, mtime);
                }
            }
        }
        // 读完 stdout rclone 应已退出，给 30s 做进程资源回收
        if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly();
        return result;
    }

    /**
     * 不带 mtime，仅列出视频文件相对路径（供一次性生成场景使用）。
     * 同样采用逐行流式读取。
     */
    public List<String> listGdFiles(String remote, String path) throws Exception {
        String remotePath = remote + ":" + path.replaceAll("^/+", "");
        Process p = new ProcessBuilder(
                rclonePath, "lsf", "--config", rcloneConfigPath,
                remotePath, "-R", "--files-only"
        ).redirectErrorStream(true).start();

        List<String> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 8192)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int dot = line.lastIndexOf('.');
                if (dot > 0 && VIDEO_EXTS.contains(line.substring(dot + 1).toLowerCase())) {
                    result.add(line);
                }
            }
        }
        if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly();
        return result;
    }

    // ─── 核心：单文件处理 ─────────────────────────────────────────────────────

    /**
     * 处理单个视频文件：解析文件名 → TMDB → 写 .strm → 写 .nfo → 下载图片。
     *
     * @param showCache 调用方维护的 ShowCache Map，相同节目不重复请求 TMDB
     * @return StrmFileResult 包含生成文件的本地路径信息
     */
    public StrmFileResult processFileToStrm(String gdRemote, String gdSourcePath, String relFilePath,
                                            String outputBase, String playUrlBase,
                                            String tmdbApiKey, String language,
                                            Map<String, ShowCache> showCache) throws Exception {

        String filename = Paths.get(relFilePath.replace('\\', '/')).getFileName().toString();

        // 1. 解析文件名
        ArchiveAnalyzeResult parsed = archiveService.analyzeFilename(filename);

        // 2. TMDB（缓存）
        // 优先从路径目录名中提取 [tmdbid=XXX]，直接按 ID 查询，跳过模糊搜索
        Integer dirTmdbId = extractTmdbIdFromPath(relFilePath);
        String cacheKey;
        ShowCache cache;
        if (dirTmdbId != null && tmdbApiKey != null && !tmdbApiKey.isEmpty()) {
            cacheKey = "tmdbid:" + dirTmdbId;
            int tmdbIdFinal = dirTmdbId;
            cache = showCache.computeIfAbsent(cacheKey,
                    k -> fetchShowCacheByTmdbId(tmdbIdFinal, parsed, tmdbApiKey, language));
        } else {
            cacheKey = normalizeKey(parsed.getTitle())
                    + "|" + safe(parsed.getYear())
                    + "|" + safe(parsed.getMediaType());
            cache = showCache.computeIfAbsent(cacheKey,
                    k -> fetchShowCache(parsed, tmdbApiKey, language));
        }

        // 3. 输出目录
        // 类型优先取 cache.type（TMDB 直查时已确定），其次取文件名解析结果
        String resolvedType = cache.type != null ? cache.type : safe(parsed.getMediaType());
        String category  = cache.category != null ? cache.category : defaultCategory(resolvedType);
        String dirName   = sanitizeDirName(
                cache.dirName != null ? cache.dirName : buildFallbackDirName(parsed));
        String seasonDir = "";
        if ("tv".equals(resolvedType) && parsed.getSeason() != null) {
            seasonDir = "Season " + parsed.getSeason();
        }
        Path outDir = seasonDir.isEmpty()
                ? Paths.get(outputBase, category, dirName)
                : Paths.get(outputBase, category, dirName, seasonDir);
        Files.createDirectories(outDir);

        // 4. 集标题（TV）
        EpInfo epInfo = null;
        if ("tv".equals(resolvedType) && parsed.getSeason() != null && parsed.getEpisode() != null) {
            epInfo = findEp(cache, parsed, tmdbApiKey, language);
        }

        // 5. 文件名（不含扩展名）
        String baseName = buildStrmBaseName(parsed, cache.title, epInfo != null ? epInfo.title : null);

        // 6. 写 .strm
        String cleanBase = playUrlBase.replaceAll("/+$", "");
        String cleanSrc  = gdSourcePath.replaceAll("^/+|/+$", "");
        String strmUrl   = cleanBase + "/" + cleanSrc + "/" + relFilePath.replace('\\', '/');
        Path strmPath    = outDir.resolve(baseName + ".strm");
        writeTextFile(strmPath, strmUrl);

        // 7. 写 NFO
        Path nfoPath = outDir.resolve(baseName + ".nfo");
        if ("tv".equals(resolvedType)) {
            writeEpisodeNfo(outDir, baseName, parsed, cache, epInfo);
        } else {
            writeMovieNfo(outDir, baseName, parsed, cache);
        }

        // 8. 节目级元数据（每个 show 只写一次）
        Path showDirPath = Paths.get(outputBase, category, dirName);
        if (!cache.metadataWritten) {
            writeShowLevelMetadata(showDirPath, cache, resolvedType);
            cache.metadataWritten = true;
        }

        // 9. 集缩略图（*-thumb.jpg）— Emby 集列表封面（异步，不阻塞主循环）
        if (epInfo != null && epInfo.stillPath != null) {
            downloadImageAsync(TMDB_IMG_W500 + epInfo.stillPath, outDir.resolve(baseName + "-thumb.jpg"));
        }

        // 10. 季海报（Season N/poster.jpg）— Emby 季封面（异步）
        if ("tv".equals(resolvedType) && !seasonDir.isEmpty() && parsed.getSeason() != null) {
            String seasonPoster = cache.seasonPosterPaths.get("s" + parsed.getSeason());
            if (seasonPoster != null) {
                downloadImageAsync(TMDB_IMG_W500 + seasonPoster, outDir.resolve("poster.jpg"));
            }
        }

        StrmFileResult result = new StrmFileResult();
        result.strmLocalPath = strmPath.toAbsolutePath().toString();
        result.nfoLocalPath  = nfoPath.toAbsolutePath().toString();
        result.showDir       = showDirPath.toAbsolutePath().toString();
        result.tmdbId        = cache.tmdbId;
        return result;
    }

    // ─── 删除本地文件 ─────────────────────────────────────────────────────────

    /**
     * 删除 .strm 和 .nfo 文件，并尝试清理空目录。
     *
     * @param strmPath  本地 .strm 路径
     * @param nfoPath   本地 .nfo 路径
     * @param showDir   节目/电影根目录，作为向上清理的边界（不会删除此目录及其以上的目录）
     */
    public void deleteLocalFiles(String strmPath, String nfoPath, String showDir) {
        deleteSilently(strmPath);
        deleteSilently(nfoPath);
        if (strmPath != null && !strmPath.isEmpty()) {
            // 以 showDir 为上界，避免级联删除整个输出根目录
            Path stopAt = (showDir != null && !showDir.isEmpty())
                    ? Paths.get(showDir).toAbsolutePath() : null;
            cleanEmptyDir(Paths.get(strmPath).getParent().toAbsolutePath(), stopAt);
        }
    }

    private void deleteSilently(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (Exception e) {
            log.warn("删除文件失败: {}", path);
        }
    }

    /**
     * 递归向上清理空目录，但不超过 stopAt 边界。
     *
     * @param stopAt 上界目录（不删除此目录本身，即使为空）
     */
    private void cleanEmptyDir(Path dir, Path stopAt) {
        if (dir == null || !Files.isDirectory(dir)) return;
        // 到达边界时停止（保护 showDir 及其祖先目录）
        if (stopAt != null && dir.equals(stopAt)) return;
        try {
            try (Stream<Path> stream = Files.list(dir)) {
                if (stream.findFirst().isPresent()) return;
            }
            Files.delete(dir);
            cleanEmptyDir(dir.getParent().toAbsolutePath(), stopAt);
        } catch (Exception e) {
            log.debug("清理目录失败: {}", dir);
        }
    }

    // ─── TMDB 搜索 + 详情 ────────────────────────────────────────────────────

    /**
     * 从文件相对路径的各级目录名中提取 [tmdbid=XXX]。
     * 例如 "鬼人幻燈抄 (2024) [tmdbid=243060]/Season 1/ep01.mkv" → 243060
     */
    private Integer extractTmdbIdFromPath(String relFilePath) {
        for (String part : relFilePath.replace('\\', '/').split("/")) {
            Matcher m = TMDB_ID_PATTERN.matcher(part);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /**
     * 直接按 TMDB ID 获取 ShowCache，跳过模糊搜索。
     * 根据文件是否含集号推断类型（tv/movie），若推断错误则自动切换重试。
     */
    private ShowCache fetchShowCacheByTmdbId(int tmdbId, ArchiveAnalyzeResult parsed,
                                             String tmdbApiKey, String language) {
        ShowCache cache = new ShowCache();
        cache.tmdbId = tmdbId;
        // 有集号 → tv，否则 → movie
        String[] typesToTry = (parsed.getEpisode() != null)
                ? new String[]{"tv", "movie"} : new String[]{"movie", "tv"};

        for (String type : typesToTry) {
            try {
                boolean isTV = "tv".equals(type);
                String url = isTV
                        ? String.format(TMDB_DETAILS_TV_URL,    tmdbId, tmdbApiKey, language)
                        : String.format(TMDB_DETAILS_MOVIE_URL, tmdbId, tmdbApiKey, language);
                JSONObject obj = tmdbGet(url);
                // TMDB 查询失败时返回 {"status_code":34,...}
                if (obj.containsKey("status_code")) continue;

                cache.type         = type;
                cache.overview     = obj.getStr("overview");
                cache.posterPath   = obj.getStr("poster_path");
                cache.backdropPath = obj.getStr("backdrop_path");
                cache.rating       = obj.getDouble("vote_average", 0.0);
                cache.voteCount    = obj.getInt("vote_count", 0);

                JSONArray genres = obj.getJSONArray("genres");
                if (genres != null) {
                    for (int i = 0; i < genres.size(); i++) {
                        String g = genres.getJSONObject(i).getStr("name");
                        if (g != null) cache.genres.add(g);
                    }
                }

                if (isTV) {
                    cache.title         = obj.getStr("name");
                    cache.originalTitle = obj.getStr("original_name");
                    String firstAir     = obj.getStr("first_air_date");
                    cache.premiered     = firstAir;
                    cache.year          = firstAir != null && firstAir.length() >= 4
                            ? firstAir.substring(0, 4) : null;
                    cache.statusStr     = obj.getStr("status");
                    cache.contentRating = extractTvContentRating(obj);
                    JSONArray networks  = obj.getJSONArray("networks");
                    if (networks != null) {
                        for (int i = 0; i < networks.size(); i++) {
                            String n = networks.getJSONObject(i).getStr("name");
                            if (n != null) cache.studios.add(n);
                        }
                    }
                    JSONArray originCountries = obj.getJSONArray("origin_country");
                    if (originCountries != null) {
                        for (int i = 0; i < originCountries.size(); i++) {
                            String c = originCountries.getStr(i);
                            if (c != null) cache.countries.add(c);
                        }
                    }
                } else {
                    cache.title         = obj.getStr("title");
                    cache.originalTitle = obj.getStr("original_title");
                    String releaseDate  = obj.getStr("release_date");
                    cache.premiered     = releaseDate;
                    cache.year          = releaseDate != null && releaseDate.length() >= 4
                            ? releaseDate.substring(0, 4) : null;
                    cache.runtime       = obj.getInt("runtime");
                    cache.tagline       = obj.getStr("tagline");
                    cache.imdbId        = obj.getStr("imdb_id");
                    cache.contentRating = extractMovieContentRating(obj);
                    JSONArray companies = obj.getJSONArray("production_companies");
                    if (companies != null) {
                        for (int i = 0; i < Math.min(2, companies.size()); i++) {
                            String c = companies.getJSONObject(i).getStr("name");
                            if (c != null) cache.studios.add(c);
                        }
                    }
                    JSONArray prodCountries = obj.getJSONArray("production_countries");
                    if (prodCountries != null) {
                        for (int i = 0; i < prodCountries.size(); i++) {
                            String c = prodCountries.getJSONObject(i).getStr("name");
                            if (c != null) cache.countries.add(c);
                        }
                    }
                }

                extractCredits(obj, cache);

                // 构造输出目录名和分类
                if (cache.title != null) {
                    String yearStr = cache.year != null ? " (" + cache.year + ")" : "";
                    cache.dirName  = cache.title + yearStr;
                    cache.category = defaultCategory(type);
                }
                log.debug("TMDB 直查成功: id={}, type={}, title={}", tmdbId, type, cache.title);
                break;
            } catch (Exception e) {
                log.warn("TMDB 直查失败: id={}, type={}, err={}", tmdbId, type, e.getMessage());
            }
        }
        return cache;
    }

    public ShowCache fetchShowCache(ArchiveAnalyzeResult parsed, String tmdbApiKey, String language) {
        ShowCache cache = new ShowCache();
        if (tmdbApiKey == null || tmdbApiKey.isEmpty()) return cache;
        try {
            List<ArchiveTmdbItem> results = archiveService.searchTmdb(
                    parsed.getTitle(), parsed.getYear(), safe(parsed.getMediaType()));
            if (results.isEmpty()) return cache;

            ArchiveTmdbItem best  = results.get(0);
            cache.tmdbId          = best.getTmdbId();
            cache.type            = best.getType();
            cache.title           = best.getTitle();
            cache.originalTitle   = best.getOriginalTitle();
            cache.year            = best.getYear();
            cache.category        = best.getSuggestedCategory();
            cache.dirName         = best.getSuggestedDirName();
            fetchTmdbDetails(cache, tmdbApiKey, language);
        } catch (Exception e) {
            log.warn("TMDB 搜索失败: title={}, err={}", parsed.getTitle(), e.getMessage());
        }
        return cache;
    }

    private void fetchTmdbDetails(ShowCache cache, String tmdbApiKey, String language) {
        if (cache.tmdbId == null) return;
        try {
            boolean isTV = "tv".equals(cache.type);
            String url = isTV
                    ? String.format(TMDB_DETAILS_TV_URL,    cache.tmdbId, tmdbApiKey, language)
                    : String.format(TMDB_DETAILS_MOVIE_URL, cache.tmdbId, tmdbApiKey, language);
            JSONObject obj = tmdbGet(url);

            cache.overview     = obj.getStr("overview");
            cache.posterPath   = obj.getStr("poster_path");
            cache.backdropPath = obj.getStr("backdrop_path");
            cache.rating       = obj.getDouble("vote_average", 0.0);
            cache.voteCount    = obj.getInt("vote_count", 0);

            JSONArray genres = obj.getJSONArray("genres");
            if (genres != null) {
                for (int i = 0; i < genres.size(); i++) {
                    String g = genres.getJSONObject(i).getStr("name");
                    if (g != null) cache.genres.add(g);
                }
            }

            if (isTV) {
                cache.statusStr = obj.getStr("status");
                cache.premiered = obj.getStr("first_air_date");
                JSONArray networks = obj.getJSONArray("networks");
                if (networks != null) {
                    for (int i = 0; i < networks.size(); i++) {
                        String n = networks.getJSONObject(i).getStr("name");
                        if (n != null) cache.studios.add(n);
                    }
                }
                JSONArray originCountries = obj.getJSONArray("origin_country");
                if (originCountries != null) {
                    for (int i = 0; i < originCountries.size(); i++) {
                        String c = originCountries.getStr(i);
                        if (c != null) cache.countries.add(c);
                    }
                }
                // TV 内容分级（取 US，fallback 取第一个）
                cache.contentRating = extractTvContentRating(obj);
            } else {
                cache.runtime   = obj.getInt("runtime");
                cache.tagline   = obj.getStr("tagline");
                cache.imdbId    = obj.getStr("imdb_id");
                cache.premiered = obj.getStr("release_date");
                JSONArray companies = obj.getJSONArray("production_companies");
                if (companies != null) {
                    for (int i = 0; i < Math.min(2, companies.size()); i++) {
                        String c = companies.getJSONObject(i).getStr("name");
                        if (c != null) cache.studios.add(c);
                    }
                }
                JSONArray prodCountries = obj.getJSONArray("production_countries");
                if (prodCountries != null) {
                    for (int i = 0; i < prodCountries.size(); i++) {
                        String c = prodCountries.getJSONObject(i).getStr("name");
                        if (c != null) cache.countries.add(c);
                    }
                }
                // 电影 MPAA 分级（取 US）
                cache.contentRating = extractMovieContentRating(obj);
            }

            // 演员表（取前15位主演）
            extractCredits(obj, cache);

        } catch (Exception e) {
            log.warn("TMDB 详情失败: id={}, err={}", cache.tmdbId, e.getMessage());
        }
    }

    /** 从 append_to_response=credits 的响应中提取演员和导演 */
    private void extractCredits(JSONObject obj, ShowCache cache) {
        JSONObject credits = obj.getJSONObject("credits");
        if (credits == null) return;
        // 演员（cast，取前15位）
        JSONArray cast = credits.getJSONArray("cast");
        if (cast != null) {
            for (int i = 0; i < Math.min(15, cast.size()); i++) {
                JSONObject c = cast.getJSONObject(i);
                ActorInfo a  = new ActorInfo();
                a.name        = c.getStr("name");
                a.role        = c.getStr("character");
                a.profilePath = c.getStr("profile_path");
                a.order       = c.getInt("order", i);
                if (a.name != null) cache.actors.add(a);
            }
        }
        // 导演（crew，job == "Director"）
        JSONArray crew = credits.getJSONArray("crew");
        if (crew != null) {
            for (int i = 0; i < crew.size(); i++) {
                JSONObject c = crew.getJSONObject(i);
                if ("Director".equals(c.getStr("job"))) {
                    String n = c.getStr("name");
                    if (n != null) cache.directors.add(n);
                }
            }
        }
    }

    /** 提取电影 MPAA 分级（release_dates，优先取 US） */
    private String extractMovieContentRating(JSONObject obj) {
        try {
            JSONObject rd = obj.getJSONObject("release_dates");
            if (rd == null) return null;
            JSONArray results = rd.getJSONArray("results");
            if (results == null) return null;
            String fallback = null;
            for (int i = 0; i < results.size(); i++) {
                JSONObject r = results.getJSONObject(i);
                String iso = r.getStr("iso_3166_1");
                JSONArray dates = r.getJSONArray("release_dates");
                if (dates == null || dates.isEmpty()) continue;
                String cert = dates.getJSONObject(0).getStr("certification");
                if (cert != null && !cert.isEmpty()) {
                    if ("US".equals(iso)) return cert;
                    if (fallback == null) fallback = cert;
                }
            }
            return fallback;
        } catch (Exception e) { return null; }
    }

    /** 提取剧集内容分级（content_ratings，优先取 US） */
    private String extractTvContentRating(JSONObject obj) {
        try {
            JSONObject cr = obj.getJSONObject("content_ratings");
            if (cr == null) return null;
            JSONArray results = cr.getJSONArray("results");
            if (results == null) return null;
            String fallback = null;
            for (int i = 0; i < results.size(); i++) {
                JSONObject r = results.getJSONObject(i);
                String iso = r.getStr("iso_3166_1");
                String rating = r.getStr("rating");
                if (rating != null && !rating.isEmpty()) {
                    if ("US".equals(iso)) return rating;
                    if (fallback == null) fallback = rating;
                }
            }
            return fallback;
        } catch (Exception e) { return null; }
    }

    private EpInfo findEp(ShowCache cache, ArchiveAnalyzeResult parsed,
                          String tmdbApiKey, String language) {
        if (cache.tmdbId == null || !"tv".equals(cache.type)) return null;
        int seasonNum, epNum;
        try {
            seasonNum = Integer.parseInt(parsed.getSeason());
            epNum     = Integer.parseInt(parsed.getEpisode());
        } catch (NumberFormatException e) {
            return null;
        }

        String seasonKey = "s" + seasonNum;
        cache.seasonEpsCache.computeIfAbsent(seasonKey,
                k -> fetchSeason(cache, seasonNum, tmdbApiKey, language));

        List<EpInfo> eps = cache.seasonEpsCache.get(seasonKey);
        if (eps == null) return null;
        for (EpInfo ep : eps) {
            if (ep.number == epNum) return ep;
        }
        return null;
    }

    private List<EpInfo> fetchSeason(ShowCache cache, int seasonNum, String tmdbApiKey, String language) {
        int tmdbId = cache.tmdbId;
        try {
            String url = String.format(TMDB_SEASON_URL, tmdbId, seasonNum, tmdbApiKey, language);
            JSONObject obj = tmdbGet(url);
            // 存储季海报路径，供后续下载 Season poster.jpg
            String seasonPoster = obj.getStr("poster_path");
            if (seasonPoster != null) {
                cache.seasonPosterPaths.put("s" + seasonNum, seasonPoster);
            }
            JSONArray episodes = obj.getJSONArray("episodes");
            List<EpInfo> list = new ArrayList<>();
            if (episodes != null) {
                for (int i = 0; i < episodes.size(); i++) {
                    JSONObject ep = episodes.getJSONObject(i);
                    EpInfo info  = new EpInfo();
                    info.number    = ep.getInt("episode_number", 0);
                    info.title     = ep.getStr("name");
                    info.overview  = ep.getStr("overview");
                    info.airDate   = ep.getStr("air_date");
                    info.stillPath = ep.getStr("still_path");
                    info.rating    = ep.getDouble("vote_average", 0.0);
                    // 单集导演 / 编剧
                    JSONArray crew = ep.getJSONArray("crew");
                    if (crew != null) {
                        for (int j = 0; j < crew.size(); j++) {
                            JSONObject m = crew.getJSONObject(j);
                            String job  = m.getStr("job");
                            String name = m.getStr("name");
                            if (name == null) continue;
                            if ("Director".equals(job))                info.directors.add(name);
                            else if ("Writer".equals(job)
                                  || "Screenplay".equals(job)
                                  || "Story".equals(job))              info.writers.add(name);
                        }
                    }
                    list.add(info);
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("获取季信息失败: tmdbId={}, season={}, err={}", tmdbId, seasonNum, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── NFO 写入 ─────────────────────────────────────────────────────────────

    public void writeShowLevelMetadata(Path showDir, ShowCache cache, String mediaType) {
        try {
            Files.createDirectories(showDir);
            if ("tv".equals(mediaType)) {
                writeTvshowNfo(showDir, cache);
            }
            if (cache.posterPath != null) {
                downloadImageAsync(TMDB_IMG_W500 + cache.posterPath, showDir.resolve("poster.jpg"));
            }
            if (cache.backdropPath != null) {
                downloadImageAsync(TMDB_IMG_ORIG + cache.backdropPath, showDir.resolve("fanart.jpg"));
            }
        } catch (Exception e) {
            log.warn("写节目元数据失败: dir={}, err={}", showDir, e.getMessage());
        }
    }

    private void writeTvshowNfo(Path showDir, ShowCache cache) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<tvshow>\n");
        appendTag(sb, "  ", "title",         cache.title);
        appendTag(sb, "  ", "originaltitle", cache.originalTitle);
        appendTag(sb, "  ", "sorttitle",     cache.title);
        appendTag(sb, "  ", "year",          cache.year);
        appendTag(sb, "  ", "premiered",     cache.premiered);
        appendTag(sb, "  ", "plot",          cache.overview);
        if (cache.tmdbId != null) {
            sb.append("  <uniqueid type=\"tmdb\" default=\"true\">").append(cache.tmdbId).append("</uniqueid>\n");
        }
        appendTag(sb, "  ", "mpaa",   cache.contentRating);
        appendTag(sb, "  ", "status", cache.statusStr);
        if (cache.rating > 0) {
            sb.append("  <rating>").append(String.format("%.1f", cache.rating)).append("</rating>\n");
        }
        if (cache.voteCount > 0) {
            sb.append("  <votes>").append(cache.voteCount).append("</votes>\n");
        }
        for (String g : cache.genres)   appendTag(sb, "  ", "genre",   g);
        for (String s : cache.studios)  appendTag(sb, "  ", "studio",  s);
        for (String c : cache.countries) appendTag(sb, "  ", "country", c);
        if (cache.posterPath   != null) sb.append("  <thumb aspect=\"poster\">poster.jpg</thumb>\n");
        if (cache.backdropPath != null) sb.append("  <fanart>\n    <thumb>fanart.jpg</thumb>\n  </fanart>\n");
        for (ActorInfo a : cache.actors) {
            sb.append("  <actor>\n");
            appendTag(sb, "    ", "name",  a.name);
            appendTag(sb, "    ", "role",  a.role);
            if (a.profilePath != null) appendTag(sb, "    ", "thumb", TMDB_IMG_PROFILE + a.profilePath);
            sb.append("    <order>").append(a.order).append("</order>\n");
            sb.append("  </actor>\n");
        }
        sb.append("</tvshow>\n");
        writeTextFile(showDir.resolve("tvshow.nfo"), sb.toString());
    }

    private void writeEpisodeNfo(Path outDir, String baseName,
                                 ArchiveAnalyzeResult parsed, ShowCache cache, EpInfo ep) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<episodedetails>\n");
        appendTag(sb, "  ", "title",   ep != null ? ep.title   : baseName);
        appendTag(sb, "  ", "season",  parsed.getSeason());
        appendTag(sb, "  ", "episode", parsed.getEpisode());
        appendTag(sb, "  ", "plot",    ep != null ? ep.overview : null);
        if (cache.tmdbId != null) {
            sb.append("  <uniqueid type=\"tmdb\">").append(cache.tmdbId).append("</uniqueid>\n");
        }
        if (ep != null) {
            appendTag(sb, "  ", "aired", ep.airDate);
            if (ep.stillPath != null) appendTag(sb, "  ", "thumb", TMDB_IMG_W500 + ep.stillPath);
            if (ep.rating > 0) sb.append("  <rating>").append(String.format("%.1f", ep.rating)).append("</rating>\n");
            for (String d : ep.directors) appendTag(sb, "  ", "director", d);
            for (String w : ep.writers)   appendTag(sb, "  ", "credits",  w);
        }
        sb.append("</episodedetails>\n");
        writeTextFile(outDir.resolve(baseName + ".nfo"), sb.toString());
    }

    private void writeMovieNfo(Path outDir, String baseName,
                               ArchiveAnalyzeResult parsed, ShowCache cache) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<movie>\n");
        String title = cache.title != null ? cache.title
                : (parsed.getTitle() != null ? parsed.getTitle() : baseName);
        appendTag(sb, "  ", "title",         title);
        appendTag(sb, "  ", "originaltitle", cache.originalTitle);
        appendTag(sb, "  ", "sorttitle",     title);
        appendTag(sb, "  ", "year",          cache.year != null ? cache.year : parsed.getYear());
        appendTag(sb, "  ", "premiered",     cache.premiered);
        appendTag(sb, "  ", "tagline",       cache.tagline);
        appendTag(sb, "  ", "plot",          cache.overview);
        appendTag(sb, "  ", "mpaa",          cache.contentRating);
        if (cache.tmdbId != null) {
            sb.append("  <uniqueid type=\"tmdb\" default=\"true\">").append(cache.tmdbId).append("</uniqueid>\n");
        }
        if (cache.imdbId != null && !cache.imdbId.isEmpty()) {
            sb.append("  <uniqueid type=\"imdb\">").append(cache.imdbId).append("</uniqueid>\n");
        }
        if (cache.rating > 0) {
            sb.append("  <rating>").append(String.format("%.1f", cache.rating)).append("</rating>\n");
        }
        if (cache.voteCount > 0) {
            sb.append("  <votes>").append(cache.voteCount).append("</votes>\n");
        }
        if (cache.runtime != null && cache.runtime > 0) {
            sb.append("  <runtime>").append(cache.runtime).append("</runtime>\n");
        }
        for (String g : cache.genres)    appendTag(sb, "  ", "genre",     g);
        for (String s : cache.studios)   appendTag(sb, "  ", "studio",    s);
        for (String c : cache.countries) appendTag(sb, "  ", "country",   c);
        for (String d : cache.directors) appendTag(sb, "  ", "director",  d);
        if (cache.posterPath   != null) sb.append("  <thumb aspect=\"poster\">poster.jpg</thumb>\n");
        if (cache.backdropPath != null) sb.append("  <fanart>\n    <thumb>fanart.jpg</thumb>\n  </fanart>\n");
        for (ActorInfo a : cache.actors) {
            sb.append("  <actor>\n");
            appendTag(sb, "    ", "name",  a.name);
            appendTag(sb, "    ", "role",  a.role);
            if (a.profilePath != null) appendTag(sb, "    ", "thumb", TMDB_IMG_PROFILE + a.profilePath);
            sb.append("    <order>").append(a.order).append("</order>\n");
            sb.append("  </actor>\n");
        }
        sb.append("</movie>\n");
        writeTextFile(outDir.resolve(baseName + ".nfo"), sb.toString());

        // 电影封面下载到同目录（异步，不阻塞主循环）
        if (cache.posterPath   != null) downloadImageAsync(TMDB_IMG_W500 + cache.posterPath,   outDir.resolve("poster.jpg"));
        if (cache.backdropPath != null) downloadImageAsync(TMDB_IMG_ORIG + cache.backdropPath, outDir.resolve("fanart.jpg"));
    }

    // ─── 图片下载 ─────────────────────────────────────────────────────────────

    /**
     * 受令牌桶保护的 TMDB HTTP GET，自动限速（≤35次/10s）。
     * 所有对 TMDB API 的调用都应通过此方法，防止触发 429。
     */
    private JSONObject tmdbGet(String url) throws Exception {
        try {
            tmdbPermits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("TMDB 请求被中断", e);
        }
        return JSONUtil.parseObj(HttpRequest.get(url).timeout(15000).execute().body());
    }

    public void downloadImage(String url, Path dest) {
        if (url == null || url.isEmpty()) return;
        if (Files.exists(dest)) return;
        try {
            byte[] bytes = HttpRequest.get(url).timeout(30000).execute().bodyBytes();
            if (bytes != null && bytes.length > 0) Files.write(dest, bytes);
        } catch (Exception e) {
            log.warn("下载图片失败: url={}, err={}", url, e.getMessage());
        }
    }

    /**
     * 异步下载图片：提交到后台线程池，不阻塞调用方。
     * 调用前先做 Files.exists 快速判断，避免对已存在文件提交任务。
     */
    public void downloadImageAsync(String url, Path dest) {
        if (url == null || url.isEmpty() || dest == null) return;
        if (Files.exists(dest)) return;
        imgPool.submit(() -> downloadImage(url, dest));
    }

    // ─── 辅助 ─────────────────────────────────────────────────────────────────

    public void writeTextFile(Path path, String content) {
        try {
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("写文件失败: path={}, err={}", path, e.getMessage());
        }
    }

    private void appendTag(StringBuilder sb, String indent, String tag, String value) {
        if (value == null || value.isEmpty()) return;
        sb.append(indent).append('<').append(tag).append('>')
          .append(xmlEscape(value))
          .append("</").append(tag).append(">\n");
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public String buildStrmBaseName(ArchiveAnalyzeResult parsed, String tmdbTitle, String epTitle) {
        String title = tmdbTitle != null ? tmdbTitle
                : (parsed.getTitle() != null ? parsed.getTitle() : "Unknown");
        StringBuilder sb = new StringBuilder(title);
        if (parsed.getSeason() != null && parsed.getEpisode() != null) {
            sb.append(" - S").append(parsed.getSeason()).append("E").append(parsed.getEpisode());
            if (epTitle != null && !epTitle.isEmpty()) {
                String ep = epTitle.length() > 60 ? epTitle.substring(0, 60) : epTitle;
                sb.append(" - ").append(ep);
            }
        } else if (parsed.getEpisode() != null) {
            sb.append(" - E").append(parsed.getEpisode());
            if (epTitle != null && !epTitle.isEmpty()) {
                String ep = epTitle.length() > 60 ? epTitle.substring(0, 60) : epTitle;
                sb.append(" - ").append(ep);
            }
        } else if ("movie".equals(safe(parsed.getMediaType()))) {
            String year = parsed.getYear() != null ? parsed.getYear() : "";
            if (!year.isEmpty()) sb.append(" (").append(year).append(")");
        }
        return sb.toString().replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    public String buildFallbackDirName(ArchiveAnalyzeResult parsed) {
        String t = parsed.getTitle();
        if (t != null && !t.isEmpty()) {
            return (parsed.getYear() != null) ? t + " (" + parsed.getYear() + ")" : t;
        }
        return "未知";
    }

    public String defaultCategory(String mediaType) {
        return "tv".equals(mediaType) ? "其他剧集" : "外语电影";
    }

    public String normalizeKey(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 清理目录名中的非法文件系统字符（Windows + Linux 通用）。
     * Windows 禁止 \ / : * ? " < > | 出现在路径组件中；Linux 只禁止 / 和 \0，
     * 但统一清理可避免跨平台问题（电影标题常含冒号，如 "Avatar: The Way of Water"）。
     */
    public String sanitizeDirName(String name) {
        if (name == null) return "未知";
        return name.replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s{2,}", " ").trim();
    }
}
