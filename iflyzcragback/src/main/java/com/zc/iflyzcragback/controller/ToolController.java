package com.zc.iflyzcragback.controller;

import com.zc.iflyzcragback.common.Result;
import com.zc.iflyzcragback.dto.ToolToggleRequest;
import com.zc.iflyzcragback.dto.ToolVO;
import com.zc.iflyzcragback.service.rag.tool.ToolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ToolController {

    private final ToolService toolService;

    @GetMapping
    public Result<List<ToolVO>> list() {
        return Result.success(toolService.list());
    }

    @PutMapping("/{name}/toggle")
    public Result<ToolVO> toggle(@PathVariable String name,
                                 @Valid @RequestBody ToolToggleRequest request) {
        return Result.success(toolService.toggle(name, request.getEnabled()));
    }
}
