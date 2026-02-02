package com.gdupload.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.common.Result;
import com.gdupload.entity.Resource115;
import com.gdupload.service.IResource115ManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 115资源管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/resource115/manage")
public class Resource115ManageController {

    @Autowired
    private IResource115ManageService resource115ManageService;

    /**
     * 分页查询资源
     *
     * @param page 页码
     * @param size 每页大小
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<Page<Resource115>> getResourcePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {

        log.info("分页查询资源: page={}, size={}, keyword={}", page, size, keyword);

        Page<Resource115> result = resource115ManageService.getResourcePage(page, size, keyword);
        return Result.success(result);
    }

    /**
     * 根据ID获取资源
     *
     * @param id 资源ID
     * @return 资源信息
     */
    @GetMapping("/{id}")
    public Result<Resource115> getResourceById(@PathVariable Integer id) {
        log.info("获取资源详情: id={}", id);

        Resource115 resource = resource115ManageService.getResourceById(id);
        if (resource != null) {
            return Result.success(resource);
        } else {
            return Result.error("资源不存在");
        }
    }

    /**
     * 添加资源
     *
     * @param resource 资源信息
     * @return 操作结果
     */
    @PostMapping
    public Result<String> addResource(@RequestBody Resource115 resource) {
        log.info("添加资源: {}", resource.getName());

        boolean success = resource115ManageService.addResource(resource);
        if (success) {
            return Result.success("添加成功");
        } else {
            return Result.error("添加失败");
        }
    }

    /**
     * 更新资源
     *
     * @param resource 资源信息
     * @return 操作结果
     */
    @PutMapping
    public Result<String> updateResource(@RequestBody Resource115 resource) {
        log.info("更新资源: id={}, name={}", resource.getId(), resource.getName());

        boolean success = resource115ManageService.updateResource(resource);
        if (success) {
            return Result.success("更新成功");
        } else {
            return Result.error("更新失败");
        }
    }

    /**
     * 删除资源
     *
     * @param id 资源ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteResource(@PathVariable Integer id) {
        log.info("删除资源: id={}", id);

        boolean success = resource115ManageService.deleteResource(id);
        if (success) {
            return Result.success("删除成功");
        } else {
            return Result.error("删除失败");
        }
    }

    /**
     * 批量删除资源
     *
     * @param ids 资源ID列表
     * @return 操作结果
     */
    @DeleteMapping("/batch")
    public Result<String> batchDeleteResources(@RequestBody List<Integer> ids) {
        log.info("批量删除资源: ids={}", ids);

        boolean success = resource115ManageService.batchDeleteResources(ids);
        if (success) {
            return Result.success("批量删除成功");
        } else {
            return Result.error("批量删除失败");
        }
    }
}
