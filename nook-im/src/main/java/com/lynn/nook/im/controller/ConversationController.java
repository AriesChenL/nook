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

    // ==================== 群聊 ====================

    @PostMapping("/group")
    public Result<ConversationVO> createGroup(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                              @Valid @RequestBody CreateGroupRequest req) {
        return Result.ok(conversationService.createGroup(userId, req));
    }

    @PutMapping("/{id}")
    public Result<ConversationVO> updateGroup(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody UpdateGroupRequest req) {
        return Result.ok(conversationService.updateGroup(userId, id, req));
    }

    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> members(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                          @PathVariable Long id) {
        return Result.ok(memberQueryService.listMembers(userId, id));
    }

    @PostMapping("/{id}/members")
    public Result<ConversationVO> addMembers(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                             @PathVariable Long id,
                                             @Valid @RequestBody AddMembersRequest req) {
        return Result.ok(conversationService.addMembers(userId, id, req.getMemberIds()));
    }

    @DeleteMapping("/{id}/members/{targetUserId}")
    public Result<Void> removeMember(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                     @PathVariable Long id,
                                     @PathVariable Long targetUserId) {
        conversationService.removeMember(userId, id, targetUserId);
        return Result.ok();
    }

    @PutMapping("/{id}/members/{targetUserId}/role")
    public Result<ConversationVO> setMemberRole(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                                @PathVariable Long id,
                                                @PathVariable Long targetUserId,
                                                @Valid @RequestBody SetMemberRoleRequest req) {
        return Result.ok(conversationService.setMemberRole(userId, id, targetUserId, req.getRole()));
    }

    @PostMapping("/{id}/leave")
    public Result<Void> leave(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                              @PathVariable Long id) {
        conversationService.leaveGroup(userId, id);
        return Result.ok();
    }

    @PostMapping("/{id}/owner")
    public Result<ConversationVO> transferOwner(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                                @PathVariable Long id,
                                                @Valid @RequestBody TransferOwnerRequest req) {
        return Result.ok(conversationService.transferOwner(userId, id, req.getNewOwnerId()));
    }
}
