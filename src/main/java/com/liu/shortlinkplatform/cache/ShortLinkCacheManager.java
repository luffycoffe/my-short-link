package com.liu.shortlinkplatform.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.liu.shortlinkplatform.config.ShortLinkConfig;
import com.liu.shortlinkplatform.entity.ShortLinkEntity;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 短链接Redis 缓存管理器
 */
@Slf4j
@Component
public class ShortLinkCacheManager {

    //缓存常量
    private static final String NULL_CACHE = "NULL";
    private static final Random RANDOM = new Random();
    private static final long MIN_TTL = 1L;
    //延迟时间
    private static final long DELAY_TIME = 500;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShortLinkConfig shortLinkConfig;
    @Resource
    private RedissonClient redissonClient;
    //spring 线程池
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Resource
    private Cache<String,String> shortLinkLocalCCache;


    /**
     * 统一缓存key
     */
    private String getCacheKey(String shortCode){
        return shortLinkConfig.getCache().getPrefix() + shortCode;
    }

    /**
     * 一级缓存
     */
    private String getLocalCache(String shortCode){
        return shortLinkLocalCCache.getIfPresent(getCacheKey(shortCode));
    }

    private void setLocalCache(String shortCode,String longUrl){
        shortLinkLocalCCache.put(shortCode,longUrl);
    }

    //清理本地缓存
    public void clearLocalCache(String shortCode){
        shortLinkLocalCCache.invalidate(shortCode);
    }

    /**
     * 原子删除缓存
     */
    public void deleteCache(String shortCode){
        String cacheKey = shortLinkConfig.getCache().getPrefix() + shortCode;
        try {
            String script = "return redis.call('del',KEYS[1])";
            stringRedisTemplate.execute(
                    new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(cacheKey)
            );
            log.info("删除缓存成功：{}",cacheKey);
        }catch (Exception e){
            log.error("删除缓存失败：{},服务降级",e);
        }
    }

    /**
     * 延迟双删
     */
    public void deleteCacheWithDelay(String shortCode){
        //第一次立即删除
        clearLocalCache(shortCode);
        deleteCache(shortCode);
        // 异步延迟第二次删除
        taskExecutor.execute(() -> {
            try {
               TimeUnit.MILLISECONDS.sleep(DELAY_TIME);
               deleteCache(shortCode);
               log.info("再次删除缓存成功：{}",shortCode);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 写入缓存
     */
    public void setCache(ShortLinkEntity shortLinkEntity){
        String shortCode = shortLinkEntity.getShortCode();
        String cacheKey = getCacheKey(shortCode);
        try {
            // 写入Hash
            stringRedisTemplate.opsForHash().put(cacheKey,"longUrl",shortLinkEntity.getLongUrl());
            if (shortLinkEntity.getExpireTime()!=null){
                stringRedisTemplate.opsForHash().put(cacheKey,"expireTime",
                        String .valueOf(shortLinkEntity.getExpireTime().getTime()));
            }
            //设置随即过期时间
            long ttl = calculateRandomTTL(shortLinkEntity);
            stringRedisTemplate.expire(cacheKey,ttl, TimeUnit.SECONDS);

            setLocalCache(shortCode,shortLinkEntity.getLongUrl());
        }catch (Exception e){
            log.warn("写入缓存失败：{}",e);
        }
    }


    /**
     * 分布式锁
     */
    public String getCacheWithLock(String shortCode){
        //优先查一级本地缓存
        String localCache = getLocalCache(shortCode);
        if (localCache != null){
            if ("NULL".equals(localCache)){
                return NULL_CACHE;
            }
            log.debug("一级缓存命中:{}",shortCode);
            return localCache;
        }

        //查二级缓存
        String cacheResult = getCache(shortCode);
        if (cacheResult != null){
            setLocalCache(shortCode,cacheResult);
            return cacheResult;
        }
        //缓存未命中，加锁
        String lockKey = "lock:shortLink:" + shortCode;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            //尝试枷锁，等待1s,锁过期3s
            boolean lockSuccess = lock.tryLock(1,3,TimeUnit.SECONDS);
            if (!lockSuccess){
                //二次查缓存
                cacheResult = getCache(shortCode);
                if (cacheResult != null){
                    return cacheResult;
                }
            }
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }finally {
            if (lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
        return null;
    }
    /**
     * 缓存空值
     */
    public void setNullCache(String shortCode){
        String cacheKey = shortLinkConfig.getCache().getPrefix() + shortCode;
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,NULL_CACHE,60,TimeUnit.SECONDS);
        }catch (Exception e){}
    }

    /**
     * 查询缓存
     */
    public String getCache(String shortCode){
        String cacheKey = shortLinkConfig.getCache().getPrefix() + shortCode;
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey))){
                return null ;
            }
            //空值缓存拦截
            if ("spring".equals(stringRedisTemplate.type(cacheKey).name())){
                return NULL_CACHE;
            }
            //读取数据
            String longUrl = (String) stringRedisTemplate.opsForHash().get(cacheKey,"longUrl");
            String expireTime = (String) stringRedisTemplate.opsForHash().get(cacheKey,"expireTime");

            //过期校验
            if (expireTime!=null && Long.parseLong(expireTime) < System.currentTimeMillis()){
                deleteCache(shortCode);
                return NULL_CACHE ;
            }
            return longUrl;
        }catch (Exception e){
            log.warn("Redis异常，降级查询",e);
            return null;
        }
    }

    /**
     * 计算随机过期时间
     */
    private long calculateRandomTTL(ShortLinkEntity shortLinkEntity){
        long baseTTL;
        if (shortLinkEntity.getExpireTime()!=null){
            baseTTL = (shortLinkEntity.getExpireTime().getTime() - System.currentTimeMillis()) / 1000+1;
            baseTTL = Math.max(baseTTL,MIN_TTL);
        }else {
            baseTTL = shortLinkConfig.getCache().getExpireSeconds();
        }
        return baseTTL + RANDOM.nextInt(60);
    }



}
