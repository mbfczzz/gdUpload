package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.service.ITmdbService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * TMDB控制器
 */
@Slf4j
@RestController
@RequestMapping("/tmdb")
public class TmdbController {

    @Autowired
    private ITmdbService tmdbService;

    /**
     * 搜索TMDB ID
     *
     * @param name 名称
     * @param year 年份
     * @param type 类型
     * @return TMDB ID
     */
    @GetMapping("/search")
    public Result<String> searchTmdbId(
            @RequestParam String name,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type) {

        log.info("搜索 TMDB ID: name={}, year={}, type={}", name, year, type);

        String tmdbId = tmdbService.searchTmdbId(name, year, type);

        if (tmdbId != null) {
            return Result.success(tmdbId);
        } else {
            return Result.success("未找到匹配的 TMDB ID", null);
        }
    }

    /**
     * 批量补充115资源的TMDB ID
     *
     * @return 补充结果
     */
    @PostMapping("/batch-fill")
    public Result<Map<String, Object>> batchFillTmdbIds() {
        log.info("开始批量补充 TMDB ID");

        int successCount = tmdbService.batchFillTmdbIds();

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("message", "批量补充完成，成功 " + successCount + " 个");

        return Result.success(result);
    }
}
