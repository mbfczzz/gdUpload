package com.gdupload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.entity.Resource115;
import com.gdupload.mapper.Resource115Mapper;
import com.gdupload.service.IResource115ManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 115资源管理服务实现
 */
@Slf4j
@Service
public class Resource115ManageServiceImpl implements IResource115ManageService {

    @Autowired
    private Resource115Mapper resource115Mapper;

    @Override
    public Page<Resource115> getResourcePage(int page, int size, String keyword) {
        Page<Resource115> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Resource115> wrapper = new LambdaQueryWrapper<>();

        // 如果有搜索关键词，添加搜索条件
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Resource115::getName, keyword)
                    .or()
                    .like(Resource115::getTmdbId, keyword)
                    .or()
                    .like(Resource115::getType, keyword));
        }

        wrapper.orderByDesc(Resource115::getId);

        return resource115Mapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Resource115 getResourceById(Integer id) {
        return resource115Mapper.selectById(id);
    }

    @Override
    public boolean addResource(Resource115 resource) {
        try {
            return resource115Mapper.insert(resource) > 0;
        } catch (Exception e) {
            log.error("添加资源失败", e);
            return false;
        }
    }

    @Override
    public boolean updateResource(Resource115 resource) {
        try {
            return resource115Mapper.updateById(resource) > 0;
        } catch (Exception e) {
            log.error("更新资源失败", e);
            return false;
        }
    }

    @Override
    public boolean deleteResource(Integer id) {
        try {
            return resource115Mapper.deleteById(id) > 0;
        } catch (Exception e) {
            log.error("删除资源失败", e);
            return false;
        }
    }

    @Override
    public boolean batchDeleteResources(List<Integer> ids) {
        try {
            return resource115Mapper.deleteBatchIds(ids) > 0;
        } catch (Exception e) {
            log.error("批量删除资源失败", e);
            return false;
        }
    }
}
