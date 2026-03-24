package com.liu.shortlinkplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_short_link")
public class ShortLinkEntity {
    /**
     * 主键ID
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 原始长链接
     */
    private String longUrl;

    /**
     * 短链接后缀
     */
    private String shortCode;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 过期时间
     */
     private Date expireTime;

    /**
     * 访问次数
     */
    private Integer visitCount;

    /**
     * 逻辑删除标识
     */
    @TableLogic
    private Integer isDeleted;
}
