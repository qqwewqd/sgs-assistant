package com.sanguosha.assistant.controller;

import com.sanguosha.assistant.service.AppSettingService;
import com.sanguosha.assistant.vo.Result;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class AppSettingController {
    private final AppSettingService appSettingService;

    @GetMapping("/factions")
    public Result<List<String>> factions() {
        return Result.success(appSettingService.getFactions());
    }
}
