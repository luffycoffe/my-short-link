package com.liu.shortlinkplatform.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 生成短链接入参DTO
 */

@Data
@Schema(description = "生成短链接入参")
public class ShortLinkCreateDto {
    /**
     * 原始长连接
     */
    @NotBlank(message = "长连接不能为空")
    @Pattern(regexp = "^https?://[\\w.-]+(?:/[\\w.-]*)*(?:\\?[\\w.&=%-]*)?$", message = "长连接格式非法")
    @Schema(description = "原始长连接", required = true,example = "https??www.baidu.com")
    private String longUrl;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "过期时间（yyyy-MM-dd HH:mm:ss）", example = "2026-12-31 23:59:59")
    private String expireTime;

    /**
     * 自定义短码（可选，字母+数字，6-10位）
     */
    @Pattern(regexp = "^[a-zA-Z0-9]{6,10}$", message = "自定义短码仅支持6-10位字母/数字" )
    @Schema(description = "自定义短码（6-10位字母/数字）\", example = \"mycode123")
    private String customCode;
}
