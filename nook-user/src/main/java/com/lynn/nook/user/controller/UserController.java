package com.lynn.nook.user.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.user.dto.UpdateProfileRequest;
import com.lynn.nook.user.dto.UserVO;
import com.lynn.nook.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(userService.getSelf(userId));
    }

    @PutMapping("/me")
    public Result<UserVO> updateMe(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                   @Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(userService.updateSelf(userId, req));
    }

    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam("q") String q,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.ok(userService.search(q, limit));
    }

    /** 批量取公开资料（脱敏）。`?ids=1,2,3`。供内部服务聚合成员资料用。 */
    @GetMapping("/batch")
    public Result<List<UserVO>> batch(@RequestParam("ids") List<Long> ids) {
        return Result.ok(userService.listByIds(ids));
    }
}
