package com.zc.iflyzcragback.controller;

import com.zc.iflyzcragback.common.Result;
import com.zc.iflyzcragback.dto.PluginConfigRequest;
import com.zc.iflyzcragback.dto.PluginToggleRequest;
import com.zc.iflyzcragback.dto.PluginVO;
import com.zc.iflyzcragback.service.PluginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
/**
 * 插件管理接口，仅管理员可访问。
 */
public class PluginController {

    private final PluginService pluginService;

    @GetMapping
    public Result<List<PluginVO>> list() {
        return Result.success(pluginService.list());
    }

    @PutMapping("/{name}/toggle")
    public Result<PluginVO> toggle(@PathVariable String name,
                                   @Valid @RequestBody PluginToggleRequest request) {
        return Result.success(pluginService.toggle(name, request.getEnabled()));
    }

    @PutMapping("/{name}/config")
    public Result<PluginVO> updateConfig(@PathVariable String name,
                                         @RequestBody PluginConfigRequest request) {
        return Result.success(pluginService.updateConfig(name, request));
    }
}
