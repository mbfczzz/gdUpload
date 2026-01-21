package com.gdupload.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 日期时间工具类
 * 统一使用东8区（Asia/Shanghai）时间
 *
 * @author GD Upload Manager
 * @since 2026-01-21
 */
public class DateTimeUtil {

    /**
     * 东8区时区
     */
    public static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * 获取当前东8区时间
     *
     * @return 东8区的当前时间
     */
    public static LocalDateTime now() {
        return ZonedDateTime.now(ZONE_SHANGHAI).toLocalDateTime();
    }

    /**
     * 获取当前东8区的ZonedDateTime
     *
     * @return 东8区的当前时间（带时区信息）
     */
    public static ZonedDateTime nowZoned() {
        return ZonedDateTime.now(ZONE_SHANGHAI);
    }

    /**
     * 将LocalDateTime转换为东8区的ZonedDateTime
     *
     * @param localDateTime 本地时间
     * @return 东8区的时间（带时区信息）
     */
    public static ZonedDateTime toZoned(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZONE_SHANGHAI);
    }
}
