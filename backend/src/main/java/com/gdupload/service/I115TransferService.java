package com.gdupload.service;

import com.gdupload.entity.Resource115;

import java.util.Map;

/**
 * 115网盘转存服务接口
 */
public interface I115TransferService {

    /**
     * 转存115资源到我的网盘
     *
     * @param resource 115资源信息
     * @param targetFolderId 目标文件夹ID（可选，默认根目录）
     * @return 转存结果
     */
    Map<String, Object> transferResource(Resource115 resource, String targetFolderId);

    /**
     * 测试115 Cookie是否有效
     *
     * @return 是否有效
     */
    boolean testCookie();

    /**
     * 获取115用户信息
     *
     * @return 用户信息
     */
    Map<String, Object> getUserInfo();
}
