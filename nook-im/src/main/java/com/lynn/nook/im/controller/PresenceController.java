package com.lynn.nook.im.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.service.PresenceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线状态查询：WS 只在状态跳变时推送，无法告诉刚连上的客户端「此刻谁在线」。
 * 前端登录/进入页面时调用本接口拿一次在线快照，之后再靠 WS presence 帧增量更新。
 */
@RestController
@RequestMapping("/im/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceQueryService presenceQueryService;

    /** 返回当前用户「在线的好友」的 user public_id 列表，作为前端在线状态初始快照。 */
    @GetMapping("/online")
    public Result<List<String>> onlineFriends(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(presenceQueryService.onlineFriendPublicIds(userId));
    }
}
