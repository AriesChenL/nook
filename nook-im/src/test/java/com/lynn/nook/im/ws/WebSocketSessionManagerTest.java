package com.lynn.nook.im.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebSocketSessionManagerTest {

    private WebSocketSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new WebSocketSessionManager();
    }

    private WebSocketSession openSession(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    @Test
    void register_and_unregister_tracksSessionCount() {
        WebSocketSession s1 = openSession("s1");
        WebSocketSession s2 = openSession("s2");

        manager.register(100L, s1);
        manager.register(100L, s2);
        assertThat(manager.isOnline(100L)).isTrue();
        assertThat(manager.sessionCount(100L)).isEqualTo(2);

        manager.unregister(100L, s1);
        assertThat(manager.sessionCount(100L)).isEqualTo(1);
        manager.unregister(100L, s2);
        assertThat(manager.isOnline(100L)).isFalse();
        assertThat(manager.sessionCount(100L)).isZero();
    }

    @Test
    void register_ignoresNulls() {
        manager.register(null, openSession("s"));
        manager.register(1L, null);
        assertThat(manager.isOnline(1L)).isFalse();
    }

    @Test
    void sendToUser_sendsToAllOpenSessions() throws IOException {
        WebSocketSession s1 = openSession("s1");
        WebSocketSession s2 = openSession("s2");
        manager.register(7L, s1);
        manager.register(7L, s2);

        int sent = manager.sendToUser(7L, "hello");

        assertThat(sent).isEqualTo(2);
        ArgumentCaptor<TextMessage> cap = ArgumentCaptor.forClass(TextMessage.class);
        verify(s1).sendMessage(cap.capture());
        verify(s2).sendMessage(any(TextMessage.class));
        assertThat(cap.getValue().getPayload()).isEqualTo("hello");
    }

    @Test
    void sendToUser_skipsClosedSessions() throws IOException {
        WebSocketSession open = openSession("open");
        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.isOpen()).thenReturn(false);
        manager.register(8L, open);
        manager.register(8L, closed);

        int sent = manager.sendToUser(8L, "x");

        assertThat(sent).isEqualTo(1);
        verify(closed, never()).sendMessage(any());
    }

    @Test
    void sendToUser_countsOnlySuccessfulSessions_whenOneThrows() throws IOException {
        WebSocketSession good = openSession("good");
        WebSocketSession bad = openSession("bad");
        doThrow(new IOException("boom")).when(bad).sendMessage(any());
        manager.register(9L, good);
        manager.register(9L, bad);

        int sent = manager.sendToUser(9L, "x");

        assertThat(sent).isEqualTo(1);
    }

    @Test
    void sendToUser_returnsZeroWhenOffline() {
        assertThat(manager.sendToUser(404L, "x")).isZero();
    }

    @Test
    void sendToUsers_aggregatesAcrossUsers() throws IOException {
        manager.register(1L, openSession("a"));
        manager.register(2L, openSession("b"));
        manager.register(2L, openSession("c"));

        int sent = manager.sendToUsers(List.of(1L, 2L, 3L), "x");

        assertThat(sent).isEqualTo(3);
    }
}
