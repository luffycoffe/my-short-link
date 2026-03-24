package com.liu.shortlinkplatform.utils;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Snowflake;
import com.liu.shortlinkplatform.expection.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成id
 *
 */
@Slf4j
@Component
public class IdGenerator {
    private Snowflake SNOW_FLAKE;
    @Value("${snowflake.worker-id:1}")
    private long workerId ;

    @Value("${snowflake.datacenter-id:1}")
    private long dataCenterId ;

    //雪花算法参数合法范围
    private static final int MAX_WORKER_ID = 31;
    private static final int MAX_DATA_CENTER_ID = 31;
    @PostConstruct
    private void init(){
        try {
            //参数检验
            Assert.isTrue(workerId <= MAX_WORKER_ID&& workerId >= 0,
                    "雪花算法workerId必须在0-31之间，当前值：{}", workerId);
            Assert.isTrue(dataCenterId <= MAX_DATA_CENTER_ID&& dataCenterId >= 0,
                    "雪花算法dataCenterId必须在0-31之间，当前值：{}", dataCenterId);
            //初始化雪花算法
            SNOW_FLAKE = new Snowflake(workerId, dataCenterId);
            log.info("雪花算法初始化成功！workerId={}，dataCenterId={}", workerId, dataCenterId);
        } catch (Exception e) {
            log.error("雪花算法初始化失败！请检查参数：workerId={}，dataCenterId={}", workerId, dataCenterId);
            throw new BusinessException("雪花算法出池化失败");
        }
    }
    // 生成分布式id
    public long generateId(){
        try {
            long id = SNOW_FLAKE.nextId();
            return id;
        } catch (Exception e) {
            log.error("雪花算法生成id失败！",e);
            throw new BusinessException("生成分布式Id失败");
        }
    }
}
