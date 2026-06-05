package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.dto.ConversationVO;
import com.lynn.nook.im.dto.CreateGroupRequest;
import com.lynn.nook.im.dto.UpdateGroupRequest;
import com.lynn.nook.im.entity.Conversation;
import com.lynn.nook.im.entity.ConversationMember;
import com.lynn.nook.im.mapper.ConversationMapper;
import com.lynn.nook.im.mapper.ConversationMemberMapper;
import com.lynn.nook.im.mapper.MessageMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 群聊管理逻辑单测。重点：角色权限校验、转让、退群、成员增删。 */
class ConversationServiceTest {

    private ConversationMapper conversationMapper;
    private ConversationMemberMapper memberMapper;
    private MessageMapper messageMapper;
    private SystemMessageService systemMessageService;
    private IdResolver idResolver;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        memberMapper = mock(ConversationMemberMapper.class);
        messageMapper = mock(MessageMapper.class);
        systemMessageService = mock(SystemMessageService.class);
        idResolver = mock(IdResolver.class);
        service = new ConversationService(conversationMapper, memberMapper, messageMapper,
                systemMessageService, idResolver);
        // buildVO 会查成员列表，统一返回空，避免 NPE（不影响被测断言）
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        // buildVO 会调 idResolver 做脱敏，统一返回空 map（不影响群聊权限/角色断言）
        lenient().when(idResolver.userPublicIds(any())).thenReturn(Map.of());
    }

    private ConversationMember member(Long convId, Long userId, short role) {
        ConversationMember m = new ConversationMember();
        m.setId(userId); // 用 userId 当主键便于断言 deleteById
        m.setConversationId(convId);
        m.setUserId(userId);
        m.setRole(role);
        m.setLastReadMsgId(0L);
        return m;
    }

    private Conversation group(Long id, Long ownerId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setType(Conversation.TYPE_GROUP);
        c.setOwnerId(ownerId);
        return c;
    }

    // -------- createGroup --------

    @Test
    void createGroup_addsOwnerAndDedupedMembers() {
        CreateGroupRequest req = new CreateGroupRequest();
        req.setName("小组");
        // 含重复 3L 和创建者 1L；memberIds 已由 controller 解析为数字，单独以参数传入
        List<Long> memberIds = List.of(2L, 3L, 3L, 1L);

        doAnswer(inv -> { ((Conversation) inv.getArgument(0)).setId(100L); return 1; })
                .when(conversationMapper).insert(any(Conversation.class));

        ConversationVO vo = service.createGroup(1L, req, memberIds);

        // id 输出会话 public_id（建群时 Java 层生成的 UUID）
        assertThat(vo.getId()).isNotBlank();
        ArgumentCaptor<ConversationMember> cap = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper, times(3)).insert(cap.capture()); // owner + 2 + 3
        List<ConversationMember> added = cap.getAllValues();
        assertThat(added.get(0).getUserId()).isEqualTo(1L);
        assertThat(added.get(0).getRole()).isEqualTo(ConversationMember.ROLE_OWNER);
        assertThat(added.stream().map(ConversationMember::getUserId).toList())
                .containsExactly(1L, 2L, 3L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(systemMessageService).post(eq(100L), eq(1L), bodyCap.capture(), any());
        assertThat(bodyCap.getValue()).containsEntry("action", "group_created");
    }

    // -------- addMembers --------

    @Test
    void addMembers_rejectsNonGroup() {
        Conversation direct = new Conversation();
        direct.setId(5L);
        direct.setType(Conversation.TYPE_DIRECT);
        when(conversationMapper.selectOneById(5L)).thenReturn(direct);

        assertThatThrownBy(() -> service.addMembers(1L, 5L, List.of(9L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CONVERSATION_NOT_GROUP.getCode());
    }

    @Test
    void addMembers_rejectsWhenOperatorIsPlainMember() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 2L, ConversationMember.ROLE_MEMBER));

        assertThatThrownBy(() -> service.addMembers(2L, 5L, List.of(9L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_PERMISSION_DENIED.getCode());
        verify(memberMapper, never()).insert(any());
        verifyNoInteractions(systemMessageService);
    }

    @Test
    void addMembers_skipsExistingAndAddsNew() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER));
        // listMemberIds：已有 1L、2L
        when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(member(5L, 1L, ConversationMember.ROLE_OWNER),
                                    member(5L, 2L, ConversationMember.ROLE_MEMBER)));

        service.addMembers(1L, 5L, List.of(2L, 7L)); // 2L 已存在，仅新增 7L

        ArgumentCaptor<ConversationMember> cap = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(7L);
        assertThat(cap.getValue().getRole()).isEqualTo(ConversationMember.ROLE_MEMBER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(systemMessageService).post(eq(5L), eq(1L), bodyCap.capture(), any());
        assertThat(bodyCap.getValue())
                .containsEntry("action", "members_added")
                .containsEntry("targetIds", List.of(7L));
    }

    // -------- removeMember --------

    @Test
    void removeMember_rejectsRemovingSelf() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER));

        assertThatThrownBy(() -> service.removeMember(1L, 5L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_PERMISSION_DENIED.getCode());
        verify(memberMapper, never()).deleteById(anyLong());
    }

    @Test
    void removeMember_rejectsRemovingOwner() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        // 第一次 selectOneByQuery 返回操作者(群主)，第二次返回目标(也是群主——构造不可能，这里测保护)
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER),
                            member(5L, 9L, ConversationMember.ROLE_OWNER));

        assertThatThrownBy(() -> service.removeMember(1L, 5L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_PERMISSION_DENIED.getCode());
        verify(memberMapper, never()).deleteById(anyLong());
    }

    @Test
    void removeMember_adminCannotRemoveAnotherAdmin() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 2L, ConversationMember.ROLE_ADMIN),
                            member(5L, 3L, ConversationMember.ROLE_ADMIN));

        assertThatThrownBy(() -> service.removeMember(2L, 5L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_PERMISSION_DENIED.getCode());
        verify(memberMapper, never()).deleteById(anyLong());
    }

    @Test
    void removeMember_ownerRemovesMember() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER),
                            member(5L, 9L, ConversationMember.ROLE_MEMBER));

        service.removeMember(1L, 5L, 9L);

        verify(memberMapper).deleteById(9L); // member() 用 userId 当主键
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(systemMessageService).post(eq(5L), eq(1L), bodyCap.capture(), any());
        assertThat(bodyCap.getValue())
                .containsEntry("action", "member_removed")
                .containsEntry("targetId", 9L);
    }

    @Test
    void removeMember_rejectsWhenTargetNotMember() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER), null);

        assertThatThrownBy(() -> service.removeMember(1L, 5L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_MEMBER_NOT_FOUND.getCode());
    }

    // -------- updateGroup --------

    @Test
    void updateGroup_updatesNameAndAvatar() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER));
        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setName("新群名");
        req.setAvatarUrl("http://x/a.png");

        service.updateGroup(1L, 5L, req);

        ArgumentCaptor<Conversation> cap = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).update(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("新群名");
        assertThat(cap.getValue().getAvatarUrl()).isEqualTo("http://x/a.png");
    }

    // -------- leaveGroup --------

    @Test
    void leaveGroup_ownerMustTransferFirst() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER));

        assertThatThrownBy(() -> service.leaveGroup(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_OWNER_CANNOT_LEAVE.getCode());
        verify(memberMapper, never()).deleteById(anyLong());
    }

    @Test
    void leaveGroup_memberLeaves() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 8L, ConversationMember.ROLE_MEMBER));

        service.leaveGroup(8L, 5L);

        verify(memberMapper).deleteById(8L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(systemMessageService).post(eq(5L), eq(8L), bodyCap.capture(), any());
        assertThat(bodyCap.getValue()).containsEntry("action", "member_left");
    }

    // -------- transferOwner --------

    @Test
    void transferOwner_rejectsNonOwner() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 2L, ConversationMember.ROLE_ADMIN));

        assertThatThrownBy(() -> service.transferOwner(2L, 5L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_PERMISSION_DENIED.getCode());
    }

    @Test
    void transferOwner_swapsRolesAndUpdatesConversation() {
        Conversation c = group(5L, 1L);
        when(conversationMapper.selectOneById(5L)).thenReturn(c);
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER),
                            member(5L, 9L, ConversationMember.ROLE_MEMBER));

        service.transferOwner(1L, 5L, 9L);

        ArgumentCaptor<ConversationMember> cap = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper, times(2)).update(cap.capture());
        // 第一次更新原群主→普通，第二次更新目标→群主
        assertThat(cap.getAllValues().get(0).getRole()).isEqualTo(ConversationMember.ROLE_MEMBER);
        assertThat(cap.getAllValues().get(1).getRole()).isEqualTo(ConversationMember.ROLE_OWNER);
        ArgumentCaptor<Conversation> cc = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).update(cc.capture());
        assertThat(cc.getValue().getOwnerId()).isEqualTo(9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(systemMessageService).post(eq(5L), eq(1L), bodyCap.capture(), any());
        assertThat(bodyCap.getValue())
                .containsEntry("action", "owner_transferred")
                .containsEntry("targetId", 9L);
    }

    // -------- setMemberRole --------

    @Test
    void setMemberRole_rejectsInvalidRole() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER));

        assertThatThrownBy(() -> service.setMemberRole(1L, 5L, 9L, (short) 99))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_ROLE_INVALID.getCode());
    }

    @Test
    void setMemberRole_promotesMemberToAdmin() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER),
                            member(5L, 9L, ConversationMember.ROLE_MEMBER));

        service.setMemberRole(1L, 5L, 9L, ConversationMember.ROLE_ADMIN);

        ArgumentCaptor<ConversationMember> cap = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper).update(cap.capture());
        assertThat(cap.getValue().getRole()).isEqualTo(ConversationMember.ROLE_ADMIN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(systemMessageService).post(eq(5L), eq(1L), bodyCap.capture(), any());
        assertThat(bodyCap.getValue()).containsEntry("action", "role_changed");
    }

    // -------- readersOf --------

    @Test
    void readersOf_mapsMemberUserIds() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                member(5L, 2L, ConversationMember.ROLE_MEMBER),
                member(5L, 3L, ConversationMember.ROLE_MEMBER)));

        List<Long> readers = service.readersOf(5L, 50L, 1L);

        assertThat(readers).containsExactly(2L, 3L);
        verify(memberMapper).selectListByQuery(any(QueryWrapper.class));
    }

    @Test
    void setMemberRole_cannotChangeOwner() {
        when(conversationMapper.selectOneById(5L)).thenReturn(group(5L, 1L));
        when(memberMapper.selectOneByQuery(any(QueryWrapper.class)))
                .thenReturn(member(5L, 1L, ConversationMember.ROLE_OWNER),
                            member(5L, 1L, ConversationMember.ROLE_OWNER));

        assertThatThrownBy(() -> service.setMemberRole(1L, 5L, 1L, ConversationMember.ROLE_ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.GROUP_PERMISSION_DENIED.getCode());
    }
}
