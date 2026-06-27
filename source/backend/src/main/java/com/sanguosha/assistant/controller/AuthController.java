package com.sanguosha.assistant.controller;

import com.sanguosha.assistant.service.UserService;
import com.sanguosha.assistant.util.SecurityUtil;
import com.sanguosha.assistant.vo.AuthVO;
import com.sanguosha.assistant.vo.BootstrapAdminRequest;
import com.sanguosha.assistant.vo.BootstrapStatusVO;
import com.sanguosha.assistant.vo.LoginRequest;
import com.sanguosha.assistant.vo.Result;
import com.sanguosha.assistant.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/login")
    public Result<AuthVO> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/bootstrap-admin")
    public Result<UserVO> bootstrapAdmin(@RequestBody @Valid BootstrapAdminRequest request) {
        return Result.success(userService.bootstrapAdmin(request));
    }

    @GetMapping("/bootstrap-status")
    public Result<BootstrapStatusVO> bootstrapStatus() {
        return Result.success(userService.bootstrapStatus());
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.current(SecurityUtil.currentUser()));
    }
}
