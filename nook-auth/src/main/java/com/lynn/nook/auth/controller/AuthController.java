package com.lynn.nook.auth.controller;

import com.lynn.nook.auth.dto.LoginRequest;
import com.lynn.nook.auth.dto.LoginResponse;
import com.lynn.nook.auth.dto.RegisterRequest;
import com.lynn.nook.auth.service.AuthService;
import com.lynn.nook.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Long> register(@RequestBody @Valid RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            authService.logout(auth.substring(BEARER_PREFIX.length()));
        }
        return Result.ok();
    }
}
