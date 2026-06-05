package com.lynn.nook.im.service;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.im.dto.MemberVO;
import com.lynn.nook.im.dto.UserBriefVO;
import com.lynn.nook.im.entity.ConversationMember;
import com.lynn.nook.im.mapper.ConversationMemberMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberQueryServiceTest {

    private ConversationService conversationService;
    private ConversationMemberMapper memberMapper;
    private IdResolver idResolver;
    private MemberQueryService service;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        memberMapper = mock(ConversationMemberMapper.class);
        idResolver = mock(IdResolver.class);
        service = new MemberQueryService(conversationService, memberMapper, idResolver);
    }

    private ConversationMember member(Long userId, short role) {
        ConversationMember m = new ConversationMember();
        m.setConversationId(5L);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    @Test
    void listMembers_mergesProfiles() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                member(1L, ConversationMember.ROLE_OWNER),
                member(2L, ConversationMember.ROLE_MEMBER)));
        // 数字 userId → UserBriefVO(id=public_id, 昵称/头像)
        when(idResolver.userProfilesByNumericId(any())).thenReturn(Map.of(
                1L, new UserBriefVO("pub-1", "alice", "Alice", "a.png"),
                2L, new UserBriefVO("pub-2", "bob", "Bob", "b.png")));

        List<MemberVO> result = service.listMembers(1L, 5L);

        verify(conversationService).requireMember(5L, 1L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo("pub-1");
        assertThat(result.get(0).getNickname()).isEqualTo("Alice");
        assertThat(result.get(0).getRole()).isEqualTo(ConversationMember.ROLE_OWNER);
        assertThat(result.get(1).getUserId()).isEqualTo("pub-2");
        assertThat(result.get(1).getNickname()).isEqualTo("Bob");
    }

    @Test
    void listMembers_degradesWhenClientThrows() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(member(1L, ConversationMember.ROLE_OWNER)));
        // nook-user 不可用 → 空 map（IdResolver 内部已吞异常）
        when(idResolver.userProfilesByNumericId(any())).thenReturn(Map.of());

        List<MemberVO> result = service.listMembers(1L, 5L);

        // 资料取不到但成员关系仍返回；脱敏前提缺失时 userId/昵称为 null
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isNull();
        assertThat(result.get(0).getNickname()).isNull();
        assertThat(result.get(0).getRole()).isEqualTo(ConversationMember.ROLE_OWNER);
    }

    @Test
    void listMembers_handlesMissingProfileForSomeMembers() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                member(1L, ConversationMember.ROLE_OWNER),
                member(2L, ConversationMember.ROLE_MEMBER)));
        // 只回来 1L 的资料，2L 缺失
        when(idResolver.userProfilesByNumericId(any())).thenReturn(Map.of(
                1L, new UserBriefVO("pub-1", "alice", "Alice", "a.png")));

        List<MemberVO> result = service.listMembers(1L, 5L);

        assertThat(result.get(0).getUserId()).isEqualTo("pub-1");
        assertThat(result.get(0).getNickname()).isEqualTo("Alice");
        assertThat(result.get(1).getUserId()).isNull();
        assertThat(result.get(1).getNickname()).isNull();
    }

    @Test
    void listMembers_enforcesMembership() {
        doThrow(new BusinessException(ResultCode.NOT_CONVERSATION_MEMBER))
                .when(conversationService).requireMember(5L, 9L);

        assertThatThrownBy(() -> service.listMembers(9L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_CONVERSATION_MEMBER.getCode());
        verifyNoInteractions(memberMapper, idResolver);
    }

    @Test
    void listMembers_emptyWhenNoRows() {
        when(memberMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        List<MemberVO> result = service.listMembers(1L, 5L);

        assertThat(result).isEmpty();
        verifyNoInteractions(idResolver);
    }
}
