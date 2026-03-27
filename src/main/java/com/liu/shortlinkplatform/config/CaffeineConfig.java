package com.liu.shortlinkplatform.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置
 * 一级缓存：内存级，超高读写性能
 */
@Configuration
public class CaffeineConfig {
    /**
     * 短链接本地缓存
     * 最大容量：10000条
     * 写入后30分钟过期（比Redis过期时间短，保证一致性）
     */
    @Bean
    public Cache<String, String> shortLinkLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }
}
