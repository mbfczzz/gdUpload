package com.gdupload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdupload.dto.EmbyItem;
import com.gdupload.entity.EmbyItemCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Emby媒体项缓存Mapper
 */
@Mapper
public interface EmbyItemCacheMapper extends BaseMapper<EmbyItemCache> {

    /**
     * 根据转存状态和下载状态筛选媒体项（分页）
     */
    @Select("<script>" +
            "SELECT DISTINCT e.* FROM emby_item e " +
            "<if test='params.transferStatus == \"success\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM transfer_history " +
            "  GROUP BY emby_item_id" +
            ") t ON e.id = t.emby_item_id " +
            "INNER JOIN transfer_history th ON t.emby_item_id = th.emby_item_id AND t.max_time = th.create_time " +
            "</if>" +
            "<if test='params.transferStatus == \"failed\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM transfer_history " +
            "  GROUP BY emby_item_id" +
            ") t ON e.id = t.emby_item_id " +
            "INNER JOIN transfer_history th ON t.emby_item_id = th.emby_item_id AND t.max_time = th.create_time " +
            "</if>" +
            "<if test='params.transferStatus == \"none\"'>" +
            "LEFT JOIN transfer_history th ON e.id = th.emby_item_id " +
            "</if>" +
            "<if test='params.downloadStatus == \"success\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM emby_download_history " +
            "  GROUP BY emby_item_id" +
            ") d ON e.id = d.emby_item_id " +
            "INNER JOIN emby_download_history dh ON d.emby_item_id = dh.emby_item_id AND d.max_time = dh.create_time " +
            "</if>" +
            "<if test='params.downloadStatus == \"failed\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM emby_download_history " +
            "  GROUP BY emby_item_id" +
            ") d ON e.id = d.emby_item_id " +
            "INNER JOIN emby_download_history dh ON d.emby_item_id = dh.emby_item_id AND d.max_time = dh.create_time " +
            "</if>" +
            "<if test='params.downloadStatus == \"none\"'>" +
            "LEFT JOIN emby_download_history dh2 ON e.id = dh2.emby_item_id " +
            "</if>" +
            "WHERE e.emby_config_id = #{params.configId} " +
            "AND e.parent_id = #{params.libraryId} " +
            "AND e.type != 'Episode' " +
            "<if test='params.transferStatus == \"success\"'>" +
            "AND th.transfer_status = 'success' " +
            "</if>" +
            "<if test='params.transferStatus == \"failed\"'>" +
            "AND th.transfer_status = 'failed' " +
            "</if>" +
            "<if test='params.transferStatus == \"none\"'>" +
            "AND th.id IS NULL " +
            "</if>" +
            "<if test='params.downloadStatus == \"success\"'>" +
            "AND dh.download_status = 'success' " +
            "</if>" +
            "<if test='params.downloadStatus == \"failed\"'>" +
            "AND dh.download_status = 'failed' " +
            "</if>" +
            "<if test='params.downloadStatus == \"none\"'>" +
            "AND dh2.id IS NULL " +
            "</if>" +
            "ORDER BY e.update_time DESC " +
            "LIMIT #{params.limit} OFFSET #{params.offset}" +
            "</script>")
    List<EmbyItem> selectItemsByTransferStatus(@Param("params") Map<String, Object> params);

    /**
     * 统计符合转存状态和下载状态的媒体项数量
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT e.id) FROM emby_item e " +
            "<if test='params.transferStatus == \"success\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM transfer_history " +
            "  GROUP BY emby_item_id" +
            ") t ON e.id = t.emby_item_id " +
            "INNER JOIN transfer_history th ON t.emby_item_id = th.emby_item_id AND t.max_time = th.create_time " +
            "</if>" +
            "<if test='params.transferStatus == \"failed\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM transfer_history " +
            "  GROUP BY emby_item_id" +
            ") t ON e.id = t.emby_item_id " +
            "INNER JOIN transfer_history th ON t.emby_item_id = th.emby_item_id AND t.max_time = th.create_time " +
            "</if>" +
            "<if test='params.transferStatus == \"none\"'>" +
            "LEFT JOIN transfer_history th ON e.id = th.emby_item_id " +
            "</if>" +
            "<if test='params.downloadStatus == \"success\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM emby_download_history " +
            "  GROUP BY emby_item_id" +
            ") d ON e.id = d.emby_item_id " +
            "INNER JOIN emby_download_history dh ON d.emby_item_id = dh.emby_item_id AND d.max_time = dh.create_time " +
            "</if>" +
            "<if test='params.downloadStatus == \"failed\"'>" +
            "INNER JOIN (" +
            "  SELECT emby_item_id, MAX(create_time) as max_time " +
            "  FROM emby_download_history " +
            "  GROUP BY emby_item_id" +
            ") d ON e.id = d.emby_item_id " +
            "INNER JOIN emby_download_history dh ON d.emby_item_id = dh.emby_item_id AND d.max_time = dh.create_time " +
            "</if>" +
            "<if test='params.downloadStatus == \"none\"'>" +
            "LEFT JOIN emby_download_history dh2 ON e.id = dh2.emby_item_id " +
            "</if>" +
            "WHERE e.emby_config_id = #{params.configId} " +
            "AND e.parent_id = #{params.libraryId} " +
            "AND e.type != 'Episode' " +
            "<if test='params.transferStatus == \"success\"'>" +
            "AND th.transfer_status = 'success' " +
            "</if>" +
            "<if test='params.transferStatus == \"failed\"'>" +
            "AND th.transfer_status = 'failed' " +
            "</if>" +
            "<if test='params.transferStatus == \"none\"'>" +
            "AND th.id IS NULL " +
            "</if>" +
            "<if test='params.downloadStatus == \"success\"'>" +
            "AND dh.download_status = 'success' " +
            "</if>" +
            "<if test='params.downloadStatus == \"failed\"'>" +
            "AND dh.download_status = 'failed' " +
            "</if>" +
            "<if test='params.downloadStatus == \"none\"'>" +
            "AND dh2.id IS NULL " +
            "</if>" +
            "</script>")
    Long countItemsByTransferStatus(@Param("params") Map<String, Object> params);
}
