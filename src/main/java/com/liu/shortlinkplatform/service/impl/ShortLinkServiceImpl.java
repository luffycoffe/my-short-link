package com.liu.shortlinkplatform.service.impl;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liu.shortlinkplatform.cache.ShortLinkCacheManager;
import com.liu.shortlinkplatform.common.Result;
import com.liu.shortlinkplatform.config.RabbitMQConfig;
import com.liu.shortlinkplatform.config.ShortLinkBloomFilter;
import com.liu.shortlinkplatform.config.ShortLinkConfig;
import com.liu.shortlinkplatform.dto.ShortLinkCreateDto;
import com.liu.shortlinkplatform.dto.ShortLinkStatDto;
import com.liu.shortlinkplatform.dto.ShortLinkStatusDto;
import com.liu.shortlinkplatform.entity.ShortLinkAccessLog;
import com.liu.shortlinkplatform.entity.ShortLinkEntity;
import com.liu.shortlinkplatform.enums.ResultCodeEnum;
import com.liu.shortlinkplatform.expection.BusinessException;
import com.liu.shortlinkplatform.mapper.ShortLinkAccessLogMapper;
import com.liu.shortlinkplatform.mapper.ShortLinkMapper;
import com.liu.shortlinkplatform.service.IShortLinkService;
import com.liu.shortlinkplatform.utils.IdGenerator;
import com.liu.shortlinkplatform.utils.ShortCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.apache.catalina.AccessLog;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;


import java.util.Date;
import java.util.HashMap;
import java.util.Map;



@Slf4j
@Service
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkEntity> implements IShortLinkService {

    @Resource
    private IdGenerator idGenerator;
    @Resource
    private ShortCode shortCode;
    @Resource
    private ShortLinkConfig shortLinkConfig;
    @Resource
    private ShortLinkAccessLogMapper accessLogMapper;
    @Resource
    private ShortLinkBloomFilter shortLinkBloomFilter;
    @Resource
    private ShortLinkCacheManager cacheManager;
    @Resource
    private RabbitTemplate rabbitTemplate;


    /**
     * 创建短链接
     */
    @SentinelResource(
            value = "createShortLink",
            blockHandler = "createBlockHandler",
            fallback = "createFallbackHandler"
    )
    @Transactional(rollbackFor = Exception.class)//异常回滚
    @Override
    public Result<String> createShortLink(ShortLinkCreateDto dto){

        //短码校验
        String shortCode = generateShortCode(dto);
        //构建实体
        ShortLinkEntity shortLinkEntity = buildeShortLinkEntity(dto, shortCode);
        boolean saveSuccess = this.save(shortLinkEntity);
        if (!saveSuccess){
            log.error("链接入库失败，shortCode={}",shortCode);
            throw new BusinessException("短链接创建失败");
        }
        //布隆过滤+缓存预热
        shortLinkBloomFilter.add(shortCode);
        cacheManager.setCache(shortLinkEntity);
        return Result.success(shortLinkConfig.getDomain() +"/short-link/r/" +shortCode);

    }

    /**
     * 短链接跳转
     */
    @Override
    public Result<String> getLongUrlByShortCode(String shortCode){
        //布隆过滤器拦截无效短码
        if (!shortLinkBloomFilter.contains(shortCode)){
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
        }
        if (!this.shortCode.isValidShortCode(shortCode)){
            throw new BusinessException("短码不合法");
        }

        //查缓存
        String cacheResult = cacheManager.getCacheWithLock(shortCode);
        if (cacheResult != null){
            if ("NULL".equals(cacheResult))
                throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
            ShortLinkAccessLog accessLog = new ShortLinkAccessLog();
            accessLog.setShortCode(shortCode);
            accessLog.setAccessIp(getClientIp());
            accessLog.setAccessTime(new Date());
            accessLog.setAccessSource("PC");
            updateVisitCount(shortCode);
            return Result.success(cacheResult);
        }

        //查库
        ShortLinkEntity shortLinkEntity = getValidShortLink(shortCode);
        //写回缓存
        cacheManager.setCache(shortLinkEntity);
        //日志计数
        ShortLinkAccessLog accessLog = new ShortLinkAccessLog();
        accessLog.setShortCode(shortCode);
        accessLog.setAccessIp(getClientIp());
        accessLog.setAccessTime(new Date());
        accessLog.setAccessSource("PC");
        //异步发送
        rabbitTemplate.convertAndSend(RabbitMQConfig.ACCESS_LOG_QUEUE, log);

        return Result.success(shortLinkEntity.getLongUrl());
    }



    /**
     * 修改短链接状态（启用/禁用）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> updateStatus(ShortLinkStatusDto dto) {

        if (dto.getStatus() !=0 && dto.getStatus() != 1){
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "状态只能是0（禁用）或1（启用）");
        }

        //更新数据库
        update(new LambdaUpdateWrapper<ShortLinkEntity>()
                .set(ShortLinkEntity::getStatus, dto.getStatus())
                .eq(ShortLinkEntity::getShortCode, dto.getShortCode()));

        //删除缓存
        cacheManager.deleteCacheWithDelay(dto.getShortCode());
        return Result.success();
    }


    /**
     * 删除短链接（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> deleteShortLink(String shortCode) {

        remove(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, shortCode));
        cacheManager.deleteCacheWithDelay(shortCode);
        return Result.success();
    }

    /**
     * 查询短链接访问统计
     */
    @Override
    public Result<Map<String, Object>> getAccessStat(ShortLinkStatDto dto) {
       /* log.info("查询短链接统计：shortCode={}, startTime={}, endTime={}",
                dto.getShortCode(), dto.getStartTime(), dto.getEndTime());

        // 1. 校验短链接是否存在
        ShortLinkEntity shortLink = this.getOne(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, dto.getShortCode())
                .eq(ShortLinkEntity::getIsDeleted, 0));
        if (shortLink == null) {
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
        }

        // 2. 构建查询条件
        LambdaQueryWrapper<ShortLinkAccessLog> queryWrapper = new LambdaQueryWrapper<ShortLinkAccessLog>()
                .eq(ShortLinkAccessLog::getShortCode, dto.getShortCode());
        // 时间范围过滤
        if (StrUtil.isNotBlank(dto.getStartTime())) {
            queryWrapper.ge(ShortLinkAccessLog::getAccessTime, DateUtil.parse(dto.getStartTime()));
        }
        if (StrUtil.isNotBlank(dto.getEndTime())) {
            queryWrapper.le(ShortLinkAccessLog::getAccessTime, DateUtil.parse(dto.getEndTime()));
        }

        // 3. 统计核心数据
        long totalCount = accessLogMapper.selectCount(queryWrapper); // 总访问次数
        // 其他统计：按IP/按时间（可选扩展）

        // 4. 封装结果
        Map<String, Object> statResult = new HashMap<>();
        statResult.put("shortCode", dto.getShortCode());
        statResult.put("longUrl", shortLink.getLongUrl());
        statResult.put("totalVisitCount", totalCount);
        statResult.put("dbVisitCount", shortLink.getVisitCount()); // 数据库记录的次数
        statResult.put("status", shortLink.getStatus() == 1 ? "启用" : "禁用");
        statResult.put("expireTime", shortLink.getExpireTime());

        log.info("查询短链接统计成功：shortCode={}, totalCount={}", dto.getShortCode(), totalCount);
        return Result.success(statResult);*/
        ShortLinkEntity link = getOne(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, dto.getShortCode())
                .eq(ShortLinkEntity::getIsDeleted, 0));
        if (link == null){
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
        }
        LambdaQueryWrapper<ShortLinkAccessLog> queryWrapper = new LambdaQueryWrapper<ShortLinkAccessLog>()
                .eq(ShortLinkAccessLog::getShortCode, dto.getShortCode());
        if (StrUtil.isNotBlank(dto.getStartTime())){
            queryWrapper.ge(ShortLinkAccessLog::getAccessTime, DateUtil.parse(dto.getStartTime()));
        }
        if (StrUtil.isNotBlank(dto.getEndTime())){
            queryWrapper.le(ShortLinkAccessLog::getAccessTime, DateUtil.parse(dto.getEndTime()));
        }

        Map<String, Object> map = new HashMap<>();
        map.put("totalVisitCount", accessLogMapper.selectCount(queryWrapper));
        map.put("longUrl",link.getLongUrl());
        map.put("status",link.getStatus() == 1 ? "启用" : "禁用");
        return Result.success(map);
    }

    /**
     * 短码生成和校验
     */
    private String generateShortCode(ShortLinkCreateDto dto){
        if (StrUtil.isNotBlank(dto.getCustomCode())){
            if (getOne(new LambdaQueryWrapper<ShortLinkEntity>()
                    .eq(ShortLinkEntity::getShortCode, dto.getCustomCode())
                    .eq(ShortLinkEntity::getStatus, 1))!= null){
                throw new BusinessException("自定义短码已存在");
            }
            return dto.getCustomCode();
        }
        String code = shortCode.idToShortCode(idGenerator.generateId());
        if (getOne(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, code))!= null){
           throw new BusinessException("短码生成冲突");
        }
        return code;
    }

    /**
     * 创建实体
     */
    private ShortLinkEntity buildeShortLinkEntity(ShortLinkCreateDto dto, String shortCode){
        ShortLinkEntity entity = new ShortLinkEntity();
        entity.setId(idGenerator.generateId());
        entity.setShortCode(shortCode);
        entity.setLongUrl(dto.getLongUrl());
        entity.setStatus(1);
        entity.setIsDeleted(0);
        entity.setCreateTime(new Date());
        entity.setVisitCount(0);
        if (StrUtil.isNotBlank(dto.getExpireTime())){
            if (DateUtil.parse(dto.getExpireTime()).getTime() > System.currentTimeMillis()) {
                entity.setExpireTime(DateUtil.parse(dto.getExpireTime()));
            }else {
                throw new BusinessException("短链接已过期");
            }
        }

        return entity;
    }


    /**
     * 查库
     */
    private ShortLinkEntity getValidShortLink(String shortCode){
        ShortLinkEntity entity = getOne(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, shortCode)
                .eq(ShortLinkEntity::getStatus, 1)
                .eq(ShortLinkEntity::getIsDeleted, 0));
        if (entity == null){
            cacheManager.setNullCache(shortCode);
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
        }
        if (entity.getExpireTime()!= null && entity.getExpireTime().before(new Date())){
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_EXPIRED);
        }
        return entity;
    }


    /**
     * 异步记录访问日志
     */
    @Async // 异步执行，不阻塞跳转
    @Override
    public void recordAccessLog(String shortCode, String ip) {
        try {
            log.debug("记录访问日志：shortCode={}, ip={}", shortCode, ip);
            ShortLinkAccessLog logEntity = new ShortLinkAccessLog();
            logEntity.setShortCode(shortCode);
            logEntity.setAccessIp(ip);
            logEntity.setAccessTime(new Date());
            logEntity.setAccessSource("PC"); // 简化处理，可扩展解析User-Agent
            accessLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.error("记录访问日志失败：shortCode={}", shortCode, e);
        }
    }

    /**
     * 辅助方法：更新访问次数
     */
    private void updateVisitCount(String shortCode) {
        try {
            this.update(new LambdaUpdateWrapper<ShortLinkEntity>()
                    .setSql("visit_count = visit_count + 1")
                    .eq(ShortLinkEntity::getShortCode, shortCode)
                    .eq(ShortLinkEntity::getIsDeleted, 0));
        } catch (Exception e) {
            log.error("更新访问次数失败：shortCode={}", shortCode, e);
        }
    }
    /**
     * 辅助方法：获取客户端IP（简化版，生产需扩展）
     */
    private String getClientIp() {
        // 生产环境需从RequestContextHolder获取真实IP，简化返回固定值
        return "127.0.0.1";
    }

    //热点限流
    public Result<String> createBlockHandler(ShortLinkCreateDto dto, BlockException ex) {
       return Result.fail("请求过于频繁！禁止恶意批量创建短链接");
    }
    // 降级处理
    public Result<Void> createFallbackHandler(ShortLinkStatusDto dto) {
        return Result.fail("系统繁忙，请稍后再试");
    }


}
