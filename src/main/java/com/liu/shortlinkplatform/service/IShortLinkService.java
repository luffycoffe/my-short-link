package com.liu.shortlinkplatform.service;


import com.liu.shortlinkplatform.common.Result;
import com.liu.shortlinkplatform.dto.ShortLinkCreateDto;
import com.liu.shortlinkplatform.dto.ShortLinkStatDto;
import com.liu.shortlinkplatform.dto.ShortLinkStatusDto;

import java.util.Map;

public interface IShortLinkService {
    /**
     * 创建短链接
     */
    Result<String> createShortLink(ShortLinkCreateDto dto);

    /**
     * 短链接跳转
     */
    Result<String> getLongUrlByShortCode(String shortCode);

    /**
     * 修改短链接状态
     */
    Result<Void> updateStatus(ShortLinkStatusDto dto);

    /**
     * 删除短链接（逻辑删除）
     */
    Result<Void> deleteShortLink(String shortCode);

    /**
     * 查询短链接访问统计
     */
    Result<Map<String, Object>> getAccessStat(ShortLinkStatDto dto);

    /**
     * 记录访问日志（异步）
     */
    void recordAccessLog (String shortCode, String accessIp);
}
