package com.gdupload.controller;

import com.gdupload.common.Result;
import com.gdupload.entity.Resource115;
import com.gdupload.service.IResource115Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 115资源控制器
 */
@Slf4j
@RestController
@RequestMapping("/resource115")
public class Resource115Controller {

    @Autowired
    private IResource115Service resource115Service;

    /**
     * 智能搜索匹配资源
     *
     * @param tmdbId TMDB ID（优先匹配）
     * @param name 媒体项名称
     * @param originalTitle 原始名称
     * @param year 年份
     * @return 匹配的资源
     */
    @GetMapping("/smart-search")
    public Result<Resource115> smartSearch(
            @RequestParam(required = false) String tmdbId,
            @RequestParam String name,
            @RequestParam(required = false) String originalTitle,
            @RequestParam(required = false) Integer year) {

        log.info("智能搜索115资源: tmdbId={}, name={}, originalTitle={}, year={}", tmdbId, name, originalTitle, year);

        Resource115 resource = resource115Service.smartSearch(tmdbId, name, originalTitle, year);

        if (resource != null) {
            return Result.success(resource);
        } else {
            return Result.success("未找到匹配的资源", null);
        }
    }
}
