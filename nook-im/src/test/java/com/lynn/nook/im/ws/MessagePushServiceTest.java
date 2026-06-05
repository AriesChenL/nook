package com.lynn.nook.im.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynn.nook.im.dto.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessagePushServiceTest {

    private WebSocketSessionManager sessionManager;
    private MessagePushService pushService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sessionManager = mock(WebSocketSessionManager.class);
        objectMapper = new ObjectMapper();
        pushService = new MessagePushService(sessionManager, objectMapper);
    }

    @Test
    void pushNewMessage_returnsZeroForEmptyMembers() {
        int n = pushService.pushNewMessage(List.of(), MessageVO.builder().id("m1").build());
        assertThat(n).isZero();
        verifyNoInteractions(sessionManager);
    }

    @Test
    void pushRecall_serializesRecallEnvelope() {
        when(sessionManager.sendToUsers(eq(List.of(1L, 2L)), anyString())).thenReturn(2);

        // 帧内用 public_id；路由仍按数字成员 id
        int sent = pushService.pushRecall(List.of(1L, 2L), "c42", "m99");

        assertThat(sent).isEqualTo(2);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(sessionManager).sendToUsers(eq(List.of(1L, 2L)), cap.capture());
        String payload = cap.getValue();
        assertThat(payload).contains("\"type\":\"recall\"");
        assertThat(payload).contains("\"conversationId\":\"c42\"");
        assertThat(payload).contains("\"messageId\":\"m99\"");
    }

    @Test
    void pushRecall_returnsZeroForEmptyMembers() {
        int n = pushService.pushRecall(List.of(), "c1", "m1");
        assertThat(n).isZero();
        verifyNoInteractions(sessionManager);
    }

    @Test
    void pushPresence_serializesPresenceEnvelope() {
        when(sessionManager.sendToUsers(eq(List.of(1L, 2L)), anyString())).thenReturn(2);

        int sent = pushService.pushPresence(List.of(1L, 2L), "u7", true);

        assertThat(sent).isEqualTo(2);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(sessionManager).sendToUsers(eq(List.of(1L, 2L)), cap.capture());
        String payload = cap.getValue();
        assertThat(payload).contains("\"type\":\"presence\"");
        assertThat(payload).contains("\"userId\":\"u7\"");
        assertThat(payload).contains("\"online\":true");
    }

    @Test
    void pushPresence_returnsZeroForEmptyFriends() {
        int n = pushService.pushPresence(List.of(), "u7", true);
        assertThat(n).isZero();
        verifyNoInteractions(sessionManager);
    }

    @Test
    void pushNewMessage_serializesEnvelopeAndDelegatesToManager() {
        MessageVO msg = MessageVO.builder()
                .id("m42").conversationId("c7").senderId("u100")
                .contentType((short) 1).content("hi")
                .build();
        when(sessionManager.sendToUsers(eq(List.of(100L, 200L)), anyString())).thenReturn(3);

        int sent = pushService.pushNewMessage(List.of(100L, 200L), msg);

        assertThat(sent).isEqualTo(3);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(sessionManager).sendToUsers(eq(List.of(100L, 200L)), cap.capture());
        String payload = cap.getValue();
        assertThat(payload).contains("\"type\":\"message\"");
        assertThat(payload).contains("\"id\":\"m42\"");
        assertThat(payload).contains("\"content\":\"hi\"");
    }
}
