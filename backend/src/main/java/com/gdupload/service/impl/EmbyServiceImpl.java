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
import com.gdupload.service.IEmbyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Emby服务实现类
 */
@Slf4j
@Service
public class EmbyServiceImpl implements IEmbyService {

    @Autowired
    private EmbyAuthService embyAuthService;

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
}
