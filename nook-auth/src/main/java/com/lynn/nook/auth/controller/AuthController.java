package com.lynn.nook.auth.controller;

import com.lynn.nook.auth.dto.ChangePasswordRequest;
import com.lynn.nook.auth.dto.LoginRequest;
import com.lynn.nook.auth.dto.LoginResponse;
import com.lynn.nook.auth.dto.MeResponse;
import com.lynn.nook.auth.dto.RegisterRequest;
import com.lynn.nook.auth.service.AuthService;
import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/me")
    public Result<MeResponse> me(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(authService.me(userId));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                       @RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody @Valid ChangePasswordRequest req) {
        String token = (auth != null && auth.startsWith(BEARER_PREFIX))
                ? auth.substring(BEARER_PREFIX.length()) : null;
        authService.changePassword(userId, token, req);
        return Result.ok();
    }
}
