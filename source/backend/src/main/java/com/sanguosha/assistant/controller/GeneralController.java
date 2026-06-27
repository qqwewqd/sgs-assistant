package com.sanguosha.assistant.controller;

import com.sanguosha.assistant.service.GeneralService;
import com.sanguosha.assistant.vo.Result;
import com.sanguosha.assistant.vo.RoomView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generals")
@RequiredArgsConstructor
public class GeneralController {
    private final GeneralService generalService;

    @GetMapping("/lords")
    public Result<List<RoomView.GeneralCard>> lordGenerals(@RequestParam(required = false) String keyword) {
        return Result.success(generalService.listLordCards(keyword));
    }
}
