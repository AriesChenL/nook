package com.lynn.nook.im.ws;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.lynn.nook.im.service.PresenceBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ChatWebSocketHandlerTest {

    private WebSocketSessionManager sessionManager;
    private PresenceService presenceService;
    private PresenceBroadcastService presenceBroadcastService;
    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        sessionManager = mock(WebSocketSessionManager.class);
        presenceService = mock(PresenceService.class);
        presenceBroadcastService = mock(PresenceBroadcastService.class);
        handler = new ChatWebSocketHandler(sessionManager, presenceService, presenceBroadcastService, JsonMapper.builder().build());
    }

    private WebSocketSession sessionWith(Object userIdAttr) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        if (userIdAttr != null) attrs.put(WebSocketSessionManager.ATTR_USER_ID, userIdAttr);
        when(s.getAttributes()).thenReturn(attrs);
        when(s.getId()).thenReturn("test");
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    @Test
    void onOpen_registersAndMarksOnlineAndSendsReady() throws Exception {
        WebSocketSession s = sessionWith(42L);
        when(sessionManager.sessionCount(42L)).thenReturn(1);

        handler.afterConnectionEstablished(s);

        verify(sessionManager).register(42L, s);
        verify(presenceService).markOnline(42L);
        verify(presenceBroadcastService).onOnline(42L); // 首个连接 → 广播上线
        ArgumentCaptor<TextMessage> cap = ArgumentCaptor.forClass(TextMessage.class);
        verify(s).sendMessage(cap.capture());
        assertThat(cap.getValue().getPayload())
                .contains("\"type\":\"ready\"")
                .contains("\"userId\":42");
    }

    @Test
    void onOpen_secondSession_doesNotRebroadcastOnline() throws Exception {
        WebSocketSession s = sessionWith(42L);
        when(sessionManager.sessionCount(42L)).thenReturn(2); // 已有一个连接

        handler.afterConnectionEstablished(s);

        verify(presenceService).markOnline(42L);
        verify(presenceBroadcastService, never()).onOnline(anyLong());
    }

    @Test
    void onOpen_withoutUserIdAttribute_closesSession() throws Exception {
        WebSocketSession s = sessionWith(null);
        handler.afterConnectionEstablished(s);
        verify(s).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(sessionManager, presenceService);
    }

    @Test
    void onPing_replyPong() throws Exception {
        WebSocketSession s = sessionWith(1L);
        handler.handleTextMessage(s, new TextMessage("{\"type\":\"ping\"}"));
        ArgumentCaptor<TextMessage> cap = ArgumentCaptor.forClass(TextMessage.class);
        verify(s).sendMessage(cap.capture());
        assertThat(cap.getValue().getPayload()).contains("\"type\":\"pong\"");
    }

    @Test
    void onClose_unregisters_and_marksOfflineWhenLastSession() {
        WebSocketSession s = sessionWith(7L);
        when(sessionManager.sessionCount(7L)).thenReturn(0);

        handler.afterConnectionClosed(s, CloseStatus.NORMAL);

        verify(sessionManager).unregister(7L, s);
        verify(presenceService).markOffline(7L);
        verify(presenceBroadcastService).onOffline(7L); // 最后一个连接断开 → 广播下线
    }

    @Test
    void onClose_keepsOnlineWhenOtherSessionsRemain() {
        WebSocketSession s = sessionWith(7L);
        when(sessionManager.sessionCount(7L)).thenReturn(1);

        handler.afterConnectionClosed(s, CloseStatus.NORMAL);

        verify(sessionManager).unregister(7L, s);
        verify(presenceService, never()).markOffline(anyLong());
        verify(presenceBroadcastService, never()).onOffline(anyLong());
    }
}
