/*
package com.liu.shortlinkplatform.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.liu.shortlinkplatform.entity.ShortLinkEntity;
import com.liu.shortlinkplatform.mapper.ShortLinkMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShortLinkBloomFilter {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTmeplate;

    @Resource
    private ShortLinkMapper shortLinkMapper;

    //布隆过滤器名称
    private static final String BLOOM_FILTER_NAME = "short_link_bloom_filter";
    //预计存储的短码数量
    private static final long EXPECTED_NUMBER_OF_ELEMENTS = 100000L;
    //误判率
    private static final double FALSE_POSITIVE_PROBABILITY = 0.01;

    private static final long BIT_SIZE = (long) (-EXPECTED_NUMBER_OF_ELEMENTS * Math.log(FALSE_POSITIVE_PROBABILITY) / (Math.log(2) * Math.log(2)));

    */
/**
     * 项目启动初始化布隆过滤器，加载所有有效短码
     *//*

    @PostConstruct
    public void initBloomFilter(){
        //获取布隆过滤器
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);
        bloomFilter.tryInit(EXPECTED_NUMBER_OF_ELEMENTS, FALSE_POSITIVE_PROBABILITY);

        //查询数据库中所有有效短码
        List<ShortLinkEntity> validShortLinks = shortLinkMapper.selectList(
                Wrappers.lambdaQuery(ShortLinkEntity.class)
                        .eq(ShortLinkEntity::getStatus, 1)
                        .eq(ShortLinkEntity::getIsDeleted, 0)
        );

        //添加所有有效短码到布隆过滤器
        for (ShortLinkEntity shortLink : validShortLinks) {
            bloomFilter.add(shortLink.getShortCode());
        }
       }

     //判断短码是否存在
    public boolean contains(String shortCode) {
        try {
            int[] hashes = getHashIndexes(shortCode);
            for (int index : hashes) {
                if (!stringRedisTmeplate.opsForValue().getBit(BLOOM_FILTER_NAME, index)) {
                    return false;
                }
            }
            return true;
        }catch (Exception e){
            return true;
        }

    }

    */
/**
     * 新增短码到布隆过滤器
     *//*

    public void add(String shortCode) {
        try{
            int[] hashes = getHashIndexes(shortCode);
            for (int index : hashes) {
                stringRedisTmeplate.opsForValue().setBit(BLOOM_FILTER_NAME, index, true);
            }
        } catch (Exception e){}
    }

    */
/**
     * 多重hash计算索引
     *//*

    private int[] getHashIndexes(String value) {
        int hash1 =value.hashCode();
        int hash2 =hash1 >>> 16;
        return new int[]{
                Math.abs(hash1 % (int) BIT_SIZE),
                Math.abs(hash2 % (int) BIT_SIZE)
        };

    }

}
*/

package com.liu.shortlinkplatform.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ShortLinkBloomFilter {

    @Resource
    private RedissonClient redissonClient;

    private RBloomFilter<String> bloomFilter;

    // 项目启动初始化
    @PostConstruct
    public void init() {
        bloomFilter = redissonClient.getBloomFilter("short_link_bloom_filter");
        // 初始化：10万数据，0.01误差率
        bloomFilter.tryInit(100000L, 0.01);
        log.info("Redisson布隆过滤器初始化完成（无大Key、高性能）");
    }

    // 添加短码
    public void add(String shortCode) {
        bloomFilter.add(shortCode);
    }

    // 判断短码是否存在
    public boolean contains(String shortCode) {
        return bloomFilter.contains(shortCode);
    }
}


