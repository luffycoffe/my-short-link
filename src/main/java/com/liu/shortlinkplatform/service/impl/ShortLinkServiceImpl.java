package com.liu.shortlinkplatform.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.shortlinkplatform.common.Result;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkEntity> implements IShortLinkService {

//public class ShortLinkServiceImpl implements IShortLinkService {
    @Resource
    private IdGenerator idGenerator;
    @Resource
    private ShortCode shortCode;
    @Resource
    private ShortLinkMapper shortLinkMapper;
    @Resource
    private ShortLinkConfig shortLinkConfig;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShortLinkAccessLogMapper accessLogMapper;

    /**
     * 创建短链接
     */
    @Transactional(rollbackFor = Exception.class)//异常回滚
    @Override
    public Result<String> createShortLink(ShortLinkCreateDto dto){
        log.info("创建短链接开始:longUrl={},customCode={},expireTime={}",
                dto.getLongUrl(),dto.getCustomCode(),dto.getCustomCode());

        String codeShort;
        if (StrUtil.isNotBlank(dto.getCustomCode())){
            //校验自定义短码是否已经存在
            ShortLinkEntity existLink = this.getOne(
                    new LambdaQueryWrapper<ShortLinkEntity>()
                            .eq(ShortLinkEntity::getShortCode, dto.getCustomCode())
                            .eq(ShortLinkEntity::getStatus, 0)
            );
            if (existLink != null){
                throw new BusinessException("自定义短码已存在");
            }
            codeShort = dto.getCustomCode();
        }else {
            // 生成ID
            long id = idGenerator.generateId();
            //生成6位短码
            codeShort = shortCode.idToShortCode(id);
            //双重校验短码唯一性
            ShortLinkEntity existLink = this.getOne(
                    new LambdaQueryWrapper<ShortLinkEntity>()
                            .eq(ShortLinkEntity::getShortCode, codeShort)
                            .eq(ShortLinkEntity::getIsDeleted, 0)
            );
            if (existLink != null){
                throw new BusinessException("短码生成冲突，请稍后再试");
            }
        }
        //过期时间合理性校验
        if(dto.getExpireTime()!=null){
            Date now = DateUtil.truncate(new Date(), DateField.SECOND);
            Date expireDate = DateUtil.parse(dto.getExpireTime());
            if (expireDate.before(now)){
                log.error("过期时间不能早于当前时间");
                throw new BusinessException("过期时间不能早于当前时间");
            }
        }
        //生成实体
        ShortLinkEntity shortLinkEntity = new ShortLinkEntity();
        shortLinkEntity.setId(idGenerator.generateId());
        shortLinkEntity.setLongUrl(dto.getLongUrl());
        shortLinkEntity.setShortCode(codeShort);
        shortLinkEntity.setCreateTime(new Date());
        shortLinkEntity.setStatus(1);//默认有效
        //处理过期时间
        if (StrUtil.isNotBlank(dto.getExpireTime())){
            try {
                shortLinkEntity.setExpireTime(DateUtil.parse(dto.getExpireTime()));
            } catch (Exception e){
                throw new BusinessException("过期时间格式错误，请输入yyyy-MM-dd HH:mm:ss");
            }
        }
        shortLinkEntity.setVisitCount(0);//默认0
        shortLinkEntity.setIsDeleted(0);//默认未删除
        //4. 入库
        boolean saveSuccess = this.save(shortLinkEntity);
        if (!saveSuccess){
            log.error("链接入库失败，shortCode={}",shortCode);
            throw new BusinessException("短链接创建失败");
        }

        //缓存预热（Redis)
        String cacheKey = shortLinkConfig.getCache().getPrefix() + codeShort;
        Long expireSeconds;
        if (dto.getExpireTime()!=null){
            //业务过期时间有效：计算expireTime与当前时间的差值
            Date now = new Date();
            Date expireDate = DateUtil.parse(dto.getExpireTime());
            expireSeconds = (expireDate.getTime() - now.getTime())/1000 +1;
            if (expireSeconds <= 0){
                expireSeconds = 1L;
            }
        }else {
            // 使用配置文件的默认过期时间
            expireSeconds = shortLinkConfig.getCache().getExpireSeconds();
        }
        if (dto.getExpireTime()!=null){
            //存储过期时间戳
            Date expireDate = DateUtil.parse(dto.getExpireTime(), "yyyy-MM-dd HH:mm:ss");
            stringRedisTemplate.opsForHash().put(cacheKey, "expireTime", String.valueOf(expireDate.getTime()));
        }
        //存储长连接
        stringRedisTemplate.opsForHash().put(cacheKey, "longUrl", dto.getLongUrl());
        //设置Redis过期时间
        stringRedisTemplate.expire(cacheKey,expireSeconds , TimeUnit.SECONDS);
        //拼接完整短链接
        String shortUrl = shortLinkConfig.getDomain() + "/short-link/r/" + codeShort;
        return Result.success(shortUrl);
    }

    /**
     * 短链接跳转
     */
    @Override
    public Result<String> getLongUrlByShortCode(String shortCode){
        log.info("短链接跳转开始，shortCode={}",shortCode);
        //检验短码合法性
        if(!this.shortCode.isValidShortCode(shortCode)){
            throw new BusinessException("短码不合法");
        }
        //查Redis缓存
        String cacheKey = shortLinkConfig.getCache().getPrefix() + shortCode;
       /* String longUrl = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(longUrl)){
            log.info("从Redis缓存中获取短链接成功，key={},value={}",cacheKey,longUrl);
            //异步记录访问日志
            recordAccessLog(shortCode,getClientIp());
            updateVisitCount(shortCode);
            return Result.success(longUrl);
        }*/
        if (stringRedisTemplate.hasKey(cacheKey)) {
            String longUrl = (String) stringRedisTemplate.opsForHash().get(cacheKey, "longUrl");
            String expireTime = (String) stringRedisTemplate.opsForHash().get(cacheKey, "expireTime");
            //二次校验是否过期
            if (StrUtil.isNotBlank(expireTime)) {
                long expireTimeStamp = Long.parseLong(expireTime);
                if (expireTimeStamp < System.currentTimeMillis()) {
                    //缓存已过期
                    stringRedisTemplate.delete(cacheKey);
                    log.debug("缓存已过期，key={}", cacheKey);
                } else {
                    log.info("从Redis缓存中获取短链接成功，key={},value={}", cacheKey, longUrl);
                    //异步记录访问日志
                    recordAccessLog(shortCode, getClientIp());
                    updateVisitCount(shortCode);
                    return Result.success(longUrl);
                }
            } else {
                //缓存默认过期时间
                log.debug("从Redis缓存中获取短链接成功，key={},value={}", cacheKey, longUrl);
                //异步记录访问日志
                recordAccessLog(shortCode, getClientIp());
                updateVisitCount(shortCode);
                return Result.success(longUrl);
            }
        }
        //缓存未命中
        ShortLinkEntity shortLinkEntity = this.getOne(
                new LambdaQueryWrapper<ShortLinkEntity>()
                        .eq(ShortLinkEntity::getShortCode, shortCode)
                        .eq(ShortLinkEntity::getStatus, 1)
        );
        if (shortLinkEntity == null){
            log.warn("短链接不存在，shortCode={}",shortCode);
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
        }
        //校验状态
        if (shortLinkEntity.getStatus() != 1){
            log.warn("短链接已禁用，shortCode={}",shortCode);
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_DISABLE);
        }
        //校验过期时间
        if (shortLinkEntity.getExpireTime() != null && shortLinkEntity.getExpireTime().before(new Date())){
            log.warn("短链接已过期，shortCode={}",shortCode);
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_EXPIRED);
        }
        //写回缓存
        stringRedisTemplate.opsForValue().set(cacheKey, shortLinkEntity.getLongUrl(),
                shortLinkConfig.getCache().getExpireSeconds(),TimeUnit.SECONDS);

        //记录访问日志和更新次数
        recordAccessLog(shortCode,getClientIp());
        updateVisitCount(shortCode);
        log.info("查询短链接成功：shortCode={}, longUrl={}", shortCode, shortLinkEntity.getLongUrl());
        return Result.success(shortLinkEntity.getLongUrl());
    }

    /**
     * 修改短链接状态（启用/禁用）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> updateStatus(ShortLinkStatusDto dto) {
        log.info("修改短链接状态：shortCode={}, targetStatus={}", dto.getShortCode(), dto.getStatus());

        // 1. 校验状态合法性
        if (dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "状态只能是0（禁用）或1（启用）");
        }

        // 2. 校验短链接是否存在（未删除）
        ShortLinkEntity shortLink = this.getOne(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, dto.getShortCode())
                .eq(ShortLinkEntity::getIsDeleted, 0));
        if (shortLink == null) {
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
        }

        // 3. 修改状态
        boolean updateSuccess = this.update(new LambdaUpdateWrapper<ShortLinkEntity>()
                .set(ShortLinkEntity::getStatus, dto.getStatus())
                .eq(ShortLinkEntity::getShortCode, dto.getShortCode())
                .eq(ShortLinkEntity::getIsDeleted, 0));
        if (!updateSuccess) {
            log.error("修改短链接状态失败：shortCode={}", dto.getShortCode());
            throw new BusinessException("修改状态失败");
        }

        // 4. 禁用时删除缓存
        if (dto.getStatus() == 0) {
            String cacheKey = shortLinkConfig.getCache().getPrefix() + dto.getShortCode();
            stringRedisTemplate.delete(cacheKey);
            log.debug("禁用短链接，删除缓存：{}", cacheKey);
        }

        log.info("修改短链接状态成功：shortCode={}, status={}", dto.getShortCode(), dto.getStatus());
        return Result.success();
    }


    /**
     * 删除短链接（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> deleteShortLink(String shortCode) {
        log.info("删除短链接：shortCode={}", shortCode);

        // 1. 校验短链接是否存在
        ShortLinkEntity shortLink = this.getOne(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, shortCode)
                .eq(ShortLinkEntity::getIsDeleted, 0));
        if (shortLink == null) {
            throw new BusinessException(ResultCodeEnum.SHORT_LINK_NOT_EXIT);
        }

        // 2. 逻辑删除（MP的@TableLogic自动处理）
        boolean deleteSuccess = this.remove(new LambdaQueryWrapper<ShortLinkEntity>()
                .eq(ShortLinkEntity::getShortCode, shortCode)
                .eq(ShortLinkEntity::getIsDeleted, 0));
        if (!deleteSuccess) {
            log.error("删除短链接失败：shortCode={}", shortCode);
            throw new BusinessException("删除失败");
        }

        // 3. 删除缓存
        String cacheKey = shortLinkConfig.getCache().getPrefix() + shortCode;
        stringRedisTemplate.delete(cacheKey);

        log.info("删除短链接成功：shortCode={}", shortCode);
        return Result.success();
    }

    /**
     * 查询短链接访问统计
     */
    @Override
    public Result<Map<String, Object>> getAccessStat(ShortLinkStatDto dto) {
        log.info("查询短链接统计：shortCode={}, startTime={}, endTime={}",
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
        return Result.success(statResult);
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


}
