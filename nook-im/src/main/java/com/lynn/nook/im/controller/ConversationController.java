package com.lynn.nook.im.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.dto.AddMembersRequest;
import com.lynn.nook.im.dto.ConversationVO;
import com.lynn.nook.im.dto.CreateDirectRequest;
import com.lynn.nook.im.dto.CreateGroupRequest;
import com.lynn.nook.im.dto.MemberVO;
import com.lynn.nook.im.dto.ReadCursorRequest;
import com.lynn.nook.im.dto.SetMemberRoleRequest;
import com.lynn.nook.im.dto.TransferOwnerRequest;
import com.lynn.nook.im.dto.UpdateGroupRequest;
import com.lynn.nook.im.service.ConversationService;
import com.lynn.nook.im.service.IdResolver;
import com.lynn.nook.im.service.MemberQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/im/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final MemberQueryService memberQueryService;
    private final IdResolver idResolver;

    @GetMapping
    public Result<List<ConversationVO>> mine(@RequestHeader(RequestHeaders.USER_ID) Long userId) {
        return Result.ok(conversationService.listMine(userId));
    }

    @PostMapping("/direct")
    public Result<ConversationVO> direct(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                         @Valid @RequestBody CreateDirectRequest req) {
        Long peerId = idResolver.userId(req.getPeerUserId());
        return Result.ok(conversationService.getOrCreateDirect(userId, peerId));
    }

    @GetMapping("/{id}")
    public Result<ConversationVO> get(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                      @PathVariable String id) {
        return Result.ok(conversationService.get(userId, idResolver.conversationId(id)));
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                             @PathVariable String id,
                             @Valid @RequestBody ReadCursorRequest req) {
        Long convId = idResolver.conversationId(id);
        Long lastReadMsgId = idResolver.messageId(req.getLastReadMsgId());
        conversationService.updateReadCursor(userId, convId, lastReadMsgId);
        return Result.ok();
    }

    // ==================== 群聊 ====================

    @PostMapping("/group")
    public Result<ConversationVO> createGroup(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                              @Valid @RequestBody CreateGroupRequest req) {
        List<Long> memberIds = resolveUserPublicIds(req.getMemberIds());
        return Result.ok(conversationService.createGroup(userId, req, memberIds));
    }

    @PutMapping("/{id}")
    public Result<ConversationVO> updateGroup(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                              @PathVariable String id,
                                              @Valid @RequestBody UpdateGroupRequest req) {
        return Result.ok(conversationService.updateGroup(userId, idResolver.conversationId(id), req));
    }

    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> members(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                          @PathVariable String id) {
        return Result.ok(memberQueryService.listMembers(userId, idResolver.conversationId(id)));
    }

    @PostMapping("/{id}/members")
    public Result<ConversationVO> addMembers(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                             @PathVariable String id,
                                             @Valid @RequestBody AddMembersRequest req) {
        Long convId = idResolver.conversationId(id);
        List<Long> memberIds = resolveUserPublicIds(req.getMemberIds());
        return Result.ok(conversationService.addMembers(userId, convId, memberIds));
    }

    @DeleteMapping("/{id}/members/{targetUserId}")
    public Result<Void> removeMember(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                     @PathVariable String id,
                                     @PathVariable String targetUserId) {
        conversationService.removeMember(userId, idResolver.conversationId(id), idResolver.userId(targetUserId));
        return Result.ok();
    }

    @PutMapping("/{id}/members/{targetUserId}/role")
    public Result<ConversationVO> setMemberRole(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                                @PathVariable String id,
                                                @PathVariable String targetUserId,
                                                @Valid @RequestBody SetMemberRoleRequest req) {
        Long convId = idResolver.conversationId(id);
        Long targetId = idResolver.userId(targetUserId);
        return Result.ok(conversationService.setMemberRole(userId, convId, targetId, req.getRole()));
    }

    @PostMapping("/{id}/leave")
    public Result<Void> leave(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                              @PathVariable String id) {
        conversationService.leaveGroup(userId, idResolver.conversationId(id));
        return Result.ok();
    }

    @PostMapping("/{id}/owner")
    public Result<ConversationVO> transferOwner(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                                @PathVariable String id,
                                                @Valid @RequestBody TransferOwnerRequest req) {
        Long convId = idResolver.conversationId(id);
        Long newOwnerId = idResolver.userId(req.getNewOwnerId());
        return Result.ok(conversationService.transferOwner(userId, convId, newOwnerId));
    }

    /** 把一批 user public_id 解析为数字 id，保持入参顺序；任一解析不到抛 USER_NOT_FOUND。 */
    private List<Long> resolveUserPublicIds(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) return List.of();
        var map = idResolver.resolveUserIds(publicIds);
        return publicIds.stream().map(pid -> {
            Long id = map.get(pid);
            if (id == null) {
                throw new com.lynn.nook.common.exception.BusinessException(
                        com.lynn.nook.common.result.ResultCode.USER_NOT_FOUND);
            }
            return id;
        }).toList();
    }
}
