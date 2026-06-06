package com.lynn.nook.ai.controller;

import com.lynn.nook.ai.dto.AgentVO;
import com.lynn.nook.ai.dto.ChatMessageVO;
import com.lynn.nook.ai.dto.ChatReplyVO;
import com.lynn.nook.ai.dto.ChatRequest;
import com.lynn.nook.ai.dto.ChatSessionVO;
import com.lynn.nook.ai.dto.CreateAgentRequest;
import com.lynn.nook.ai.dto.CreateSessionRequest;
import com.lynn.nook.ai.dto.UpdateAgentRequest;
import com.lynn.nook.ai.service.AiAgentService;
import com.lynn.nook.ai.service.AiChatService;
import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentService agentService;
    private final AiChatService chatService;

    @PostMapping("/agents")
    public Result<AgentVO> create(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                  @Valid @RequestBody CreateAgentRequest req) {
        return Result.ok(agentService.create(userId, req));
    }

    @GetMapping("/agents")
    public Result<List<AgentVO>> list(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(agentService.listMine(userId));
    }

    @GetMapping("/agents/{id}")
    public Result<AgentVO> get(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                               @PathVariable("id") String agentPublicId) {
        return Result.ok(agentService.get(userId, agentService.resolveAgentId(agentPublicId)));
    }

    @PutMapping("/agents/{id}")
    public Result<AgentVO> update(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                  @PathVariable("id") String agentPublicId,
                                  @Valid @RequestBody UpdateAgentRequest req) {
        return Result.ok(agentService.update(userId, agentService.resolveAgentId(agentPublicId), req));
    }

    @DeleteMapping("/agents/{id}")
    public Result<Void> delete(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                               @PathVariable("id") String agentPublicId) {
        agentService.delete(userId, agentService.resolveAgentId(agentPublicId));
        return Result.ok();
    }

    @PostMapping("/agents/{id}/sessions")
    public Result<ChatSessionVO> createSession(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                               @PathVariable("id") String agentPublicId,
                                               @RequestBody(required = false) CreateSessionRequest req) {
        return Result.ok(agentService.createSession(userId, agentService.resolveAgentId(agentPublicId), req));
    }

    @GetMapping("/agents/{id}/sessions")
    public Result<List<ChatSessionVO>> sessions(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                                @PathVariable("id") String agentPublicId) {
        return Result.ok(agentService.listSessions(userId, agentService.resolveAgentId(agentPublicId)));
    }

    @PostMapping("/agents/{id}/chat")
    public Result<ChatReplyVO> chat(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                    @PathVariable("id") String agentPublicId,
                                    @Valid @RequestBody ChatRequest req) {
        return Result.ok(chatService.chat(userId, agentService.resolveAgentId(agentPublicId), req));
    }

    /** 该 Agent 当前会话的可见历史消息（按时间顺序），用于打开 Agent 时加载之前的对话。 */
    @GetMapping("/agents/{id}/messages")
    public Result<List<ChatMessageVO>> messages(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                                @PathVariable("id") String agentPublicId) {
        return Result.ok(chatService.listMessages(userId, agentService.resolveAgentId(agentPublicId)));
    }
}
