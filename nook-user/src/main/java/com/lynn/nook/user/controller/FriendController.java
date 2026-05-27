package com.lynn.nook.user.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.user.dto.CreateFriendRequest;
import com.lynn.nook.user.dto.FriendRequestVO;
import com.lynn.nook.user.dto.FriendVO;
import com.lynn.nook.user.dto.UpdateFriendRemarkRequest;
import com.lynn.nook.user.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    public Result<List<FriendVO>> list(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(friendService.listFriends(userId));
    }

    @PostMapping("/requests")
    public Result<FriendRequestVO> send(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                        @Valid @RequestBody CreateFriendRequest req) {
        return Result.ok(friendService.sendRequest(userId, req));
    }

    @GetMapping("/requests/incoming")
    public Result<List<FriendRequestVO>> incoming(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(friendService.incoming(userId));
    }

    @GetMapping("/requests/outgoing")
    public Result<List<FriendRequestVO>> outgoing(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(friendService.outgoing(userId));
    }

    @PostMapping("/requests/{id}/accept")
    public Result<Void> accept(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                               @PathVariable("id") Long requestId) {
        friendService.accept(userId, requestId);
        return Result.ok();
    }

    @PostMapping("/requests/{id}/reject")
    public Result<Void> reject(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                               @PathVariable("id") Long requestId) {
        friendService.reject(userId, requestId);
        return Result.ok();
    }

    @DeleteMapping("/{friendUserId}")
    public Result<Void> remove(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                               @PathVariable Long friendUserId) {
        friendService.removeFriend(userId, friendUserId);
        return Result.ok();
    }

    @PutMapping("/{friendUserId}/remark")
    public Result<Void> updateRemark(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                     @PathVariable Long friendUserId,
                                     @Valid @RequestBody UpdateFriendRemarkRequest req) {
        friendService.updateRemark(userId, friendUserId, req.getRemark());
        return Result.ok();
    }
}
