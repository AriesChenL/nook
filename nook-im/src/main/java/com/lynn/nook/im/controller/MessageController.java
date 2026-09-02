package com.lynn.nook.im.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.dto.MessageVO;
import com.lynn.nook.im.dto.ReadStatusVO;
import com.lynn.nook.im.dto.SendMessageRequest;
import com.lynn.nook.im.service.IdResolver;
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
    private final IdResolver idResolver;

    @PostMapping
    public Result<MessageVO> send(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                  @Valid @RequestBody SendMessageRequest req) {
        Long conversationId = idResolver.conversationId(req.conversationId());
        return Result.ok(messageService.send(userId, conversationId, req));
    }

    @GetMapping
    public Result<List<MessageVO>> history(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                           @RequestParam("conversationId") String conversationId,
                                           @RequestParam(value = "beforeId", required = false) String beforeId,
                                           @RequestParam(value = "limit", defaultValue = "30") int limit) {
        Long convId = idResolver.conversationId(conversationId);
        Long beforeNumId = (beforeId == null || beforeId.isBlank()) ? null : idResolver.messageId(beforeId);
        return Result.ok(messageService.history(userId, convId, beforeNumId, limit));
    }

    @PostMapping("/{id}/recall")
    public Result<Void> recall(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                               @PathVariable("id") String messageId) {
        messageService.recall(userId, idResolver.messageId(messageId));
        return Result.ok();
    }

    @GetMapping("/{id}/read-status")
    public Result<ReadStatusVO> readStatus(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                           @PathVariable("id") String messageId) {
        return Result.ok(messageService.readStatus(userId, idResolver.messageId(messageId)));
    }
}
