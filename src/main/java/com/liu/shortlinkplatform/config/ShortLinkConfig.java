package com.liu.shortlinkplatform.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "short-link")
public class ShortLinkConfig {
    //短链接域名
    private String domain;
    //缓存配置
    private CacheConfig cache;
    //短码配置
    private CodeConfig  code;

    //缓存配置
    @Data
    public static class CacheConfig {
        private String prefix;
        private Long expireSeconds;
    }
    //短码配置
    @Data
    public static class CodeConfig {
        private Integer minLength;
    }

}
