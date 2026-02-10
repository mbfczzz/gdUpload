package com.gdupload.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gdupload.common.BusinessException;
import com.gdupload.dto.EmbyGenre;
import com.gdupload.dto.EmbyItem;
import com.gdupload.dto.EmbyLibrary;
import com.gdupload.dto.PagedResult;
import com.gdupload.entity.EmbyConfig;
import com.gdupload.entity.EmbyDownloadHistory;
import com.gdupload.entity.FileInfo;
import com.gdupload.mapper.EmbyDownloadHistoryMapper;
import com.gdupload.service.*;
import com.gdupload.util.DateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Emby服务实现类
 */
@Slf4j
@Service
public class EmbyServiceImpl implements IEmbyService {

    @Autowired
    private EmbyAuthService embyAuthService;

    @Autowired
    private EmbyDownloadHistoryMapper downloadHistoryMapper;

    @Autowired
    private IEmbyConfigService embyConfigService;

    @Autowired
    private IUploadTaskService uploadTaskService;

    @Autowired
    private IFileInfoService fileInfoService;

    @Autowired
    private IWebSocketService webSocketService;

    @Autowired
    private ISmartSearchConfigService smartSearchConfigService;

    @Value("${app.emby.download-dir:/data/emby}")
    private String defaultEmbyDownloadDir;

    // 下载任务的停止标志，key=taskId
    private final Map<Long, AtomicBoolean> downloadStopFlags = new ConcurrentHashMap<>();

    /**
     * 获取Emby下载目录（优先从数据库读取，否则使用默认值）
     */
    private String getEmbyDownloadDir() {
        try {
            Map<String, Object> config = smartSearchConfigService.getFullConfig("default");
            String downloadDir = (String) config.get("embyDownloadDir");
            if (downloadDir != null && !downloadDir.trim().isEmpty()) {
                return downloadDir;
            }
        } catch (Exception e) {
            log.warn("读取Emby下载目录配置失败，使用默认值: {}", e.getMessage());
        }
        return defaultEmbyDownloadDir;
    }

    /**
     * 初始化方法，记录编码信息
     */
    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("EmbyService 初始化");
        log.info("JVM 默认编码: {}", System.getProperty("file.encoding"));
        log.info("JVM JNU 编码: {}", System.getProperty("sun.jnu.encoding"));
        log.info("系统默认字符集: {}", java.nio.charset.Charset.defaultCharset());
        log.info("系统 LANG: {}", System.getenv("LANG"));
        log.info("系统 LC_ALL: {}", System.getenv("LC_ALL"));
        log.info("========================================");
    }

    /**
     * 构建API URL
     */
    private String buildUrl(String path) {
        String baseUrl = embyAuthService.getServerUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    /**
     * 发送GET请求
     */
    private JSONObject sendGetRequest(String path, Map<String, Object> params) {
        return sendGetRequest(path, params, null);
    }

    /**
     * 发送GET请求（可指定超时时间）
     */
    private JSONObject sendGetRequest(String path, Map<String, Object> params, Integer customTimeout) {
        // 获取 Access Token（优先使用 API Key，否则使用登录 Token）
        String accessToken = embyAuthService.getAccessToken();
        String userId = embyAuthService.getUserId();

        String url = buildUrl(path);

        try {
            // 如果没有指定自定义超时，使用配置的超时时间
            int timeout = customTimeout != null ? customTimeout : embyAuthService.getTimeout();

            // 伪装成 Forward app 的设备ID（可以随机生成或固定）
            String deviceId = "30c9d308e74a46a1811c851bf76a8f77";
            String forwardVersion = "1.3.14";

            // 构建 X-Emby-Authorization 请求头（Forward app 格式）
            String embyAuth = String.format(
                "MediaBrowser Token=\"%s\", Emby UserId=\"%s\", Client=\"Forward\", Device=\"iPhone\", DeviceId=\"%s\", Version=\"%s\"",
                accessToken, userId, deviceId, forwardVersion
            );

            // 构建完整URL（包含查询参数）
            StringBuilder fullUrl = new StringBuilder(url);
            if (MapUtil.isNotEmpty(params)) {
                fullUrl.append("?");
                params.forEach((key, value) -> {
                    if (value != null) {
                        fullUrl.append(URLUtil.encode(key)).append("=").append(URLUtil.encode(String.valueOf(value))).append("&");
                    }
                });
                // 移除最后一个&
                if (fullUrl.charAt(fullUrl.length() - 1) == '&') {
                    fullUrl.setLength(fullUrl.length() - 1);
                }
            }

            log.info("Emby API请求URL: {}", fullUrl.toString());

            HttpRequest request = HttpRequest.get(fullUrl.toString())
                    .header("Content-Type", "application/json")
                    .header("X-Emby-Authorization", embyAuth)
                    .header("X-Emby-Token", accessToken)
                    .header("User-Agent", "Forward-Standard/" + forwardVersion)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                    // 不设置 Accept-Encoding，让 Hutool 自动处理压缩
                    .header("Connection", "keep-alive")
                    .timeout(timeout);

            HttpResponse response = request.execute();

            if (!response.isOk()) {
                log.error("Emby API请求失败: {} - 完整URL: {} - 响应: {}", response.getStatus(), fullUrl.toString(), response.body());
                throw new BusinessException("Emby API请求失败: " + response.getStatus());
            }

            String body = response.body();

            // 记录响应内容用于调试
            log.info("Emby API响应 [{}]: 前200字符={}", url, body.substring(0, Math.min(200, body.length())));

            // 检查响应是否为空
            if (StrUtil.isBlank(body)) {
                log.error("Emby API返回空响应: {}", url);
                throw new BusinessException("Emby API返回空响应");
            }

            // 检查响应是否是JSON对象
            body = body.trim();
            if (!body.startsWith("{")) {
                log.error("Emby API返回非JSON对象格式: URL={}, 响应前200字符={}", url, body.substring(0, Math.min(200, body.length())));

                // 如果是数组，返回包装后的对象
                if (body.startsWith("[")) {
                    log.info("检测到返回数组，自动包装为对象");
                    JSONObject wrapper = new JSONObject();
                    wrapper.set("Items", JSONUtil.parseArray(body));
                    wrapper.set("TotalRecordCount", JSONUtil.parseArray(body).size());
                    return wrapper;
                }

                throw new BusinessException("Emby API返回格式错误，期望JSON对象但收到: " + body.substring(0, Math.min(50, body.length())));
            }

            return JSONUtil.parseObj(body);

        } catch (cn.hutool.http.HttpException e) {
            if (e.getMessage().contains("timed out")) {
                log.error("Emby API请求超时: {} - 参数: {}", url, params);
                throw new BusinessException("Emby服务器响应超时，请稍后重试或减少每页数量");
            }
            log.error("调用Emby API异常: {} - URL: {}", e.getMessage(), url, e);
            throw new BusinessException("调用Emby API异常: " + e.getMessage());
        } catch (BusinessException e) {
            // 直接抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("调用Emby API异常: {} - URL: {}", e.getMessage(), url, e);
            throw new BusinessException("调用Emby API异常: " + e.getMessage());
        }
    }

    /**
     * 发送GET请求（返回数组）
     */
    private JSONArray sendGetRequestArray(String path, Map<String, Object> params) {
        // 获取 Access Token
        String accessToken = embyAuthService.getAccessToken();
        String userId = embyAuthService.getUserId();

        String url = buildUrl(path);

        try {
            // 伪装成 Forward app 的设备ID
            String deviceId = "30c9d308e74a46a1811c851bf76a8f77";
            String forwardVersion = "1.3.13";

            // 构建 X-Emby-Authorization 请求头（Forward app 格式）
            String embyAuth = String.format(
                "MediaBrowser Token=\"%s\", Emby UserId=\"%s\", Client=\"Forward\", Device=\"iPhone\", DeviceId=\"%s\", Version=\"%s\"",
                accessToken, userId, deviceId, forwardVersion
            );

            // 构建完整URL（包含查询参数）
            StringBuilder fullUrl = new StringBuilder(url);
            if (MapUtil.isNotEmpty(params)) {
                fullUrl.append("?");
                params.forEach((key, value) -> {
                    if (value != null) {
                        fullUrl.append(URLUtil.encode(key)).append("=").append(URLUtil.encode(String.valueOf(value))).append("&");
                    }
                });
                // 移除最后一个&
                if (fullUrl.charAt(fullUrl.length() - 1) == '&') {
                    fullUrl.setLength(fullUrl.length() - 1);
                }
            }

            log.info("Emby API请求URL: {}", fullUrl.toString());

            HttpRequest request = HttpRequest.get(fullUrl.toString())
                    .header("Content-Type", "application/json")
                    .header("X-Emby-Authorization", embyAuth)
                    .header("X-Emby-Token", accessToken)
                    .header("User-Agent", "Forward/" + forwardVersion)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                    // 不设置 Accept-Encoding，让 Hutool 自动处理压缩
                    .header("Connection", "keep-alive")
                    .timeout(embyAuthService.getTimeout());

            HttpResponse response = request.execute();

            if (!response.isOk()) {
                log.error("Emby API请求失败: {} - 完整URL: {} - 响应: {}", response.getStatus(), fullUrl.toString(), response.body());
                throw new BusinessException("Emby API请求失败: " + response.getStatus());
            }

            String body = response.body();

            // 记录响应内容用于调试
            log.debug("Emby API响应 [{}]: {}", url, body.length() > 500 ? body.substring(0, 500) + "..." : body);

            // 检查响应是否为空
            if (StrUtil.isBlank(body)) {
                log.error("Emby API返回空响应: {}", url);
                throw new BusinessException("Emby API返回空响应");
            }

            // 检查响应是否是JSON数组
            body = body.trim();
            if (!body.startsWith("[")) {
                log.error("Emby API返回非JSON数组格式: URL={}, 响应前100字符={}", url, body.substring(0, Math.min(100, body.length())));
                throw new BusinessException("Emby API返回格式错误，期望JSON数组但收到: " + body.substring(0, Math.min(50, body.length())));
            }

            return JSONUtil.parseArray(body);

        } catch (BusinessException e) {
            // 直接抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("调用Emby API异常: {} - URL: {}", e.getMessage(), url, e);
            throw new BusinessException("调用Emby API异常: " + e.getMessage());
        }
    }

    @Override
    public List<EmbyLibrary> getAllLibraries() {
        log.info("开始获取Emby媒体库列表");

        try {
            String userId = embyAuthService.getUserId();
            log.info("使用用户ID: {}", userId);

            String path = "/Users/" + userId + "/Views";

            // 添加查询参数（参考 Forward app 的请求格式）
            Map<String, Object> params = new HashMap<>();
            params.put("EnableTotalRecordCount", false);

            JSONObject response = sendGetRequest(path, params);

            JSONArray items = response.getJSONArray("Items");
            if (items == null || items.isEmpty()) {
                log.warn("未找到任何媒体库");
                return new ArrayList<>();
            }

            List<EmbyLibrary> libraries = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                EmbyLibrary library = new EmbyLibrary();
                library.setId(item.getStr("Id"));
                library.setName(item.getStr("Name"));
                library.setCollectionType(item.getStr("CollectionType"));

                // 获取媒体库路径
                JSONArray locations = item.getJSONArray("LibraryOptions");
                if (locations != null && !locations.isEmpty()) {
                    JSONObject libOptions = locations.getJSONObject(0);
                    JSONArray pathInfos = libOptions.getJSONArray("PathInfos");
                    if (pathInfos != null) {
                        List<String> paths = new ArrayList<>();
                        for (int j = 0; j < pathInfos.size(); j++) {
                            paths.add(pathInfos.getJSONObject(j).getStr("Path"));
                        }
                        library.setLocations(paths);
                    }
                }

                library.setDateCreated(item.getStr("DateCreated"));
                library.setDateModified(item.getStr("DateModified"));

                // 不在这里获取媒体项数量，避免串行调用导致加载缓慢
                // 数量将在用户点击"查看媒体项"时通过分页接口获取

                libraries.add(library);
            }

            log.info("成功获取{}个媒体库", libraries.size());
            return libraries;

        } catch (Exception e) {
            log.error("获取媒体库列表失败: {}", e.getMessage(), e);
            throw new BusinessException("获取媒体库列表失败: " + e.getMessage());
        }
    }

    @Override
    public Integer getLibraryItemCount(String libraryId) {
        log.info("开始获取媒体库[{}]的媒体项数量", libraryId);

        try {
            String userId = embyAuthService.getUserId();

            Map<String, Object> params = new HashMap<>();
            params.put("ParentId", libraryId);
            params.put("Recursive", true);
            params.put("Limit", 0); // 只获取总数，不获取实际数据

            String path = "/Users/" + userId + "/Items";
            JSONObject response = sendGetRequest(path, params, 30000);
            Integer totalCount = response.getInt("TotalRecordCount");

            log.info("媒体库[{}]包含{}个媒体项", libraryId, totalCount);
            return totalCount != null ? totalCount : 0;

        } catch (Exception e) {
            log.error("获取媒体库[{}]的媒体项数量失败: {}", libraryId, e.getMessage());
            return 0;
        }
    }

    @Override
    public List<EmbyItem> getLibraryItems(String libraryId) {
        return getLibraryItems(libraryId, null, null);
    }

    @Override
    public List<EmbyItem> getLibraryItems(String libraryId, Integer startIndex, Integer limit) {
        PagedResult<EmbyItem> result = getLibraryItemsPaged(libraryId, startIndex, limit);
        return result.getItems();
    }

    @Override
    public PagedResult<EmbyItem> getLibraryItemsPaged(String libraryId, Integer startIndex, Integer limit) {
        log.info("开始获取媒体库[{}]的媒体项, startIndex={}, limit={}", libraryId, startIndex, limit);

        String userId = embyAuthService.getUserId();

        Map<String, Object> params = new HashMap<>();
        params.put("ParentId", libraryId);
        params.put("Recursive", true);
        // 只请求必要的字段，减少服务器负载
        params.put("Fields", "Path,Genres,ProductionYear,CommunityRating");
        // 排除Episode类型，只显示Movie和Series
        params.put("ExcludeItemTypes", "Episode");

        if (startIndex != null) {
            params.put("StartIndex", startIndex);
        }
        if (limit != null) {
            params.put("Limit", limit);
        }

        String path = "/Users/" + userId + "/Items";

        // 对于大量数据的请求，使用更长的超时时间（60秒）
        int timeout = 60000;
        JSONObject response = sendGetRequest(path, params, timeout);

        // 获取总数
        Integer totalCount = response.getInt("TotalRecordCount");
        if (totalCount == null) {
            totalCount = 0;
        }

        JSONArray items = response.getJSONArray("Items");
        List<EmbyItem> embyItems = new ArrayList<>();

        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                EmbyItem embyItem = parseEmbyItem(item);
                embyItems.add(embyItem);
            }
        }

        log.info("成功获取媒体库[{}]的{}个媒体项，总数: {}", libraryId, embyItems.size(), totalCount);

        return new PagedResult<>(embyItems, totalCount, startIndex, limit);
    }

    @Override
    public EmbyItem getItemDetail(String itemId) {
        log.info("开始获取媒体项[{}]的详情", itemId);

        String path = "/Items/" + itemId;
        Map<String, Object> params = new HashMap<>();
        params.put("Fields", "Path,MediaSources,Genres,Tags,Studios,People,Overview");

        try {
            JSONObject response = sendGetRequest(path, params);
            EmbyItem item = parseEmbyItem(response);

            log.info("成功获取媒体项[{}]的详情: name={}, type={}", itemId, item.getName(), item.getType());
            return item;
        } catch (BusinessException e) {
            log.error("获取媒体项[{}]详情失败: {}", itemId, e.getMessage());
            // 如果是 404 错误，尝试使用用户ID的方式获取
            if (e.getMessage().contains("404")) {
                log.info("尝试使用用户ID方式获取媒体项[{}]详情", itemId);
                try {
                    String userId = embyAuthService.getUserId();
                    path = "/Users/" + userId + "/Items/" + itemId;
                    JSONObject response = sendGetRequest(path, params);
                    EmbyItem item = parseEmbyItem(response);
                    log.info("成功通过用户ID获取媒体项[{}]的详情", itemId);
                    return item;
                } catch (Exception ex) {
                    log.error("通过用户ID获取媒体项[{}]详情也失败: {}", itemId, ex.getMessage());
                    throw new BusinessException("无法获取媒体项详情，该项目可能不存在或无权访问");
                }
            }
            throw e;
        }
    }

    @Override
    public List<EmbyItem> getSeriesEpisodes(String seriesId) {
        log.info("开始获取电视剧[{}]的所有剧集", seriesId);

        String userId = embyAuthService.getUserId();
        String path = "/Shows/" + seriesId + "/Episodes";
        Map<String, Object> params = new HashMap<>();
        params.put("UserId", userId);
        params.put("Fields", "Path,MediaSources,Overview");

        try {
            JSONObject response = sendGetRequest(path, params);
            JSONArray items = response.getJSONArray("Items");

            List<EmbyItem> episodes = new ArrayList<>();
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject itemJson = items.getJSONObject(i);
                    EmbyItem episode = parseEmbyItem(itemJson);
                    episodes.add(episode);
                }
            }

            log.info("成功获取电视剧[{}]的剧集，共{}集", seriesId, episodes.size());
            return episodes;
        } catch (Exception e) {
            log.error("获取电视剧[{}]的剧集失败: {}", seriesId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<EmbyGenre> getAllGenres() {
        log.info("开始获取所有类型");

        String userId = embyAuthService.getUserId();

        String path = "/Genres";
        Map<String, Object> params = new HashMap<>();
        params.put("UserId", userId);
        params.put("Recursive", true);

        JSONObject response = sendGetRequest(path, params);
        JSONArray items = response.getJSONArray("Items");

        return parseGenreList(items, "Genre");
    }

    @Override
    public List<EmbyGenre> getAllTags() {
        log.info("开始获取所有标签");

        String userId = embyAuthService.getUserId();

        String path = "/Tags";
        Map<String, Object> params = new HashMap<>();
        params.put("UserId", userId);

        JSONObject response = sendGetRequest(path, params);
        JSONArray items = response.getJSONArray("Items");

        return parseGenreList(items, "Tag");
    }

    @Override
    public List<EmbyGenre> getAllStudios() {
        log.info("开始获取所有工作室");

        String userId = embyAuthService.getUserId();

        String path = "/Studios";
        Map<String, Object> params = new HashMap<>();
        params.put("UserId", userId);

        JSONObject response = sendGetRequest(path, params);
        JSONArray items = response.getJSONArray("Items");

        return parseGenreList(items, "Studio");
    }

    @Override
    public List<EmbyItem> searchItems(String keyword) {
        log.info("开始搜索媒体项, 关键词: {}", keyword);

        String userId = embyAuthService.getUserId();

        String path = "/Users/" + userId + "/Items";
        Map<String, Object> params = new HashMap<>();
        params.put("SearchTerm", keyword);
        params.put("Recursive", true);
        params.put("Fields", "Path,MediaSources,Genres,Tags,Studios,People,Overview");

        JSONObject response = sendGetRequest(path, params);
        JSONArray items = response.getJSONArray("Items");

        if (items == null || items.isEmpty()) {
            log.warn("未找到匹配的媒体项");
            return new ArrayList<>();
        }

        List<EmbyItem> embyItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            EmbyItem embyItem = parseEmbyItem(item);
            embyItems.add(embyItem);
        }

        log.info("搜索到{}个匹配的媒体项", embyItems.size());
        return embyItems;
    }

    @Override
    public Map<String, Object> getServerInfo() {
        log.info("开始获取Emby服务器信息");

        String path = "/System/Info";
        JSONObject response = sendGetRequest(path, null);

        Map<String, Object> info = new HashMap<>();
        info.put("serverName", response.getStr("ServerName"));
        info.put("version", response.getStr("Version"));
        info.put("operatingSystem", response.getStr("OperatingSystem"));
        info.put("id", response.getStr("Id"));
        info.put("localAddress", response.getStr("LocalAddress"));
        info.put("wanAddress", response.getStr("WanAddress"));

        log.info("成功获取服务器信息: {}", info.get("serverName"));
        return info;
    }

    @Override
    public boolean testConnection() {
        try {
            log.info("开始测试Emby连接");
            getServerInfo();
            log.info("Emby连接测试成功");
            return true;
        } catch (Exception e) {
            log.error("Emby连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> syncAllLibraries() {
        log.info("开始同步所有媒体库数据");

        Map<String, Object> result = new HashMap<>();
        int totalLibraries = 0;
        int totalItems = 0;

        try {
            // 获取所有媒体库
            List<EmbyLibrary> libraries = getAllLibraries();
            totalLibraries = libraries.size();

            Map<String, Integer> libraryItemCounts = new HashMap<>();

            // 遍历每个媒体库获取媒体项
            for (EmbyLibrary library : libraries) {
                List<EmbyItem> items = getLibraryItems(library.getId());
                int itemCount = items.size();
                totalItems += itemCount;

                libraryItemCounts.put(library.getName(), itemCount);
                library.setItemCount(itemCount);
            }

            result.put("success", true);
            result.put("totalLibraries", totalLibraries);
            result.put("totalItems", totalItems);
            result.put("libraries", libraries);
            result.put("libraryItemCounts", libraryItemCounts);

            log.info("同步完成: {}个媒体库, {}个媒体项", totalLibraries, totalItems);

        } catch (Exception e) {
            log.error("同步媒体库数据失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取第一个用户ID
     */
    private String getFirstUserId() {
        String path = "/Users";
        JSONArray users = sendGetRequestArray(path, null);

        if (users == null || users.isEmpty()) {
            throw new BusinessException("未找到任何用户");
        }

        return users.getJSONObject(0).getStr("Id");
    }

    /**
     * 解析媒体项
     */
    private EmbyItem parseEmbyItem(JSONObject json) {
        EmbyItem item = new EmbyItem();

        item.setId(json.getStr("Id"));
        item.setName(json.getStr("Name"));
        item.setOriginalTitle(json.getStr("OriginalTitle"));
        item.setType(json.getStr("Type"));
        item.setMediaType(json.getStr("MediaType"));
        item.setParentId(json.getStr("ParentId"));

        // 解析剧集信息（仅Episode类型有效）
        item.setSeriesId(json.getStr("SeriesId"));
        item.setSeriesName(json.getStr("SeriesName"));
        item.setParentIndexNumber(json.getInt("ParentIndexNumber"));
        item.setIndexNumber(json.getInt("IndexNumber"));

        item.setPath(json.getStr("Path"));
        item.setProductionYear(json.getInt("ProductionYear"));
        item.setPremiereDate(json.getStr("PremiereDate"));
        item.setCommunityRating(json.getDouble("CommunityRating"));
        item.setOfficialRating(json.getStr("OfficialRating"));
        item.setOverview(json.getStr("Overview"));
        item.setRunTimeTicks(json.getLong("RunTimeTicks"));
        item.setPlayed(json.getBool("Played", false));
        item.setPlayCount(json.getInt("PlayCount", 0));
        item.setDateCreated(json.getStr("DateCreated"));
        item.setDateModified(json.getStr("DateModified"));

        // 解析标签
        JSONArray tags = json.getJSONArray("Tags");
        if (tags != null && !tags.isEmpty()) {
            item.setTags(tags.toList(String.class));
        }

        // 解析类型
        JSONArray genres = json.getJSONArray("Genres");
        if (genres != null && !genres.isEmpty()) {
            item.setGenres(genres.toList(String.class));
        }

        // 解析工作室
        JSONArray studios = json.getJSONArray("Studios");
        if (studios != null && !studios.isEmpty()) {
            List<String> studioNames = new ArrayList<>();
            for (int i = 0; i < studios.size(); i++) {
                studioNames.add(studios.getJSONObject(i).getStr("Name"));
            }
            item.setStudios(studioNames);
        }

        // 解析演员
        JSONArray people = json.getJSONArray("People");
        if (people != null && !people.isEmpty()) {
            List<String> peopleNames = new ArrayList<>();
            for (int i = 0; i < people.size(); i++) {
                peopleNames.add(people.getJSONObject(i).getStr("Name"));
            }
            item.setPeople(peopleNames);
        }

        // 解析媒体源
        JSONArray mediaSources = json.getJSONArray("MediaSources");
        if (mediaSources != null && !mediaSources.isEmpty()) {
            List<EmbyItem.MediaSource> sources = new ArrayList<>();
            for (int i = 0; i < mediaSources.size(); i++) {
                JSONObject source = mediaSources.getJSONObject(i);
                EmbyItem.MediaSource ms = new EmbyItem.MediaSource();
                ms.setId(source.getStr("Id"));
                ms.setPath(source.getStr("Path"));
                ms.setContainer(source.getStr("Container"));
                ms.setSize(source.getLong("Size"));
                ms.setBitrate(source.getLong("Bitrate"));
                sources.add(ms);

                // 设置文件大小（使用第一个媒体源的大小）
                if (i == 0) {
                    item.setSize(ms.getSize());
                }
            }
            item.setMediaSources(sources);
        }

        return item;
    }

    /**
     * 解析类型/标签列表
     */
    private List<EmbyGenre> parseGenreList(JSONArray items, String type) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<EmbyGenre> genres = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            EmbyGenre genre = new EmbyGenre();
            genre.setId(item.getStr("Id"));
            genre.setName(item.getStr("Name"));
            genre.setType(type);

            // 某些API返回会包含ItemCount
            Integer itemCount = item.getInt("ItemCount");
            if (itemCount != null) {
                genre.setItemCount(itemCount);
            }

            genres.add(genre);
        }

        log.info("成功获取{}个{}", genres.size(), type);
        return genres;
    }

    @Override
    public Map<String, String> getDownloadUrls(String itemId) {
        log.info("获取媒体项下载URL: itemId={}", itemId);

        String accessToken = embyAuthService.getAccessToken();
        String baseUrl = embyAuthService.getServerUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        Map<String, String> urls = new HashMap<>();

        // 1. 直接下载URL（需要下载权限）
        String downloadUrl = String.format("%s/Items/%s/Download?api_key=%s",
            baseUrl, itemId, accessToken);
        urls.put("downloadUrl", downloadUrl);

        // 2. 直接流URL（Static=true表示不转码）
        String directStreamUrl = String.format("%s/Videos/%s/stream?api_key=%s&Static=true&MediaSourceId=%s",
            baseUrl, itemId, accessToken, itemId);
        urls.put("directStreamUrl", directStreamUrl);

        // 3. HLS流URL（m3u8格式）
        String hlsStreamUrl = String.format("%s/Videos/%s/master.m3u8?api_key=%s&MediaSourceId=%s",
            baseUrl, itemId, accessToken, itemId);
        urls.put("hlsStreamUrl", hlsStreamUrl);

        // 4. 播放URL（可能会转码）
        String playUrl = String.format("%s/Videos/%s/stream?api_key=%s",
            baseUrl, itemId, accessToken);
        urls.put("playUrl", playUrl);

        log.info("生成下载URL成功: {}", urls);
        return urls;
    }

    @Override
    public void proxyDownload(String itemId, String filename, javax.servlet.http.HttpServletResponse response) throws Exception {
        log.info("开始代理下载: itemId={}, filename={}", itemId, filename);

        String accessToken = embyAuthService.getAccessToken();
        String userId = embyAuthService.getUserId();
        String baseUrl = embyAuthService.getServerUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 构建流URL（模拟播放器请求，Static=true表示不转码）
        String streamUrl = String.format("%s/Videos/%s/stream?api_key=%s&Static=true&MediaSourceId=%s",
            baseUrl, itemId, accessToken, itemId);

        log.info("请求流URL: {}", streamUrl);

        try {
            // 伪装成播放器的请求头
            String deviceId = "30c9d308e74a46a1811c851bf76a8f77";
            String forwardVersion = "1.3.14";
            String embyAuth = String.format(
                "MediaBrowser Token=\"%s\", Emby UserId=\"%s\", Client=\"Forward\", Device=\"iPhone\", DeviceId=\"%s\", Version=\"%s\"",
                accessToken, userId, deviceId, forwardVersion
            );

            // 使用HttpRequest发送请求
            HttpResponse embyResponse = HttpRequest.get(streamUrl)
                .header("X-Emby-Authorization", embyAuth)
                .header("X-Emby-Token", accessToken)
                .header("User-Agent", "Forward/1.3.14 (iPhone; iOS 17.0)")
                .timeout(300000) // 5分钟超时
                .execute();

            if (!embyResponse.isOk()) {
                log.error("Emby返回错误状态: {}", embyResponse.getStatus());
                response.setStatus(embyResponse.getStatus());
                response.getWriter().write("Emby服务器返回错误: " + embyResponse.getStatus());
                return;
            }

            // 获取内容类型
            String contentType = embyResponse.header("Content-Type");
            if (contentType == null || contentType.isEmpty()) {
                contentType = "video/mp4";
            }

            // 获取文件大小
            String contentLength = embyResponse.header("Content-Length");

            log.info("Emby响应 - ContentType: {}, ContentLength: {}", contentType, contentLength);

            // 设置响应头
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            if (contentLength != null && !contentLength.isEmpty()) {
                response.setHeader("Content-Length", contentLength);
            }
            response.setHeader("Accept-Ranges", "bytes");

            // 流式传输数据
            byte[] buffer = new byte[8192];
            int bytesRead;
            java.io.InputStream inputStream = embyResponse.bodyStream();
            java.io.OutputStream outputStream = response.getOutputStream();

            long totalBytes = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;

                // 每10MB打印一次进度
                if (totalBytes % (10 * 1024 * 1024) == 0) {
                    log.info("已传输: {} MB", totalBytes / (1024 * 1024));
                }
            }

            outputStream.flush();
            log.info("代理下载完成，总大小: {} MB", totalBytes / (1024 * 1024));

        } catch (Exception e) {
            log.error("代理下载失败", e);
            throw e;
        }
    }

    @Override
    public Map<String, Object> downloadToServer(String itemId) throws Exception {
        log.info("开始下载到服务器: itemId={}", itemId);

        // 获取媒体项详情
        EmbyItem item = getItemDetail(itemId);
        if (item == null) {
            throw new BusinessException("媒体项不存在: " + itemId);
        }

        // 如果是电视剧，下载所有剧集
        if ("Series".equals(item.getType())) {
            return downloadSeriesAllEpisodes(item);
        }

        // 如果是季，不支持
        if ("Season".equals(item.getType())) {
            throw new BusinessException("不支持下载季，请选择电视剧或具体的剧集进行下载");
        }

        // 如果是剧集（Episode），需要创建电视剧目录
        if ("Episode".equals(item.getType())) {
            return downloadEpisodeWithSeriesDir(item);
        }

        // 只支持 Movie 和 Episode
        if (!"Movie".equals(item.getType())) {
            throw new BusinessException("只支持下载电影(Movie)、电视剧(Series)和剧集(Episode)，当前类型: " + item.getType());
        }

        // 下载单个文件（Movie）
        return downloadSingleItem(item);
    }

    /**
     * 下载单个剧集，并创建电视剧目录
     */
    private Map<String, Object> downloadEpisodeWithSeriesDir(EmbyItem episode) throws Exception {
        log.info("下载单个剧集: {}", episode.getName());

        // 获取剧集的 SeriesId（父电视剧ID）
        String seriesId = episode.getSeriesId();
        if (seriesId == null || seriesId.isEmpty()) {
            log.warn("剧集没有 SeriesId，下载到根目录");
            return downloadSingleItem(episode);
        }

        // 获取电视剧信息
        EmbyItem series = null;
        try {
            series = getItemDetail(seriesId);
        } catch (Exception e) {
            log.warn("无法获取电视剧信息: {}, 下载到根目录", e.getMessage());
            return downloadSingleItem(episode);
        }

        if (series == null) {
            log.warn("电视剧不存在，下载到根目录");
            return downloadSingleItem(episode);
        }

        // 创建电视剧目录（使用统一的目录名生成逻辑）
        String seriesDir = buildSeriesDirectory(series);

        // 下载剧集到电视剧目录
        return downloadSingleItem(episode, seriesDir);
    }

    /**
     * 构建电视剧目录路径（统一的目录名生成逻辑）
     */
    private String buildSeriesDirectory(EmbyItem series) {
        String seriesName = series.getName();
        log.info("原始电视剧名称: {}", seriesName);

        if (seriesName == null || seriesName.isEmpty()) {
            seriesName = "unknown_series";
        }
        if (series.getProductionYear() != null) {
            seriesName += " (" + series.getProductionYear() + ")";
        }

        // 清理文件名中的非法字符
        seriesName = seriesName.replaceAll("[\\\\/:*?\"<>|]", "_");
        log.info("清理后的电视剧名称: {}", seriesName);

        // 使用 Path 创建目录，确保UTF-8编码
        java.nio.file.Path seriesDirPath = java.nio.file.Paths.get(getEmbyDownloadDir(), seriesName);
        String seriesDir = seriesDirPath.toString();
        log.info("完整目录路径: {}", seriesDir);

        java.io.File dir = seriesDirPath.toFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            log.info("创建电视剧目录: {}, 结果: {}", seriesDir, created);
        } else {
            log.info("电视剧目录已存在: {}", seriesDir);
        }

        return seriesDir;
    }

    /**
     * 下载电视剧的所有剧集
     */
    private Map<String, Object> downloadSeriesAllEpisodes(EmbyItem series) throws Exception {
        log.info("开始下载电视剧所有剧集: {}", series.getName());

        // 获取所有剧集
        List<EmbyItem> episodes = getSeriesEpisodes(series.getId());
        if (episodes == null || episodes.isEmpty()) {
            throw new BusinessException("该电视剧没有剧集");
        }

        log.info("找到 {} 集", episodes.size());

        // 创建电视剧目录（使用统一的目录名生成逻辑）
        String seriesDir = buildSeriesDirectory(series);

        // 下载每一集
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        List<String> successFiles = new ArrayList<>();
        List<Map<String, String>> failedEpisodes = new ArrayList<>();

        for (int i = 0; i < episodes.size(); i++) {
            EmbyItem episode = episodes.get(i);
            log.info("下载进度: [{}/{}] {}", i + 1, episodes.size(), episode.getName());

            // 检查是否有媒体源，如果没有，尝试重新获取详情
            if (episode.getMediaSources() == null || episode.getMediaSources().isEmpty()) {
                log.warn("剧集 {} 没有媒体源信息，尝试重新获取详情...", episode.getName());
                log.info("剧集ID: {}, 当前媒体源: {}", episode.getId(), episode.getMediaSources());
                try {
                    EmbyItem detailedEpisode = getItemDetail(episode.getId());
                    if (detailedEpisode != null) {
                        log.info("重新获取的剧集详情 - 媒体源数量: {}",
                            detailedEpisode.getMediaSources() != null ? detailedEpisode.getMediaSources().size() : 0);
                        if (detailedEpisode.getMediaSources() != null && !detailedEpisode.getMediaSources().isEmpty()) {
                            log.info("重新获取成功，找到媒体源");
                            episode = detailedEpisode;
                        } else {
                            log.warn("⊘ 跳过（重新获取后仍无媒体源）: {}", episode.getName());
                            skippedCount++;
                            Map<String, String> failedInfo = new HashMap<>();
                            failedInfo.put("name", episode.getName());
                            failedInfo.put("reason", "无媒体源");
                            failedEpisodes.add(failedInfo);
                            continue;
                        }
                    } else {
                        log.warn("⊘ 跳过（重新获取返回null）: {}", episode.getName());
                        skippedCount++;
                        Map<String, String> failedInfo = new HashMap<>();
                        failedInfo.put("name", episode.getName());
                        failedInfo.put("reason", "获取详情返回null");
                        failedEpisodes.add(failedInfo);
                        continue;
                    }
                } catch (Exception e) {
                    log.error("重新获取详情失败: {}", e.getMessage());
                    skippedCount++;
                    Map<String, String> failedInfo = new HashMap<>();
                    failedInfo.put("name", episode.getName());
                    failedInfo.put("reason", "获取详情失败: " + e.getMessage());
                    failedEpisodes.add(failedInfo);
                    continue;
                }
            }

            try {
                Map<String, Object> result = downloadSingleItem(episode, seriesDir);
                if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                    successCount++;
                    successFiles.add((String) result.get("filename"));
                    log.info("✓ 下载成功: {}", episode.getName());
                } else {
                    failedCount++;
                    Map<String, String> failedInfo = new HashMap<>();
                    failedInfo.put("name", episode.getName());
                    failedInfo.put("reason", "下载失败");
                    failedEpisodes.add(failedInfo);
                    log.warn("✗ 下载失败: {}", episode.getName());
                }
            } catch (Exception e) {
                failedCount++;
                Map<String, String> failedInfo = new HashMap<>();
                failedInfo.put("name", episode.getName());
                failedInfo.put("reason", e.getMessage());
                failedEpisodes.add(failedInfo);
                log.error("✗ 下载异常: {}, 错误: {}", episode.getName(), e.getMessage());
            }
        }

        log.info("电视剧下载完成！成功: {}, 失败: {}, 跳过: {}", successCount, failedCount, skippedCount);

        // 保存 Series 的下载历史记录
        if (successCount > 0) {
            // 如果有至少一集下载成功，标记 Series 为成功
            String seriesStatus = (failedCount == 0 && skippedCount == 0) ? "success" : "success";
            saveDownloadHistory(series.getId(), seriesStatus, seriesDir, 0L,
                String.format("下载了 %d 集，成功 %d 集，失败 %d 集", episodes.size(), successCount, failedCount + skippedCount));
            log.info("保存 Series 下载历史记录: seriesId={}, status={}", series.getId(), seriesStatus);
        } else {
            // 如果一集都没下载成功，标记 Series 为失败
            saveDownloadHistory(series.getId(), "failed", seriesDir, 0L,
                String.format("下载失败，共 %d 集，失败 %d 集，跳过 %d 集", episodes.size(), failedCount, skippedCount));
            log.info("保存 Series 下载历史记录: seriesId={}, status=failed", series.getId());
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", failedCount == 0 && skippedCount == 0);
        result.put("type", "series");
        result.put("seriesName", series.getName());
        result.put("seriesDir", seriesDir);
        result.put("totalEpisodes", episodes.size());
        result.put("successCount", successCount);
        result.put("failedCount", failedCount + skippedCount);
        result.put("successFiles", successFiles);
        result.put("failedEpisodes", failedEpisodes);

        return result;
    }

    /**
     * 下载单个媒体项（Movie 或 Episode）
     */
    private Map<String, Object> downloadSingleItem(EmbyItem item) throws Exception {
        return downloadSingleItem(item, getEmbyDownloadDir());
    }

    /**
     * 下载单个媒体项到指定目录
     */
    private Map<String, Object> downloadSingleItem(EmbyItem item, String downloadDir) throws Exception {
        String accessToken = embyAuthService.getAccessToken();
        String userId = embyAuthService.getUserId();
        String baseUrl = embyAuthService.getServerUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 构建文件名
        String filename = item.getName();
        log.info("原始文件名: {}", filename);

        if (filename == null || filename.isEmpty()) {
            filename = "unknown";
        }
        if (item.getProductionYear() != null && "Movie".equals(item.getType())) {
            filename += " (" + item.getProductionYear() + ")";
        }

        // 清理文件名中的非法字符（只替换 Windows/Linux 文件系统不允许的字符）
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        log.info("清理后的文件名: {}", filename);

        filename += ".mp4";
        log.info("最终文件名: {}", filename);

        // 创建下载目录
        java.nio.file.Path dirPath = java.nio.file.Paths.get(downloadDir);
        if (!java.nio.file.Files.exists(dirPath)) {
            try {
                java.nio.file.Files.createDirectories(dirPath);
                log.info("创建下载目录: {}", downloadDir);
            } catch (Exception e) {
                log.error("创建目录失败: {}", e.getMessage());
                throw new BusinessException("创建目录失败: " + e.getMessage());
            }
        }

        // 目标文件路径 - 使用 NIO Path 确保 UTF-8
        java.nio.file.Path targetPath = dirPath.resolve(filename);
        String filePath = targetPath.toString();

        log.info("目标文件路径: {}", filePath);
        log.info("文件名: {}", targetPath.getFileName().toString());

        // 如果文件已存在，添加序号
        int counter = 1;
        while (java.nio.file.Files.exists(targetPath)) {
            String nameWithoutExt = filename.substring(0, filename.lastIndexOf("."));
            String ext = filename.substring(filename.lastIndexOf("."));
            String newFilename = nameWithoutExt + "_" + counter + ext;
            targetPath = dirPath.resolve(newFilename);
            filePath = targetPath.toString();
            counter++;
        }

        log.info("最终文件路径: {}", filePath);

        // 获取MediaSourceId
        String mediaSourceId = item.getId();
        if (item.getMediaSources() != null && !item.getMediaSources().isEmpty()) {
            // 使用第一个媒体源的ID
            EmbyItem.MediaSource firstSource = item.getMediaSources().get(0);
            if (firstSource.getId() != null && !firstSource.getId().isEmpty()) {
                mediaSourceId = firstSource.getId();
                log.info("使用媒体源ID: {}", mediaSourceId);
            }
        }

        // 尝试多种URL方式（不在URL中包含api_key，而是通过请求头传递）
        // 按成功率排序，优先尝试stream端点
        String[] streamUrls = {
            // 方式1：stream端点（成功率最高）
            String.format("%s/Videos/%s/stream?Static=true",
                baseUrl, item.getId()),
            // 方式2：带MediaSourceId参数（使用itemId）
            String.format("%s/Videos/%s/stream?Static=true&MediaSourceId=%s",
                baseUrl, item.getId(), item.getId()),
            // 方式3：带MediaSourceId参数（使用实际的mediaSourceId）
            String.format("%s/Videos/%s/stream?Static=true&MediaSourceId=%s",
                baseUrl, item.getId(), mediaSourceId),
            // 方式4：使用下载端点（可能需要特殊权限）
            String.format("%s/Items/%s/Download",
                baseUrl, item.getId())
        };

        // 完全模仿Forward客户端（iPhone）
        String deviceId = "gdupload-" + System.currentTimeMillis();
        String clientVersion = "1.3.14";
        String embyAuth = String.format(
            "MediaBrowser Token=\"%s\", Emby UserId=\"%s\", Client=\"Forward\", Device=\"iPhone\", DeviceId=\"%s\", Version=\"%s\"",
            accessToken, userId, deviceId, clientVersion
        );

        Exception lastException = null;

        // 尝试所有URL方式
        for (int urlIndex = 0; urlIndex < streamUrls.length; urlIndex++) {
            String streamUrl = streamUrls[urlIndex];
            log.info("尝试方式 {}: {}", urlIndex + 1, streamUrl);

            java.net.HttpURLConnection connection = null;
            try {
                // 使用HttpURLConnection进行流式下载，避免内存溢出
                java.net.URL url = new java.net.URL(streamUrl);
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // 完全模仿Forward客户端的请求头
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Emby-Authorization", embyAuth);
                connection.setRequestProperty("X-Emby-Token", accessToken);
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("User-Agent", "Forward-Standard/" + clientVersion);
                connection.setRequestProperty("Accept-Language", "zh-CN,zh-Hans;q=0.9");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
                connection.setRequestProperty("Connection", "keep-alive");

                connection.setConnectTimeout(30000); // 30秒连接超时
                connection.setReadTimeout(300000); // 5分钟读取超时

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    String errorMsg = "状态码: " + responseCode;
                    try (java.io.InputStream errorStream = connection.getErrorStream()) {
                        if (errorStream != null) {
                            byte[] errorBytes = new byte[1024];
                            int len = errorStream.read(errorBytes);
                            if (len > 0) {
                                errorMsg += ", 响应: " + new String(errorBytes, 0, len, "UTF-8");
                            }
                        }
                    }
                    log.warn("方式 {} 失败，{}", urlIndex + 1, errorMsg);
                    lastException = new BusinessException("Emby服务器返回错误: " + errorMsg);
                    continue;
                }

                log.info("方式 {} 成功！Emby响应成功，开始下载...", urlIndex + 1);

                // 获取文件大小
                long totalSize = connection.getContentLengthLong();
                if (totalSize > 0) {
                    log.info("文件大小: {} MB", totalSize / (1024 * 1024));
                }

                // 流式写入文件 - 使用 NIO Files 确保 UTF-8 文件名
                log.info("开始写入文件: {}", targetPath.toString());
                try (java.io.InputStream inputStream = connection.getInputStream();
                     java.io.OutputStream outputStream = java.nio.file.Files.newOutputStream(targetPath)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long downloadedBytes = 0;
                    long lastLogTime = System.currentTimeMillis();

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        // 每5秒打印一次进度
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastLogTime > 5000) {
                            if (totalSize > 0) {
                                int progress = (int) ((downloadedBytes * 100) / totalSize);
                                log.info("下载进度: {}% ({} MB / {} MB)",
                                    progress,
                                    downloadedBytes / (1024 * 1024),
                                    totalSize / (1024 * 1024));
                            } else {
                                log.info("已下载: {} MB", downloadedBytes / (1024 * 1024));
                            }
                            lastLogTime = currentTime;
                        }
                    }

                    log.info("下载完成！总大小: {} MB", downloadedBytes / (1024 * 1024));

                    // 验证创建的文件
                    if (java.nio.file.Files.exists(targetPath)) {
                        String actualFileName = targetPath.getFileName().toString();
                        log.info("文件创建成功，实际文件名: {}", actualFileName);
                        log.info("文件路径: {}", targetPath.toAbsolutePath());
                        log.info("文件大小: {} MB", java.nio.file.Files.size(targetPath) / (1024 * 1024));
                    }

                    // 保存下载记录
                    saveDownloadHistory(item.getId(), "success", filePath, downloadedBytes, null);

                    // 构建返回结果
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("filePath", filePath);
                    result.put("filename", targetPath.getFileName().toString());
                    result.put("size", downloadedBytes);
                    result.put("sizeMB", downloadedBytes / (1024 * 1024));
                    result.put("itemId", item.getId());
                    result.put("itemName", item.getName());
                    result.put("downloadDir", downloadDir); // 添加下载目录信息

                    return result;
                }

            } catch (Exception e) {
                log.error("方式 {} 异常: {}", urlIndex + 1, e.getMessage());
                lastException = e;
                // 删除可能创建的不完整文件
                try {
                    if (java.nio.file.Files.exists(targetPath)) {
                        java.nio.file.Files.delete(targetPath);
                        log.info("已删除不完整文件");
                    }
                } catch (Exception deleteEx) {
                    log.warn("删除不完整文件失败: {}", deleteEx.getMessage());
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        // 所有方式都失败
        String errorMsg;
        if (lastException != null) {
            errorMsg = lastException.getMessage();
            // 保存失败记录
            saveDownloadHistory(item.getId(), "failed", null, null, errorMsg);
            throw lastException;
        } else {
            errorMsg = "所有下载方式都失败";
            // 保存失败记录
            saveDownloadHistory(item.getId(), "failed", null, null, errorMsg);
            throw new BusinessException(errorMsg);
        }
    }

    /**
     * 保存下载历史记录
     */
    private void saveDownloadHistory(String embyItemId, String status, String filePath, Long fileSize, String errorMessage) {
        try {
            EmbyConfig config = embyConfigService.getDefaultConfig();
            if (config == null) {
                log.warn("无法保存下载记录：未找到默认Emby配置");
                return;
            }

            EmbyDownloadHistory history = new EmbyDownloadHistory();
            history.setEmbyItemId(embyItemId);
            history.setEmbyConfigId(config.getId());
            history.setDownloadStatus(status);
            history.setFilePath(filePath);
            history.setFileSize(fileSize);
            history.setErrorMessage(errorMessage);
            history.setCreateTime(LocalDateTime.now());
            history.setUpdateTime(LocalDateTime.now());

            downloadHistoryMapper.insert(history);
            log.info("保存下载记录成功: itemId={}, status={}", embyItemId, status);
        } catch (Exception e) {
            log.error("保存下载记录失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void downloadToServerAsync(String itemId) {
        // 在新线程中执行下载任务
        new Thread(() -> {
            try {
                log.info("========================================");
                log.info("异步下载任务开始: itemId={}", itemId);
                log.info("========================================");

                Map<String, Object> result = downloadToServer(itemId);

                log.info("========================================");
                log.info("异步下载任务完成！");
                if (result.get("type") != null && "series".equals(result.get("type"))) {
                    log.info("电视剧: {}", result.get("seriesName"));
                    log.info("保存目录: {}", result.get("seriesDir"));
                    log.info("总集数: {}", result.get("totalEpisodes"));
                    log.info("成功: {} 集", result.get("successCount"));
                    log.info("失败: {} 集", result.get("failedCount"));
                } else {
                    log.info("文件: {}", result.get("filename"));
                    log.info("路径: {}", result.get("filePath"));
                    log.info("大小: {} MB", result.get("sizeMB"));
                }
                log.info("========================================");

            } catch (Exception e) {
                log.error("========================================");
                log.error("异步下载任务失败: {}", e.getMessage(), e);
                log.error("========================================");
            }
        }, "Emby-Download-" + itemId).start();

        log.info("异步下载任务已启动，线程名: Emby-Download-{}", itemId);
    }

    // 批量下载进度信息
    private volatile Map<String, Object> batchDownloadProgress = new ConcurrentHashMap<>();

    @Override
    public Long batchDownloadToServerAsync(List<String> itemIds) {
        return batchDownloadToServerAsync(itemIds, null);
    }

    /**
     * 批量下载到服务器（异步，支持断点续传）
     *
     * @param itemIds 媒体项ID列表
     * @param existingTaskId 已存在的任务ID（用于恢复任务），如果为null则创建新任务
     * @return 任务ID
     */
    public Long batchDownloadToServerAsync(List<String> itemIds, Long existingTaskId) {
        Long taskId;
        List<FileInfo> fileInfoList;

        if (existingTaskId != null) {
            // 恢复已存在的任务
            taskId = existingTaskId;
            fileInfoList = fileInfoService.getTaskFiles(taskId);
            log.info("恢复Emby下载任务: taskId={}, 共 {} 个媒体项", taskId, fileInfoList.size());

            // 更新任务状态为运行中
            uploadTaskService.updateTaskStatus(taskId, 1, null);
        } else {
            // 创建新任务
            // 1. 收集媒体项名称，用于自动生成任务名
            List<String> itemNames = new ArrayList<>();
            for (String itemId : itemIds) {
                try {
                    EmbyItem detail = getItemDetail(itemId);
                    if (detail != null && detail.getName() != null) {
                        itemNames.add(detail.getName());
                    }
                } catch (Exception e) {
                    // 获取名称失败不影响
                }
            }

            // 自动生成任务名
            String taskName;
            if (itemNames.size() == 1) {
                taskName = "Emby下载 - " + itemNames.get(0);
            } else if (itemNames.size() <= 3) {
                taskName = "Emby下载 - " + String.join(", ", itemNames);
            } else {
                taskName = "Emby下载 - " + itemNames.get(0) + " 等" + itemIds.size() + "个媒体项";
            }
            // 截断过长的任务名
            if (taskName.length() > 200) {
                taskName = taskName.substring(0, 197) + "...";
            }

            // 2. 创建 UploadTask（taskType=3）
            taskId = uploadTaskService.createDownloadTask(taskName, getEmbyDownloadDir(), itemIds.size());
            if (taskId == null) {
                throw new BusinessException("创建下载任务失败");
            }

            // 3. 为每个 itemId 创建 FileInfo 行
            fileInfoList = new ArrayList<>();
            for (int i = 0; i < itemIds.size(); i++) {
                FileInfo fileInfo = new FileInfo();
                fileInfo.setTaskId(taskId);
                String name = i < itemNames.size() ? itemNames.get(i) : ("媒体项 " + itemIds.get(i));
                fileInfo.setFileName(name);
                fileInfo.setFilePath(itemIds.get(i)); // 存embyItemId
                fileInfo.setFileSize(0L);
                fileInfo.setStatus(0); // 待下载
                fileInfo.setCreateTime(DateTimeUtil.now());
                fileInfoList.add(fileInfo);
            }
            fileInfoService.batchSaveFiles(taskId, fileInfoList);
        }

        // 4. 初始化进度信息（兼容旧的轮询接口）
        batchDownloadProgress.put("running", true);
        batchDownloadProgress.put("totalCount", fileInfoList.size());
        batchDownloadProgress.put("completedCount", 0);
        batchDownloadProgress.put("successCount", 0);
        batchDownloadProgress.put("failedCount", 0);
        batchDownloadProgress.put("skippedCount", 0);
        batchDownloadProgress.put("currentItemId", "");
        batchDownloadProgress.put("currentItemName", "");
        batchDownloadProgress.put("taskId", taskId);

        // 5. 设置停止标志
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        downloadStopFlags.put(taskId, stopFlag);

        // 6. 启动下载管理线程（5个并发下载）
        final Long finalTaskId = taskId;
        final int CONCURRENT_DOWNLOADS = 5;
        new Thread(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            AtomicInteger skippedCount = new AtomicInteger(0);

            log.info("========================================");
            log.info("批量下载任务开始: taskId={}, 共 {} 个媒体项, 并发数: {}", finalTaskId, fileInfoList.size(), CONCURRENT_DOWNLOADS);
            log.info("========================================");

            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_DOWNLOADS);
            CountDownLatch latch = new CountDownLatch(itemIds.size());

            try {
                for (int i = 0; i < itemIds.size(); i++) {
                    // 检查停止标志
                    if (stopFlag.get()) {
                        log.info("下载任务被停止: taskId={}", finalTaskId);
                        // 剩余的直接countdown
                        for (int j = i; j < itemIds.size(); j++) {
                            latch.countDown();
                        }
                        break;
                    }

                    final int index = i;
                    final String itemId = itemIds.get(i);
                    final FileInfo currentFile = fileInfoList.get(i);

                    executor.submit(() -> {
                        try {
                            if (stopFlag.get()) {
                                return;
                            }

                            // 先检查是否已下载
                            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmbyDownloadHistory> wrapper =
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                            wrapper.eq(EmbyDownloadHistory::getEmbyItemId, itemId)
                                    .eq(EmbyDownloadHistory::getDownloadStatus, "success")
                                    .last("LIMIT 1");
                            EmbyDownloadHistory existing = downloadHistoryMapper.selectOne(wrapper);

                            if (existing != null) {
                                log.info("[{}/{}] 跳过已下载: itemId={}", index + 1, itemIds.size(), itemId);
                                skippedCount.incrementAndGet();
                                batchDownloadProgress.put("skippedCount", skippedCount.get());
                                fileInfoService.updateFileStatus(currentFile.getId(), 4, "已下载，跳过");
                                webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 4, "已下载，跳过");
                                return;
                            }

                            log.info("[{}/{}] 开始下载: itemId={}", index + 1, itemIds.size(), itemId);

                            // 再次检查停止标志（在实际下载前）
                            if (stopFlag.get()) {
                                log.info("[{}/{}] 任务已暂停，跳过下载: itemId={}", index + 1, itemIds.size(), itemId);
                                return;
                            }

                            // 更新FileInfo状态为下载中(1)
                            fileInfoService.updateFileStatus(currentFile.getId(), 1, null);
                            webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 1, null);

                            batchDownloadProgress.put("currentItemName", currentFile.getFileName());

                            Map<String, Object> result = downloadToServer(itemId);

                            // 下载完成后再次检查停止标志
                            if (stopFlag.get()) {
                                log.info("[{}/{}] 任务已暂停，不更新下载结果: itemId={}", index + 1, itemIds.size(), itemId);
                                return;
                            }

                            boolean downloadOk;
                            if ("series".equals(result.get("type"))) {
                                Integer seriesSuccess = (Integer) result.get("successCount");
                                downloadOk = seriesSuccess != null && seriesSuccess > 0;
                            } else {
                                downloadOk = Boolean.TRUE.equals(result.get("success"));
                            }

                            if (downloadOk) {
                                successCount.incrementAndGet();
                                log.info("[{}/{}] 下载成功: itemId={}", index + 1, itemIds.size(), itemId);

                                // 更新FileInfo：设置实际下载的文件路径、文件名和相对路径
                                log.info("准备更新FileInfo: fileInfoId={}, result包含的key={}",
                                    currentFile.getId(), result.keySet());
                                updateFileInfoAfterDownload(currentFile, result);

                                fileInfoService.updateFileStatus(currentFile.getId(), 2, null);
                                webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 2, null);
                            } else {
                                failedCount.incrementAndGet();
                                log.warn("[{}/{}] 下载失败: itemId={}", index + 1, itemIds.size(), itemId);
                                fileInfoService.updateFileStatus(currentFile.getId(), 3, "下载失败");
                                webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 3, "下载失败");
                            }
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            log.error("[{}/{}] 下载异常: itemId={}, error={}", index + 1, itemIds.size(), itemId, e.getMessage());
                            fileInfoService.updateFileStatus(currentFile.getId(), 3, e.getMessage());
                            webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 3, e.getMessage());
                        } finally {
                            latch.countDown();

                            // 更新进度
                            batchDownloadProgress.put("successCount", successCount.get());
                            batchDownloadProgress.put("failedCount", failedCount.get());

                            int completed = successCount.get() + failedCount.get() + skippedCount.get();
                            batchDownloadProgress.put("completedCount", completed);
                            int taskProgress = (int) ((completed * 100L) / itemIds.size());

                            uploadTaskService.updateTaskProgress(finalTaskId, completed, 0L, taskProgress);
                            webSocketService.pushTaskProgress(finalTaskId, taskProgress,
                                completed, itemIds.size(), 0L, 0L,
                                batchDownloadProgress.getOrDefault("currentItemName", "").toString());

                            // 更新失败数
                            com.gdupload.entity.UploadTask taskEntity = uploadTaskService.getTaskDetail(finalTaskId);
                            if (taskEntity != null) {
                                taskEntity.setFailedCount(failedCount.get());
                                uploadTaskService.updateById(taskEntity);
                            }
                        }
                    });
                }

                // 等待所有下载完成
                latch.await();

                // 更新最终进度
                batchDownloadProgress.put("completedCount", itemIds.size());
                batchDownloadProgress.put("running", false);

                int finalSuccess = successCount.get();
                int finalFailed = failedCount.get();
                int finalSkipped = skippedCount.get();

                // 更新任务最终状态
                if (stopFlag.get()) {
                    log.info("下载任务已停止: taskId={}", finalTaskId);
                } else if (finalFailed == 0) {
                    uploadTaskService.updateTaskStatus(finalTaskId, 2, null);
                    webSocketService.pushTaskStatus(finalTaskId, 2, "下载完成");
                } else {
                    uploadTaskService.updateTaskStatus(finalTaskId, 2,
                        String.format("完成（成功%d，失败%d，跳过%d）", finalSuccess, finalFailed, finalSkipped));
                    webSocketService.pushTaskStatus(finalTaskId, 2, "下载完成（部分失败）");
                }

                // 确保最终进度为100%
                uploadTaskService.updateTaskProgress(finalTaskId,
                    finalSuccess + finalFailed + finalSkipped, 0L, 100);

                log.info("========================================");
                log.info("批量下载任务完成: taskId={}, 成功: {}, 失败: {}, 跳过: {}",
                    finalTaskId, finalSuccess, finalFailed, finalSkipped);
                log.info("========================================");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("批量下载管理线程被中断: taskId={}", finalTaskId);
            } finally {
                executor.shutdownNow();
                downloadStopFlags.remove(finalTaskId);
            }
        }, "Emby-BatchDownload-" + taskId).start();

        log.info("批量下载任务已启动: taskId={}, 共 {} 个媒体项", taskId, itemIds.size());
        return taskId;
    }

    @Override
    public Map<String, Object> getBatchDownloadProgress() {
        if (batchDownloadProgress.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("running", false);
            return empty;
        }
        return new HashMap<>(batchDownloadProgress);
    }

    @Override
    public boolean pauseDownloadTask(Long taskId) {
        AtomicBoolean stopFlag = downloadStopFlags.get(taskId);
        if (stopFlag != null) {
            stopFlag.set(true);
            uploadTaskService.updateTaskStatus(taskId, 3, null); // 已暂停
            webSocketService.pushTaskStatus(taskId, 3, "下载已暂停");
            log.info("暂停下载任务: taskId={}", taskId);
            return true;
        }
        // 任务不在运行中，直接更新数据库状态
        return uploadTaskService.pauseTask(taskId);
    }

    @Override
    public boolean cancelDownloadTask(Long taskId) {
        AtomicBoolean stopFlag = downloadStopFlags.get(taskId);
        if (stopFlag != null) {
            stopFlag.set(true);
            uploadTaskService.updateTaskStatus(taskId, 4, "用户取消"); // 已取消
            webSocketService.pushTaskStatus(taskId, 4, "下载已取消");
            log.info("取消下载任务: taskId={}", taskId);
            return true;
        }
        // 任务不在运行中，直接更新数据库状态
        return uploadTaskService.cancelTask(taskId);
    }

    /**
     * 下载完成后更新FileInfo的实际文件路径和相对路径
     */
    private void updateFileInfoAfterDownload(FileInfo fileInfo, Map<String, Object> downloadResult) {
        try {
            if ("series".equals(downloadResult.get("type"))) {
                // 电视剧：更新为剧集目录路径
                String seriesDir = (String) downloadResult.get("seriesDir");
                if (seriesDir != null) {
                    // 提取剧集文件夹名（例如：{embyDownloadDir}/重庆遇见爱 (2024) → 重庆遇见爱 (2024)）
                    java.nio.file.Path seriesDirPath = java.nio.file.Paths.get(seriesDir);
                    String seriesFolderName = seriesDirPath.getFileName().toString();

                    // 设置相对路径为剧集文件夹名
                    fileInfo.setRelativePath(seriesFolderName);

                    // filePath设置为剧集目录（后续上传时会扫描这个目录下的所有文件）
                    fileInfo.setFilePath(seriesDir);

                    log.info("更新电视剧FileInfo: seriesDir={}, relativePath={}", seriesDir, seriesFolderName);
                }
            } else {
                // 单个文件（电影或单集）
                String filePath = (String) downloadResult.get("filePath");
                String filename = (String) downloadResult.get("filename");
                String downloadDir = (String) downloadResult.get("downloadDir");

                if (filePath != null && filename != null) {
                    // 更新实际文件路径和文件名
                    fileInfo.setFilePath(filePath);
                    fileInfo.setFileName(filename);

                    // 如果下载目录不是根目录，提取相对路径
                    String embyDownloadDir = getEmbyDownloadDir();
                    if (downloadDir != null && !downloadDir.equals(embyDownloadDir)) {
                        // 提取相对于下载根目录的路径
                        String downloadDirPrefix = embyDownloadDir + "/";
                        if (downloadDir.startsWith(downloadDirPrefix)) {
                            String relativePath = downloadDir.substring(downloadDirPrefix.length());
                            fileInfo.setRelativePath(relativePath);
                            log.info("更新单文件FileInfo: filePath={}, relativePath={}", filePath, relativePath);
                        } else {
                            // 文件直接在根目录
                            fileInfo.setRelativePath("");
                            log.info("更新单文件FileInfo: filePath={}, relativePath=空（根目录）", filePath);
                        }
                    } else {
                        // 文件直接在根目录
                        fileInfo.setRelativePath("");
                        log.info("更新单文件FileInfo: filePath={}, relativePath=空（根目录）", filePath);
                    }
                }
            }

            // 保存更新
            fileInfoService.updateById(fileInfo);
        } catch (Exception e) {
            log.error("更新FileInfo失败: fileInfoId={}, error={}", fileInfo.getId(), e.getMessage(), e);
        }
    }
}
