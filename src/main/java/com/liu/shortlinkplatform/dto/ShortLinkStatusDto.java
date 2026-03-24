package com.liu.shortlinkplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "短链接状态修改入参")
public class ShortLinkStatusDto {
    /**
     * 短链接ID
     */
    @NotBlank(message = "短链接不能为空")
    @Schema(description = "短链接编码", required = true,example = "6z7980")
    private String shortCode;

    /**
     * 目标状态： 1：正常 2：禁用
     */
    @NotNull(message = "目标状态不能为空")
    @Schema(description = "目标状态： 1：正常 2：禁用", required = true,example = "1")
    private Integer status;
}
