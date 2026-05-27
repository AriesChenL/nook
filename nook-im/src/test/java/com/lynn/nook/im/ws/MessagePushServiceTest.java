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
        int n = pushService.pushNewMessage(List.of(), MessageVO.builder().id(1L).build());
        assertThat(n).isZero();
        verifyNoInteractions(sessionManager);
    }

    @Test
    void pushRecall_serializesRecallEnvelope() {
        when(sessionManager.sendToUsers(eq(List.of(1L, 2L)), anyString())).thenReturn(2);

        int sent = pushService.pushRecall(List.of(1L, 2L), 42L, 99L);

        assertThat(sent).isEqualTo(2);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(sessionManager).sendToUsers(eq(List.of(1L, 2L)), cap.capture());
        String payload = cap.getValue();
        assertThat(payload).contains("\"type\":\"recall\"");
        assertThat(payload).contains("\"conversationId\":42");
        assertThat(payload).contains("\"messageId\":99");
    }

    @Test
    void pushRecall_returnsZeroForEmptyMembers() {
        int n = pushService.pushRecall(List.of(), 1L, 1L);
        assertThat(n).isZero();
        verifyNoInteractions(sessionManager);
    }

    @Test
    void pushNewMessage_serializesEnvelopeAndDelegatesToManager() {
        MessageVO msg = MessageVO.builder()
                .id(42L).conversationId(7L).senderId(100L)
                .contentType((short) 1).content("hi")
                .build();
        when(sessionManager.sendToUsers(eq(List.of(100L, 200L)), anyString())).thenReturn(3);

        int sent = pushService.pushNewMessage(List.of(100L, 200L), msg);

        assertThat(sent).isEqualTo(3);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(sessionManager).sendToUsers(eq(List.of(100L, 200L)), cap.capture());
        String payload = cap.getValue();
        assertThat(payload).contains("\"type\":\"message\"");
        assertThat(payload).contains("\"id\":42");
        assertThat(payload).contains("\"content\":\"hi\"");
    }
}
