package com.liu.shortlinkplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "短链接统计查询入参")
public class ShortLinkStatDto {
    /**
     * 短链接编码
     */
    @NotBlank(message = "短码不能为空")
    @Schema(description = "短链接编码", required = true, example = "6Z7890")
    private String shortCode;

    /**
     * 开始时间（yyyy-MM-dd HH:mm:ss）
     */
    @Schema(description = "开始时间", example = "2026-01-01 00:00:00")
    private String startTime;

    /**
     * 结束时间（yyyy-MM-dd HH:mm:ss）
     */
    @Schema(description = "结束时间", example = "2026-01-31 23:59:59")
    private String endTime;
}
