package com.gdupload.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gdupload.common.BusinessException;
import com.gdupload.config.EmbyProperties;
import com.gdupload.dto.*;
import com.gdupload.entity.*;
import com.gdupload.mapper.EmbyDownloadHistoryMapper;
import com.gdupload.mapper.UploadRecordMapper;
import com.gdupload.service.*;
import com.gdupload.util.DateTimeUtil;
import com.gdupload.util.RcloneResult;
import com.gdupload.util.RcloneUtil;
import com.gdupload.util.TaskPauseManager;
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
    private UploadRecordMapper uploadRecordMapper;

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

    @Autowired
    private IGdAccountService gdAccountService;

    @Autowired
    private EmbyProperties embyProperties;

    @Autowired
    private RcloneUtil rcloneUtil;

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
        log.info("下载单个剧集（平铺模式）: {}", episode.getName());

        String baseDir = getEmbyDownloadDir();

        // 获取电视剧信息以构建系列前缀
        String seriesPrefix = null;
        String seriesId = episode.getSeriesId();
        if (seriesId != null && !seriesId.isEmpty()) {
            try {
                EmbyItem series = getItemDetail(seriesId);
                if (series != null) {
                    String seriesName = series.getName() != null ? series.getName() : "unknown_series";
                    if (series.getProductionYear() != null) seriesName += " (" + series.getProductionYear() + ")";
                    String tmdbId = extractTmdbId(series);
                    if (tmdbId != null) seriesName += " [tmdbid=" + tmdbId + "]";
                    seriesPrefix = seriesName.replaceAll("[\\\\/:*?\"<>|]", "_");
                    log.info("系列文件名前缀: {}", seriesPrefix);
                }
            } catch (Exception e) {
                log.warn("获取电视剧信息失败，将使用集标题作为文件名: {}", e.getMessage());
            }
        }

        if (seriesPrefix == null) {
            // 无法获取系列信息，降级为普通下载
            return downloadSingleItem(episode, baseDir);
        }

        // 构建平铺文件名：系列前缀 + S01E04 + 集标题.ext
        String ext = getMediaExtension(episode);
        String episodePart;
        if (episode.getParentIndexNumber() != null && episode.getIndexNumber() != null) {
            String epTitle = episode.getName() != null ? " " + episode.getName() : "";
            episodePart = String.format(" S%02dE%02d%s", episode.getParentIndexNumber(), episode.getIndexNumber(), epTitle);
        } else if (episode.getIndexNumber() != null) {
            episodePart = String.format(" E%02d", episode.getIndexNumber());
        } else {
            episodePart = episode.getName() != null ? " " + episode.getName() : "";
        }
        String flatFilename = (seriesPrefix + episodePart).replaceAll("[\\\\/:*?\"<>|]", "_") + ext;
        log.info("平铺文件名: {}", flatFilename);

        return downloadSingleItem(episode, baseDir, flatFilename);
    }

    /**
     * 构建电视剧目录路径（使用默认下载目录）
     */
    private String buildSeriesDirectory(EmbyItem series) {
        return buildSeriesDirectory(series, getEmbyDownloadDir());
    }

    /**
     * 构建电视剧目录路径。
     * 优先从 series.path 最后一段提取文件夹名（已含 tmdbid），
     * 否则用 名称 (年份) [tmdbid=xxx] 格式构建。
     */
    private String buildSeriesDirectory(EmbyItem series, String baseDir) {
        // 统一构建标准格式：名称 (年份) [tmdbid=xxx]
        // TMDB ID 从 providerIds 或 path 中解析（extractTmdbId 已兼容两种格式）
        String seriesName = series.getName();
        if (seriesName == null || seriesName.isEmpty()) seriesName = "unknown_series";
        if (series.getProductionYear() != null) seriesName += " (" + series.getProductionYear() + ")";
        String tmdbId = extractTmdbId(series);
        if (tmdbId != null) seriesName += " [tmdbid=" + tmdbId + "]";
        String folderName = seriesName;

        folderName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_");
        log.info("电视剧文件夹名: {}", folderName);

        java.nio.file.Path seriesDirPath = java.nio.file.Paths.get(baseDir, folderName);
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
        return downloadSeriesAllEpisodes(series, getEmbyDownloadDir());
    }

    private Map<String, Object> downloadSeriesAllEpisodes(EmbyItem series, String baseDir) throws Exception {
        log.info("开始下载电视剧所有剧集（平铺模式）: {}", series.getName());

        // 获取所有剧集
        List<EmbyItem> episodes = getSeriesEpisodes(series.getId());
        if (episodes == null || episodes.isEmpty()) {
            throw new BusinessException("该电视剧没有剧集");
        }
        log.info("找到 {} 集", episodes.size());

        // ── 构建系列名前缀：名称 (年份) [tmdbid=xxx] ──────────────────────────────
        String seriesName = series.getName() != null ? series.getName() : "unknown_series";
        String seriesPrefix = seriesName;
        if (series.getProductionYear() != null) seriesPrefix += " (" + series.getProductionYear() + ")";
        String tmdbId = extractTmdbId(series);
        if (tmdbId != null) seriesPrefix += " [tmdbid=" + tmdbId + "]";
        seriesPrefix = seriesPrefix.replaceAll("[\\\\/:*?\"<>|]", "_");
        log.info("系列文件名前缀: {}", seriesPrefix);

        // ── 下载每一集 → 平铺到 baseDir，文件名含系列前缀 ──────────────────────────
        int successCount = 0, failedCount = 0, skippedCount = 0;
        List<String> successFiles   = new ArrayList<>();
        List<String> downloadedFilePaths = new ArrayList<>();
        List<Map<String, String>> failedEpisodes = new ArrayList<>();

        for (int i = 0; i < episodes.size(); i++) {
            EmbyItem episode = episodes.get(i);
            log.info("下载进度: [{}/{}] {}", i + 1, episodes.size(), episode.getName());

            // 无媒体源时尝试重新拉取详情
            if (episode.getMediaSources() == null || episode.getMediaSources().isEmpty()) {
                log.warn("剧集 {} 无媒体源，重新获取详情...", episode.getName());
                try {
                    EmbyItem detail = getItemDetail(episode.getId());
                    if (detail != null && detail.getMediaSources() != null && !detail.getMediaSources().isEmpty()) {
                        episode = detail;
                    } else {
                        skippedCount++;
                        Map<String, String> f = new HashMap<>();
                        f.put("name", episode.getName()); f.put("reason", "无媒体源");
                        failedEpisodes.add(f);
                        continue;
                    }
                } catch (Exception e) {
                    skippedCount++;
                    Map<String, String> f = new HashMap<>();
                    f.put("name", episode.getName()); f.put("reason", "获取详情失败: " + e.getMessage());
                    failedEpisodes.add(f);
                    continue;
                }
            }

            // ── 构建平铺文件名：系列前缀 + S01E01 + 集标题 ──────────────────────
            String ext = getMediaExtension(episode);
            String episodePart;
            if (episode.getParentIndexNumber() != null && episode.getIndexNumber() != null) {
                String epTitle = episode.getName() != null ? " " + episode.getName() : "";
                episodePart = String.format(" S%02dE%02d%s",
                        episode.getParentIndexNumber(), episode.getIndexNumber(), epTitle);
            } else if (episode.getIndexNumber() != null) {
                episodePart = String.format(" E%02d", episode.getIndexNumber());
            } else {
                episodePart = episode.getName() != null ? " " + episode.getName() : "";
            }
            String flatFilename = (seriesPrefix + episodePart).replaceAll("[\\\\/:*?\"<>|]", "_") + ext;
            log.info("平铺文件名: {}", flatFilename);

            try {
                Map<String, Object> result = downloadSingleItem(episode, baseDir, flatFilename);
                if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                    successCount++;
                    String filePath = (String) result.get("filePath");
                    successFiles.add(flatFilename);
                    downloadedFilePaths.add(filePath);
                    log.info("✓ 下载成功: {}", flatFilename);
                } else {
                    failedCount++;
                    Map<String, String> f = new HashMap<>();
                    f.put("name", episode.getName()); f.put("reason", "下载失败");
                    failedEpisodes.add(f);
                }
            } catch (Exception e) {
                failedCount++;
                Map<String, String> f = new HashMap<>();
                f.put("name", episode.getName()); f.put("reason", e.getMessage());
                failedEpisodes.add(f);
                log.error("✗ 下载异常: {}", e.getMessage());
            }
        }

        log.info("电视剧平铺下载完成！成功: {}, 失败: {}, 跳过: {}", successCount, failedCount, skippedCount);

        String firstFilePath = downloadedFilePaths.isEmpty() ? baseDir : downloadedFilePaths.get(0);
        saveDownloadHistory(series.getId(), successCount > 0 ? "success" : "failed", firstFilePath, 0L,
                String.format("平铺下载 %d 集，成功 %d，失败 %d", episodes.size(), successCount, failedCount + skippedCount));

        Map<String, Object> result = new HashMap<>();
        result.put("success", failedCount == 0 && skippedCount == 0);
        result.put("type", "series");
        result.put("seriesName", series.getName());
        result.put("downloadedFilePaths", downloadedFilePaths);   // 平铺文件路径列表
        result.put("totalEpisodes", episodes.size());
        result.put("successCount", successCount);
        result.put("failedCount", failedCount + skippedCount);
        result.put("successFiles", successFiles);
        result.put("failedEpisodes", failedEpisodes);
        return result;
    }

    /**
     * 从 EmbyItem 中提取 TMDB ID。
     * 优先读 providerIds，否则解析 path 中的 {tmdbid-xxx} / [tmdbid=xxx]。
     */
    private String extractTmdbId(EmbyItem item) {
        if (item.getProviderIds() != null) {
            String tmdbId = item.getProviderIds().get("Tmdb");
            if (tmdbId != null && !tmdbId.isEmpty()) return tmdbId;
        }
        if (item.getPath() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[{\\[]tmdbid[-=](\\d+)[}\\]]", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(item.getPath());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /**
     * 获取媒体文件的真实扩展名（含点号）。
     * 优先从 mediaSource.container 读取，其次从 path 解析（忽略 .strm）。
     */
    private String getMediaExtension(EmbyItem item) {
        if (item.getMediaSources() != null && !item.getMediaSources().isEmpty()) {
            String container = item.getMediaSources().get(0).getContainer();
            if (container != null && !container.isEmpty() && !container.equalsIgnoreCase("strm")) {
                return "." + container.toLowerCase();
            }
        }
        if (item.getPath() != null) {
            int lastDot = item.getPath().lastIndexOf('.');
            if (lastDot > 0) {
                String ext = item.getPath().substring(lastDot + 1).toLowerCase();
                if (!ext.equals("strm") && ext.length() <= 5) return "." + ext;
            }
        }
        return ".mp4";
    }

    /**
     * 从 episode.path 的最后一段提取文件名，将 .strm 替换为真实扩展名。
     * 如果 path 中已有 S01E01 等标准命名则直接沿用。
     */
    private String extractEpisodeFilenameFromPath(EmbyItem episode, String realExt) {
        if (episode.getPath() == null || episode.getPath().isEmpty()) return null;
        String path = episode.getPath();
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash < 0 || lastSlash >= path.length() - 1) return null;
        String filename = path.substring(lastSlash + 1);
        if (filename.toLowerCase().endsWith(".strm")) {
            filename = filename.substring(0, filename.length() - 5) + realExt;
        }
        return filename;
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
        return downloadSingleItem(item, downloadDir, null);
    }

    /**
     * 下载单个媒体项到指定目录，filenameOverride 不为空时强制使用该文件名（用于平铺命名）
     */
    private Map<String, Object> downloadSingleItem(EmbyItem item, String downloadDir, String filenameOverride) throws Exception {
        String accessToken = embyAuthService.getAccessToken();
        String userId = embyAuthService.getUserId();
        String baseUrl = embyAuthService.getServerUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 获取真实扩展名（从 mediaSource 或 path 推断）
        String ext = getMediaExtension(item);

        // 构建文件名
        String filename;
        if (filenameOverride != null && !filenameOverride.isEmpty()) {
            // 平铺模式：外部已构建完整文件名（含系列前缀+扩展名）
            filename = filenameOverride;
            log.info("使用覆盖文件名: {}", filename);
        } else if ("Episode".equals(item.getType())) {
            if (item.getParentIndexNumber() != null && item.getIndexNumber() != null) {
                // Emby 提供了可靠的季集号，直接构建标准格式 S01E04 Title.ext
                String epTitle = item.getName() != null ? item.getName() : "";
                String nameBase = String.format("S%02dE%02d%s",
                        item.getParentIndexNumber(), item.getIndexNumber(),
                        epTitle.isEmpty() ? "" : " " + epTitle);
                filename = nameBase.replaceAll("[\\\\/:*?\"<>|]", "_") + ext;
                log.info("构建标准剧集文件名: {}", filename);
            } else {
                // 降级：从 path 提取原始文件名
                String pathFilename = extractEpisodeFilenameFromPath(item, ext);
                if (pathFilename != null && !pathFilename.isEmpty()) {
                    filename = pathFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
                    log.info("从path提取剧集文件名(无季集号): {}", filename);
                } else {
                    filename = (item.getName() != null ? item.getName() : "unknown")
                            .replaceAll("[\\\\/:*?\"<>|]", "_") + ext;
                }
            }
            // 不创建 Season 子目录，剧集直接放在 SeriesFolder 下，由刮削模块负责归档整理
        } else {
            // 电影或其他：名称 (年份) [tmdbid=xxx].ext
            String nameBase = item.getName() != null ? item.getName() : "unknown";
            if (item.getProductionYear() != null) {
                nameBase += " (" + item.getProductionYear() + ")";
            }
            String tmdbId = extractTmdbId(item);
            if (tmdbId != null) {
                nameBase += " [tmdbid=" + tmdbId + "]";
            }
            filename = nameBase.replaceAll("[\\\\/:*?\"<>|]", "_") + ext;
            log.info("电影/其他类型文件名: {}", filename);
        }
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
            String fileExt = filename.substring(filename.lastIndexOf("."));
            String newFilename = nameWithoutExt + "_" + counter + fileExt;
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
                     java.io.BufferedInputStream bufferedInput = new java.io.BufferedInputStream(inputStream, 4 * 1024 * 1024); // 4MB缓冲（10Gbps优化）
                     java.io.OutputStream outputStream = java.nio.file.Files.newOutputStream(targetPath);
                     java.io.BufferedOutputStream bufferedOutput = new java.io.BufferedOutputStream(outputStream, 4 * 1024 * 1024)) { // 4MB缓冲

                    byte[] buffer = new byte[1024 * 1024]; // 1MB buffer（10Gbps优化，从256KB提升到1MB）
                    int bytesRead;
                    long downloadedBytes = 0;
                    long lastLogTime = System.currentTimeMillis();

                    while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                        bufferedOutput.write(buffer, 0, bytesRead);
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

                    // 确保所有数据写入磁盘
                    bufferedOutput.flush();
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

    /**
     * 下载完成后更新FileInfo的实际文件路径和相对路径（使用默认下载目录作为基准）
     */
    private void updateFileInfoAfterDownload(FileInfo fileInfo, Map<String, Object> downloadResult) {
        updateFileInfoAfterDownload(fileInfo, downloadResult, getEmbyDownloadDir());
    }

    /**
     * 下载完成后更新FileInfo的实际文件路径和相对路径。
     * baseDir 为本次下载操作的根目录（直接下载用 embyDownloadDir，下载上传用 uploadDir），
     * 用于计算文件相对路径（如单集在 SeriesFolder 内时，relativePath = SeriesFolder 名）。
     */
    private void updateFileInfoAfterDownload(FileInfo fileInfo, Map<String, Object> downloadResult, String baseDir) {
        try {
            if ("series".equals(downloadResult.get("type"))) {
                // 电视剧：relativePath 设为剧集文件夹名
                String seriesDir = (String) downloadResult.get("seriesDir");
                if (seriesDir != null) {
                    java.nio.file.Path seriesDirPath = java.nio.file.Paths.get(seriesDir);
                    String seriesFolderName = seriesDirPath.getFileName().toString();
                    fileInfo.setRelativePath(seriesFolderName);
                    fileInfo.setFilePath(seriesDir);
                    log.info("更新电视剧FileInfo: seriesDir={}, relativePath={}", seriesDir, seriesFolderName);
                }
            } else {
                // 单个文件（电影或单集）
                String filePath = (String) downloadResult.get("filePath");
                String filename = (String) downloadResult.get("filename");
                String downloadDir = (String) downloadResult.get("downloadDir");

                if (filePath != null && filename != null) {
                    fileInfo.setFilePath(filePath);
                    fileInfo.setFileName(filename);

                    // 若文件在 baseDir 的子目录下（如 SeriesFolder），提取相对路径
                    String normalizedBase = baseDir.replace('\\', '/');
                    String normalizedDownloadDir = downloadDir != null ? downloadDir.replace('\\', '/') : null;
                    if (normalizedDownloadDir != null && !normalizedDownloadDir.equals(normalizedBase)) {
                        String prefix = normalizedBase.endsWith("/") ? normalizedBase : normalizedBase + "/";
                        if (normalizedDownloadDir.startsWith(prefix)) {
                            String relativePath = normalizedDownloadDir.substring(prefix.length());
                            fileInfo.setRelativePath(relativePath);
                            log.info("更新单文件FileInfo: filePath={}, relativePath={}", filePath, relativePath);
                        } else {
                            fileInfo.setRelativePath("");
                            log.info("更新单文件FileInfo: filePath={}, relativePath=空（根目录）", filePath);
                        }
                    } else {
                        fileInfo.setRelativePath("");
                        log.info("更新单文件FileInfo: filePath={}, relativePath=空（根目录）", filePath);
                    }
                }
            }

            fileInfoService.updateById(fileInfo);
        } catch (Exception e) {
            log.error("更新FileInfo失败: fileInfoId={}, error={}", fileInfo.getId(), e.getMessage(), e);
        }
    }

    @Autowired
    private IUploadService uploadService;

    @Value("${app.emby.upload-dir:/data/upload}")
    private String defaultUploadDir;


    @Override
    public Long batchDownloadAndUploadAsync(List<String> itemIds, String uploadDir, String gdTargetPath) {
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
            taskName = "Emby下载上传 - " + itemNames.get(0);
        } else if (itemNames.size() <= 3) {
            taskName = "Emby下载上传 - " + String.join(", ", itemNames);
        } else {
            taskName = "Emby下载上传 - " + itemNames.get(0) + " 等" + itemIds.size() + "个媒体项";
        }
        // 截断过长的任务名
        if (taskName.length() > 200) {
            taskName = taskName.substring(0, 197) + "...";
        }

        // 2. 创建 UploadTask（taskType=5 表示下载上传任务）
        Long taskId = uploadTaskService.createDownloadUploadTask(taskName, uploadDir, gdTargetPath, itemIds.size());
        if (taskId == null) {
            throw new BusinessException("创建下载上传任务失败");
        }
        log.info("创建下载上传任务: taskId={}, 上传目录={}, GD目标路径={}", taskId, uploadDir, gdTargetPath);

        // 3. 为每个 itemId 创建 FileInfo 行
        List<FileInfo> fileInfoList = new ArrayList<>();
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

        // 4. 设置停止标志
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        downloadStopFlags.put(taskId, stopFlag);

        // 5. 启动并发下载上传线程
        final Long finalTaskId = taskId;
        final String finalUploadDir = uploadDir;
        final String finalGdTargetPath = gdTargetPath;

        // ── 注册到 TaskPauseManager ──
        TaskPauseManager.register(finalTaskId, itemIds.size());

        new Thread(() -> {
            // 使用 AtomicInteger 保证线程安全
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            // 获取并发下载数配置
            int concurrentDownloads = embyProperties.getConcurrentDownloads();
            if (concurrentDownloads <= 0) {
                concurrentDownloads = 3; // 默认3个并发
            }

            log.info("========================================");
            log.info("批量下载上传任务开始: taskId={}, 共 {} 个媒体项, 并发数: {}",
                finalTaskId, itemIds.size(), concurrentDownloads);
            log.info("下载目录: {}", finalUploadDir);
            log.info("========================================");

            // ═══ 预创建目标目录，避免并发上传时GD创建重复目录 ═══
            try {
                GdAccount account = gdAccountService.getNextAvailableAccountInRotation(finalTaskId, 0L);
                if (account != null) {
                    rcloneUtil.makeDirectory(account.getRcloneConfigName(), finalGdTargetPath);
                    log.info("目标目录预创建完成: {}", finalGdTargetPath);
                }
            } catch (Exception e) {
                log.warn("预创建目录失败（不影响上传）: {}", e.getMessage());
            }
            // ═══════════════════════════════════════════════

            // 创建固定大小的线程池
            ExecutorService executor = Executors.newFixedThreadPool(concurrentDownloads);

            CountDownLatch latch = new CountDownLatch(itemIds.size());

            try {
                for (int i = 0; i < itemIds.size(); i++) {
                    final int index = i;
                    final String itemId = itemIds.get(index);
                    final FileInfo currentFile = fileInfoList.get(index);

                    // 提交任务到线程池
                    executor.submit(() -> {
                        try {

                            log.info("[{}/{}] 开始处理: {}", index + 1, itemIds.size(), currentFile.getFileName());

                            try {
                                // 检查停止标志（放在内层try内，确保finally中的countDown和onThreadFinished一定会执行）
                                if (stopFlag.get()) {
                                    log.info("下载上传任务被停止: taskId={}", finalTaskId);
                                    return;
                                }

                                EmbyItem item = getItemDetail(itemId);
                                if (item == null) {
                                    throw new BusinessException("媒体项不存在: " + itemId);
                                }

                                boolean isSeries = "Series".equals(item.getType());

                                if (isSeries) {
                                    // 电视剧：交错模式 —— 下完一集立即上传，减少等待和本地磁盘占用
                                    fileInfoService.updateFileStatus(currentFile.getId(), 1, "正在下载并上传");
                                    webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 1, "正在下载并上传");

                                    // 提前获取账号（整个 series 共用一个账号）
                                    GdAccount account = gdAccountService.getNextAvailableAccountInRotation(finalTaskId, currentFile.getFileSize());
                                    if (account == null) {
                                        throw new BusinessException("没有可用的账号");
                                    }
                                    log.info("[{}/{}] 使用账号: {}", index + 1, itemIds.size(), account.getAccountName());

                                    // 获取剧集列表
                                    List<EmbyItem> episodes = getSeriesEpisodes(item.getId());
                                    if (episodes == null || episodes.isEmpty()) {
                                        throw new BusinessException("该电视剧没有剧集");
                                    }
                                    log.info("[{}/{}] 共 {} 集，开始交错下载上传", index + 1, itemIds.size(), episodes.size());

                                    String base = finalGdTargetPath.endsWith("/") ? finalGdTargetPath : finalGdTargetPath + "/";
                                    int epSuccess = 0;

                                    for (int ei = 0; ei < episodes.size(); ei++) {
                                        // 暂停/取消检查：立即停止处理剩余剧集
                                        if (stopFlag.get()) {
                                            log.info("[{}/{}] 任务已停止，跳过剩余剧集", index + 1, itemIds.size());
                                            break;
                                        }
                                        EmbyItem episode = episodes.get(ei);
                                        log.info("[{}/{}] [{}/{}集] 下载: {}", index + 1, itemIds.size(), ei + 1, episodes.size(), episode.getName());

                                        // 无媒体源时重新拉取详情
                                        if (episode.getMediaSources() == null || episode.getMediaSources().isEmpty()) {
                                            try {
                                                EmbyItem detail = getItemDetail(episode.getId());
                                                if (detail != null && detail.getMediaSources() != null && !detail.getMediaSources().isEmpty()) {
                                                    episode = detail;
                                                } else {
                                                    log.warn("[{}/{}] 剧集 {} 无媒体源，跳过", index + 1, itemIds.size(), episode.getName());
                                                    continue;
                                                }
                                            } catch (Exception e) {
                                                log.warn("[{}/{}] 获取剧集详情失败: {}", index + 1, itemIds.size(), e.getMessage());
                                                continue;
                                            }
                                        }

                                        // 从 episode.getPath() 提取原始文件名
                                        String ext = getMediaExtension(episode);
                                        String flatFilename = extractEpisodeFilenameFromPath(episode, ext);
                                        if (flatFilename == null || flatFilename.trim().isEmpty()) {
                                            // 降级：用集名
                                            flatFilename = (episode.getName() != null ? episode.getName() : "unknown")
                                                    .replaceAll("[\\\\/:*?\"<>|]", "_") + ext;
                                        }

                                        // 检查是否需要添加剧名前缀（如果文件名以S\d+E\d+开头，说明缺少剧名）
                                        if (flatFilename.matches("^S\\d+E\\d+.*")) {
                                            String seriesId = episode.getSeriesId();
                                            if (seriesId != null && !seriesId.isEmpty()) {
                                                try {
                                                    EmbyItem series = getItemDetail(seriesId);
                                                    if (series != null) {
                                                        String seriesPrefix = series.getName() != null ? series.getName() : "";
                                                        if (series.getProductionYear() != null) {
                                                            seriesPrefix += " (" + series.getProductionYear() + ")";
                                                        }
                                                        String tmdbId = extractTmdbId(series);
                                                        if (tmdbId != null) {
                                                            seriesPrefix += " [tmdbid=" + tmdbId + "]";
                                                        }
                                                        seriesPrefix = seriesPrefix.replaceAll("[\\\\/:*?\"<>|]", "_");
                                                        flatFilename = seriesPrefix + " " + flatFilename;
                                                        log.info("[{}/{}] [{}/{}集] 添加剧名前缀: {}", index + 1, itemIds.size(), ei + 1, episodes.size(), flatFilename);
                                                    }
                                                } catch (Exception e) {
                                                    log.warn("[{}/{}] [{}/{}集] 获取系列信息失败: {}", index + 1, itemIds.size(), ei + 1, episodes.size(), e.getMessage());
                                                }
                                            }
                                        }

                                        // 下载单集后立即上传
                                        try {
                                            Map<String, Object> epResult = downloadSingleItem(episode, finalUploadDir, flatFilename);
                                            if (epResult == null || !Boolean.TRUE.equals(epResult.get("success"))) {
                                                log.warn("[{}/{}] [{}/{}集] 下载失败: {}", index + 1, itemIds.size(), ei + 1, episodes.size(), flatFilename);
                                                continue;
                                            }
                                            String filePath = (String) epResult.get("filePath");
                                            if (filePath == null) {
                                                log.warn("[{}/{}] [{}/{}集] 下载结果无filePath，跳过: {}", index + 1, itemIds.size(), ei + 1, episodes.size(), flatFilename);
                                                continue;
                                            }
                                            log.info("[{}/{}] [{}/{}集] 下载成功，立即上传", index + 1, itemIds.size(), ei + 1, episodes.size());
                                            fileInfoService.updateFileStatus(currentFile.getId(), 1,
                                                String.format("上传 %d/%d", ei + 1, episodes.size()));

                                            String remoteFilePath = base + flatFilename;
                                            RcloneResult uploadResult = rcloneUtil.uploadSingleFileTo(
                                                filePath, account.getRcloneConfigName(), remoteFilePath,
                                                line -> log.debug("rclone: {}", line));
                                            if (uploadResult.isSuccess()) {
                                                epSuccess++;
                                                log.info("[{}/{}] [{}/{}集] 上传成功: {}", index + 1, itemIds.size(), ei + 1, episodes.size(), remoteFilePath);
                                            } else {
                                                log.warn("[{}/{}] [{}/{}集] 上传失败: {} err={}", index + 1, itemIds.size(), ei + 1, episodes.size(),
                                                    flatFilename, uploadResult.getErrorMessage());
                                            }
                                        } catch (Exception e) {
                                            log.error("[{}/{}] [{}/{}集] 处理异常: {}", index + 1, itemIds.size(), ei + 1, episodes.size(), e.getMessage());
                                        }
                                    }

                                    if (epSuccess == 0) {
                                        throw new BusinessException("所有剧集下载上传失败");
                                    }
                                    log.info("[{}/{}] 电视剧交错下载上传完成: 成功 {}/{} 集", index + 1, itemIds.size(), epSuccess, episodes.size());

                                    // 更新 FileInfo 路径（series 用 gdTargetPath 标记）
                                    currentFile.setFilePath(finalGdTargetPath);
                                    fileInfoService.updateById(currentFile);

                                } else {
                                    // 电影/单集：步骤1 下载，步骤2 上传
                                    log.info("[{}/{}] 步骤1: 下载文件...", index + 1, itemIds.size());
                                    fileInfoService.updateFileStatus(currentFile.getId(), 1, "正在下载");
                                    webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 1, "正在下载");

                                    // 从 item.getPath() 提取原始文件名
                                    String filenameOverride = null;
                                    if (item.getPath() != null && !item.getPath().isEmpty()) {
                                        String ext = getMediaExtension(item);
                                        filenameOverride = extractEpisodeFilenameFromPath(item, ext);
                                        if (filenameOverride != null) {
                                            log.info("[{}/{}] 使用原始文件名: {}", index + 1, itemIds.size(), filenameOverride);

                                            // 检查电影文件名是否缺少标题信息
                                            int lastDotIndex = filenameOverride.lastIndexOf('.');
                                            if (lastDotIndex > 0) {
                                                String nameWithoutExt = filenameOverride.substring(0, lastDotIndex);
                                                // 如果文件名不包含年份括号或tmdbid，则添加前缀
                                                if (!nameWithoutExt.contains("(") && !nameWithoutExt.contains("[tmdbid=")) {
                                                    String moviePrefix = item.getName() != null ? item.getName() : "";
                                                    if (item.getProductionYear() != null) {
                                                        moviePrefix += " (" + item.getProductionYear() + ")";
                                                    }
                                                    String tmdbId = extractTmdbId(item);
                                                    if (tmdbId != null) {
                                                        moviePrefix += " [tmdbid=" + tmdbId + "]";
                                                    }
                                                    moviePrefix = moviePrefix.replaceAll("[\\\\/:*?\"<>|]", "_");
                                                    filenameOverride = moviePrefix + " " + filenameOverride;
                                                    log.info("[{}/{}] 添加电影名前缀: {}", index + 1, itemIds.size(), filenameOverride);
                                                }
                                            }
                                        }
                                    }

                                    Map<String, Object> downloadResult = downloadSingleItem(item, finalUploadDir, filenameOverride);
                                    if (downloadResult == null || !Boolean.TRUE.equals(downloadResult.get("success"))) {
                                        throw new BusinessException("下载失败");
                                    }
                                    log.info("[{}/{}] 下载成功", index + 1, itemIds.size());

                                    updateFileInfoAfterDownload(currentFile, downloadResult, finalUploadDir);
                                    log.info("[{}/{}] FileInfo更新完成: filePath={}, relativePath={}",
                                        index + 1, itemIds.size(), currentFile.getFilePath(), currentFile.getRelativePath());

                                    log.info("[{}/{}] 步骤2: 上传到Google Drive...", index + 1, itemIds.size());
                                    fileInfoService.updateFileStatus(currentFile.getId(), 1, "正在上传");
                                    webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 1, "正在上传");

                                    GdAccount account = gdAccountService.getNextAvailableAccountInRotation(finalTaskId, currentFile.getFileSize());
                                    if (account == null) {
                                        throw new BusinessException("没有可用的账号");
                                    }
                                    log.info("[{}/{}] 使用账号: {}", index + 1, itemIds.size(), account.getAccountName());

                                    // 直接上传，不经过格式化流程
                                    String localFilePath = currentFile.getFilePath();
                                    String targetPath = finalGdTargetPath.endsWith("/") ? finalGdTargetPath : finalGdTargetPath + "/";
                                    String fileName = new java.io.File(localFilePath).getName();
                                    String remoteFilePath = targetPath + fileName;

                                    RcloneResult uploadResult = rcloneUtil.uploadSingleFileTo(
                                        localFilePath, account.getRcloneConfigName(), remoteFilePath,
                                        line -> log.debug("rclone: {}", line));

                                    if (!uploadResult.isSuccess()) {
                                        throw new BusinessException("上传失败: " + uploadResult.getErrorMessage());
                                    }

                                    // 标记文件为已上传
                                    fileInfoService.markFileAsUploaded(currentFile.getId(), account.getId());

                                    // 插入上传记录
                                    UploadRecord uploadRecord = new UploadRecord();
                                    uploadRecord.setTaskId(finalTaskId);
                                    uploadRecord.setAccountId(account.getId());
                                    uploadRecord.setFileId(currentFile.getId());
                                    uploadRecord.setUploadSize(currentFile.getFileSize());
                                    uploadRecord.setUploadTime(DateTimeUtil.now());
                                    uploadRecord.setStatus(1);
                                    uploadRecordMapper.insert(uploadRecord);

                                    // 更新账号配额
                                    gdAccountService.updateAccountQuota(account.getId(), currentFile.getFileSize());
                                }

                                log.info("[{}/{}] 上传成功!", index + 1, itemIds.size());
                                successCount.incrementAndGet();

                                fileInfoService.updateFileStatus(currentFile.getId(), 2, null);
                                webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 2, "完成");

                                // 保存下载历史（标记为download_upload类型）
                                // currentFile.filePath 已在各分支内设置好（series=gdTargetPath，movie=实际文件路径）
                                String recordPath = currentFile.getFilePath();
                                Long fileSize = currentFile.getFileSize();
                                EmbyDownloadHistory history = new EmbyDownloadHistory();
                                EmbyConfig config = embyConfigService.getDefaultConfig();
                                if (config != null) {
                                    history.setEmbyItemId(itemId);
                                    history.setEmbyConfigId(config.getId());
                                    history.setDownloadStatus("success");
                                    history.setTaskType("download_upload");
                                    history.setFilePath(recordPath);
                                    history.setFileSize(fileSize);
                                    history.setCreateTime(LocalDateTime.now());
                                    history.setUpdateTime(LocalDateTime.now());
                                    downloadHistoryMapper.insert(history);
                                }

                            } catch (Exception e) {
                                failedCount.incrementAndGet();
                                log.error("[{}/{}] 处理失败: {}, 错误: {}", index + 1, itemIds.size(), currentFile.getFileName(), e.getMessage());
                                fileInfoService.updateFileStatus(currentFile.getId(), 3, e.getMessage());
                                webSocketService.pushFileStatus(finalTaskId, currentFile.getId(), currentFile.getFileName(), 3, e.getMessage());

                                // 保存失败的下载历史
                                EmbyDownloadHistory history = new EmbyDownloadHistory();
                                EmbyConfig config = embyConfigService.getDefaultConfig();
                                if (config != null) {
                                    history.setEmbyItemId(itemId);
                                    history.setEmbyConfigId(config.getId());
                                    history.setDownloadStatus("failed");
                                    history.setTaskType("download_upload");
                                    history.setErrorMessage(e.getMessage());
                                    history.setCreateTime(LocalDateTime.now());
                                    history.setUpdateTime(LocalDateTime.now());
                                    downloadHistoryMapper.insert(history);
                                }
                            } finally {
                                // 完成一个任务
                                latch.countDown();
                                TaskPauseManager.onThreadFinished(finalTaskId);

                                // 更新任务进度
                                int completed = successCount.get() + failedCount.get();
                                int progress = (int) ((completed * 100L) / itemIds.size());
                                uploadTaskService.updateTaskProgress(finalTaskId, completed, 0L, progress);
                                webSocketService.pushTaskProgress(finalTaskId, progress,
                                    completed, itemIds.size(), 0L, 0L,
                                    currentFile.getFileName());
                            }
                        } catch (Exception threadEx) {
                            // 内层 finally 已保证 countDown + onThreadFinished，此处仅记录日志
                            log.error("线程执行异常", threadEx);
                        }
                    });
                }

                // 关闭线程池，不再接受新任务
                executor.shutdown();

                // 等待所有任务完成
                log.info("等待所有下载上传任务完成...");
                latch.await();

                log.info("所有任务已提交完成，等待线程池关闭...");
                executor.awaitTermination(1, java.util.concurrent.TimeUnit.HOURS);

                // 更新最终状态
                int finalSuccessCount = successCount.get();
                int finalFailedCount = failedCount.get();

                if (stopFlag.get()) {
                    // 暂停中 → 由 TaskPauseManager 回调处理状态变更
                    log.info("下载上传任务已停止，等待 TaskPauseManager 回调: taskId={}", finalTaskId);
                } else if (finalFailedCount == 0) {
                    uploadTaskService.updateTaskStatus(finalTaskId, 2, null);
                    webSocketService.pushTaskStatus(finalTaskId, 2, "完成");
                } else {
                    uploadTaskService.updateTaskStatus(finalTaskId, 2,
                        String.format("完成（成功%d，失败%d）", finalSuccessCount, finalFailedCount));
                    webSocketService.pushTaskStatus(finalTaskId, 2, "完成（部分失败）");
                }

                // 确保最终进度为100%
                uploadTaskService.updateTaskProgress(finalTaskId,
                    finalSuccessCount + finalFailedCount, 0L, 100);

                log.info("========================================");
                log.info("批量下载上传任务完成: taskId={}, 成功: {}, 失败: {}",
                    finalTaskId, finalSuccessCount, finalFailedCount);
                log.info("========================================");

            } catch (Exception e) {
                log.error("批量下载上传任务异常: taskId={}", finalTaskId, e);
                uploadTaskService.updateTaskStatus(finalTaskId, 3, e.getMessage());
                webSocketService.pushTaskStatus(finalTaskId, 3, "任务异常");
            } finally {
                executor.shutdownNow();
                downloadStopFlags.remove(finalTaskId);
                TaskPauseManager.unregister(finalTaskId);
            }
        }, "Emby-BatchDU-" + taskId).start();

        log.info("批量下载上传任务已启动: taskId={}, 共 {} 个媒体项", taskId, itemIds.size());
        return taskId;
    }

    @Override
    public boolean pauseDownloadTask(Long taskId) {
        AtomicBoolean stopFlag = downloadStopFlags.get(taskId);
        if (stopFlag != null) {
            stopFlag.set(true);
            uploadTaskService.updateTaskStatus(taskId, 6, "暂停中");
            TaskPauseManager.requestPause(taskId, tid -> {
                uploadTaskService.updateTaskStatus(tid, 4, "已暂停");
                webSocketService.pushTaskStatus(tid, 4, "已暂停");
            });
            return true;
        }
        return false;
    }

    @Override
    public boolean cancelDownloadTask(Long taskId) {
        AtomicBoolean stopFlag = downloadStopFlags.get(taskId);
        if (stopFlag != null) {
            stopFlag.set(true);
            uploadTaskService.updateTaskStatus(taskId, 6, "取消中");
            TaskPauseManager.requestCancel(taskId, tid -> {
                uploadTaskService.updateTaskStatus(tid, 5, "已取消");
                webSocketService.pushTaskStatus(tid, 5, "已取消");
            });
            return true;
        }
        return false;
    }
}
