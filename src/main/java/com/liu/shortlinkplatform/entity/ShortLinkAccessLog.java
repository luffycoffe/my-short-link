package com.liu.shortlinkplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_short_link_access_log")
public class ShortLinkAccessLog {
    /**
     * 日志ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 短链接编码
     */
    private String shortCode;

    /**
     * 访问IP
     */
    private String accessIp;

    /**
     * 访问时间
     */
    private Date accessTime;

    /**
     * 访问来源（可选：PC/移动端/微信）
     */
    private String accessSource;
}
