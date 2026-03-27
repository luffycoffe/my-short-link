package com.liu.shortlinkplatform.mq;

import com.liu.shortlinkplatform.config.RabbitMQConfig;
import com.liu.shortlinkplatform.entity.ShortLinkAccessLog;
import com.liu.shortlinkplatform.mapper.ShortLinkAccessLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LogConsumer {

    @Resource
    private ShortLinkAccessLogMapper accessLogMapper;

    // 监听队列，异步、批量、削峰写入日志
    @RabbitListener(queues = RabbitMQConfig.ACCESS_LOG_QUEUE)
    public void handleLog(ShortLinkAccessLog accessLog) {
        try {
            accessLogMapper.insert(accessLog);
            log.debug("日志异步写入成功：{}", accessLog.getShortCode());
        } catch (Exception e) {
            log.error("日志写入失败", e);
        }
    }
}
