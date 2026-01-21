package com.gdupload;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Google Drive上传管理系统主应用类
 *
 * @author GD Upload Manager
 * @since 2026-01-18
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.gdupload.mapper")
public class GdUploadManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GdUploadManagerApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  GD Upload Manager 启动成功!");
        System.out.println("========================================\n");
    }
}
