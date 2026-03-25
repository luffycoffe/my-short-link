package com.liu.shortlinkplatform.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    /**
     * 配置Redisson客户端，用于布隆过滤，分布式锁
     */
    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        //连接Redis
        config.useSingleServer()
                .setAddress("redis://192.168.106.130:6379")
                .setPassword("123321")   // 在这里设置密码
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(10);

        return Redisson.create(config);

    }
}
