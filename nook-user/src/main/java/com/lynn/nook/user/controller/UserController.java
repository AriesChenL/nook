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
import java.util.Map;

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

    /** 单个用户公开资料。路径参数为用户 public_id（脱敏）。 */
    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable String id) {
        return Result.ok(userService.getByPublicId(id));
    }

    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam("q") String q,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.ok(userService.search(q, limit));
    }

    /**
     * 批量取公开资料（脱敏）。`?ids=1,2,3`。供内部服务聚合成员资料用。
     * 入参 ids 仍是数字（im 内部持有数字 sender_id）；返回 UserVO.id 为 public_id 字符串。
     */
    @GetMapping("/batch")
    public Result<List<UserVO>> batch(@RequestParam("ids") List<Long> ids) {
        return Result.ok(userService.listByIds(ids));
    }

    /**
     * 批量取公开资料（脱敏），入参为用户 public_id。供前端聚合会话/成员/系统消息里的用户名。
     * `?publicIds=uuid1,uuid2`。返回 UserVO.id 为 public_id；解析不到的忽略。
     */
    @GetMapping("/profiles")
    public Result<List<UserVO>> profiles(@RequestParam("publicIds") List<String> publicIds) {
        return Result.ok(userService.listByPublicIds(publicIds));
    }

    /**
     * 批量把 user public_id 解析成数字主键 id，供 nook-im 把前端传来的 user public_id 换回数字。
     * `?publicIds=uuid1,uuid2`。返回 Map：key=public_id，value=数字 id；解析不到的 public_id 不出现在结果里。
     */
    @GetMapping("/resolve")
    public Result<Map<String, Long>> resolve(@RequestParam("publicIds") List<String> publicIds) {
        return Result.ok(userService.resolveIds(publicIds));
    }
}
