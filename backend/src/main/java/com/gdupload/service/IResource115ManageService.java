package com.gdupload.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdupload.entity.Resource115;

/**
 * 115资源管理服务接口
 */
public interface IResource115ManageService {

    /**
     * 分页查询资源
     *
     * @param page 页码
     * @param size 每页大小
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    Page<Resource115> getResourcePage(int page, int size, String keyword);

    /**
     * 根据ID获取资源
     *
     * @param id 资源ID
     * @return 资源信息
     */
    Resource115 getResourceById(Integer id);

    /**
     * 添加资源
     *
     * @param resource 资源信息
     * @return 是否成功
     */
    boolean addResource(Resource115 resource);

    /**
     * 更新资源
     *
     * @param resource 资源信息
     * @return 是否成功
     */
    boolean updateResource(Resource115 resource);

    /**
     * 删除资源
     *
     * @param id 资源ID
     * @return 是否成功
     */
    boolean deleteResource(Integer id);

    /**
     * 批量删除资源
     *
     * @param ids 资源ID列表
     * @return 是否成功
     */
    boolean batchDeleteResources(java.util.List<Integer> ids);
}
