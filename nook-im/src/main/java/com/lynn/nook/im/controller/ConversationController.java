package com.lynn.nook.im.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.dto.ConversationVO;
import com.lynn.nook.im.dto.CreateDirectRequest;
import com.lynn.nook.im.dto.ReadCursorRequest;
import com.lynn.nook.im.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/im/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public Result<List<ConversationVO>> mine(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(conversationService.listMine(userId));
    }

    @PostMapping("/direct")
    public Result<ConversationVO> direct(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                         @Valid @RequestBody CreateDirectRequest req) {
        return Result.ok(conversationService.getOrCreateDirect(userId, req.getPeerUserId()));
    }

    @GetMapping("/{id}")
    public Result<ConversationVO> get(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                      @PathVariable Long id) {
        return Result.ok(conversationService.get(userId, id));
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                             @PathVariable Long id,
                             @Valid @RequestBody ReadCursorRequest req) {
        conversationService.updateReadCursor(userId, id, req.getLastReadMsgId());
        return Result.ok();
    }
}
