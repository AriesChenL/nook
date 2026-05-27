package com.lynn.nook.im.ws;

import com.lynn.nook.common.constant.RequestHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserIdHandshakeInterceptorTest {

    private UserIdHandshakeInterceptor interceptor;
    private ServerHttpRequest request;
    private ServerHttpResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new UserIdHandshakeInterceptor();
        request = mock(ServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);
    }

    private void givenHeader(String value) {
        HttpHeaders h = new HttpHeaders();
        if (value != null) h.put(RequestHeaders.USER_ID, List.of(value));
        when(request.getHeaders()).thenReturn(h);
    }

    @Test
    void accepts_validUserId_andStoresAsLong() {
        givenHeader("123");
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(request, response, null, attrs);

        assertThat(ok).isTrue();
        assertThat(attrs.get(WebSocketSessionManager.ATTR_USER_ID)).isEqualTo(123L);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void rejects_whenHeaderMissing() {
        givenHeader(null);
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(request, response, null, attrs);

        assertThat(ok).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejects_whenHeaderNotNumeric() {
        givenHeader("abc");
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(request, response, null, attrs);

        assertThat(ok).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
