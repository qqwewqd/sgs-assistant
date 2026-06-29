package com.sanguosha.assistant.controller;

import com.sanguosha.assistant.service.IdentityModeService;
import com.sanguosha.assistant.vo.IdentityModeVO;
import com.sanguosha.assistant.vo.Result;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity-modes")
@RequiredArgsConstructor
public class IdentityModeController {
    private final IdentityModeService identityModeService;

    @GetMapping
    public Result<List<IdentityModeVO>> modes() {
        return Result.success(identityModeService.listModes(false));
    }
}
