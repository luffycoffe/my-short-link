package com.liu.shortlinkplatform.controller;


import com.liu.shortlinkplatform.common.Result;
import com.liu.shortlinkplatform.dto.ShortLinkCreateDto;
import com.liu.shortlinkplatform.dto.ShortLinkStatDto;
import com.liu.shortlinkplatform.dto.ShortLinkStatusDto;
import com.liu.shortlinkplatform.service.IShortLinkService;
import com.liu.shortlinkplatform.service.impl.ShortLinkServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/short-link")
@Tag(name = "短链接接口",description = "短链接接口")
public class ShortLInkController {
    @Resource
    private IShortLinkService shortLinkService;

    /**
     * 生成短链接
     */
    @PostMapping("/create")
    @Operation(summary = "生成短链接",description = "传入长链接，生成唯一短链接,支持自定义短码、过期时间")
    public Result<String> createShortLink(@Valid @RequestBody ShortLinkCreateDto dto){
        return shortLinkService.createShortLink(dto);
    }

    /**
     * 短链接跳转
     */
    @GetMapping("/r/{shortCode}")
    @Operation(summary = "短链接跳转",description = "传入短链接，跳转到原始长链接")
    public  ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        Result<String> result = shortLinkService.getLongUrlByShortCode(shortCode);
        if (result.getData() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(result.getData()));
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }
    /**
     * 短链接状态更新
     */
    @PostMapping("/status")
    @Operation(summary = "短链接状态更新",description = "传入短链接状态信息，更新短链接状态; 0禁用，1启用")
    public Result<Void> updateStatus(@Valid @RequestBody ShortLinkStatusDto dto){
        return shortLinkService.updateStatus(dto);
    }
    /**
     * 短链接删除
     */
    @DeleteMapping("/delete/{shortCode}")
    @Operation(summary = "短链接删除",description = "传入短链接，删除短链接(逻辑删除)")
    public Result<Void> deleteShortLink(@PathVariable String shortCode){
        return shortLinkService.deleteShortLink(shortCode);
    }
    /**
     * 短链接访问统计
     */
    @PostMapping("/stat")
    @Operation(summary = "短链接访问统计",description = "访问统计信息")
    public Result<Map<String, Object>> getAccessStat(@Valid @RequestBody ShortLinkStatDto dto){
        return shortLinkService.getAccessStat(dto);
    }

}
