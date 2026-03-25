package com.liu.shortlinkplatform.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    //缓存查询
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    //缓存新增（无过期时间）
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    //缓存新增（带过期时间）
    public void set(String key, Object value, long timeout, TimeUnit unit){
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    //缓存删除
    public Boolean delete(String key){
        return redisTemplate.delete(key);
    }

    //缓存是否存在
    public Boolean hasKey(String key){
        return redisTemplate.hasKey(key);
    }
}
