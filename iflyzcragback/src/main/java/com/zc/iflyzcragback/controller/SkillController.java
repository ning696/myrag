package com.zc.iflyzcragback.controller;

import com.zc.iflyzcragback.common.Result;
import com.zc.iflyzcragback.dto.SkillToggleRequest;
import com.zc.iflyzcragback.dto.SkillVO;
import com.zc.iflyzcragback.service.rag.skill.SkillService;
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
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SkillController {
    private final SkillService skillService;

    @GetMapping
    public Result<List<SkillVO>> list() {
        return Result.success(skillService.list());
    }

    @PutMapping("/{name}/toggle")
    public Result<SkillVO> toggle(@PathVariable String name,
                                  @Valid @RequestBody SkillToggleRequest request) {
        return Result.success(skillService.toggle(name, request.getEnabled()));
    }
}
