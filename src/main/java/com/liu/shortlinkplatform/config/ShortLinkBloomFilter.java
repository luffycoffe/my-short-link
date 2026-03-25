package com.liu.shortlinkplatform.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.liu.shortlinkplatform.entity.ShortLinkEntity;
import com.liu.shortlinkplatform.mapper.ShortLinkMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShortLinkBloomFilter {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ShortLinkMapper shortLinkMapper;

    //布隆过滤器名称
    private static final String BLOOM_FILTER_NAME = "short_link_bloom_filter";
    //预计存储的短码数量
    private static final long EXPECTED_NUMBER_OF_ELEMENTS = 100000;
    //误判率
    private static final double FALSE_POSITIVE_PROBABILITY = 0.0001;

    /**
     * 项目启动初始化布隆过滤器，加载所有有效短码
     */
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
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);
        return bloomFilter.contains(shortCode);
    }

    /**
     * 新增短码到布隆过滤器
     */
    public void add(String shortCode) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);
        bloomFilter.add(shortCode);
    }
}



