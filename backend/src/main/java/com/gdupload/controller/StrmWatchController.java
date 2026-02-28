package com.gdupload.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gdupload.common.Result;
import com.gdupload.entity.StrmFileRecord;
import com.gdupload.entity.StrmWatchConfig;
import com.gdupload.service.IStrmWatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * STRM 监控管理 Controller
 *
 * 路径前缀: /strm-watch
 */
@RestController
@RequestMapping("/strm-watch")
@RequiredArgsConstructor
public class StrmWatchController {

    private final IStrmWatchService strmWatchService;

    /** 分页查询监控配置 */
    @GetMapping("/list")
    public Result<IPage<StrmWatchConfig>> list(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(strmWatchService.listConfigs(page, size));
    }

    /** 新增监控配置 */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody StrmWatchConfig config) {
        strmWatchService.addConfig(config);
        return Result.success();
    }

    /** 修改监控配置 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody StrmWatchConfig config) {
        config.setId(id);
        strmWatchService.updateConfig(config);
        return Result.success();
    }

    /** 删除监控配置 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        strmWatchService.deleteConfig(id);
        return Result.success();
    }

    /** 启用 */
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        strmWatchService.enableConfig(id);
        return Result.success();
    }

    /** 禁用 */
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        strmWatchService.disableConfig(id);
        return Result.success();
    }

    /** 手动增量同步 */
    @PostMapping("/{id}/sync")
    public Result<Void> sync(@PathVariable Long id) {
        strmWatchService.triggerSync(id);
        return Result.success("同步任务已启动");
    }

    /** 强制全量重刮 */
    @PostMapping("/{id}/force-rescrape")
    public Result<Void> forceRescrape(@PathVariable Long id) {
        strmWatchService.triggerForceRescrape(id);
        return Result.success("强制重刮任务已启动");
    }

    /** 查询文件记录 */
    @GetMapping("/{id}/records")
    public Result<IPage<StrmFileRecord>> records(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(strmWatchService.listRecords(id, status, page, size));
    }

    /** 查询同步状态（前端轮询） */
    @GetMapping("/{id}/sync-status")
    public Result<Map<String, Object>> syncStatus(@PathVariable Long id) {
        return Result.success(strmWatchService.getSyncStatus(id));
    }
}
