package com.liu.shortlinkplatform.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    //访问日志队列
    public static final String ACCESS_LOG_QUEUE = "access_log_queue";

    @Bean
    public Queue accessLogQueue() {
        return new Queue(ACCESS_LOG_QUEUE , true);
    }
}
