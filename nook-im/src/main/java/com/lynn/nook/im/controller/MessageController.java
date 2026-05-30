package com.lynn.nook.im.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.dto.ReadStatusVO;
import com.lynn.nook.im.dto.SendMessageRequest;
import com.lynn.nook.im.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/im/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public Result<MessageVO> send(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                  @Valid @RequestBody SendMessageRequest req) {
        return Result.ok(messageService.send(userId, req));
    }

    @GetMapping
    public Result<List<MessageVO>> history(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                           @RequestParam("conversationId") Long conversationId,
                                           @RequestParam(value = "beforeId", required = false) Long beforeId,
                                           @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return Result.ok(messageService.history(userId, conversationId, beforeId, limit));
    }

    @PostMapping("/{id}/recall")
    public Result<Void> recall(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                               @PathVariable("id") Long messageId) {
        messageService.recall(userId, messageId);
        return Result.ok();
    }

    @GetMapping("/{id}/read-status")
    public Result<ReadStatusVO> readStatus(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                           @PathVariable("id") Long messageId) {
        return Result.ok(messageService.readStatus(userId, messageId));
    }
}
