package com.lynn.nook.im.ws;

import com.lynn.nook.common.constant.RequestHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 从 Gateway 透传的 X-User-Id 取出 userId 写入握手 attributes。
 * 没有 userId（Gateway 未拦截 / 被绕过）则直接 401，不让握手通过。
 */
@Slf4j
@Component
public class UserIdHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst(RequestHeaders.USER_ID);
        if (header == null || header.isBlank()) {
            log.debug("ws handshake rejected: missing X-User-Id");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            attributes.put(WebSocketSessionManager.ATTR_USER_ID, Long.parseLong(header));
            return true;
        } catch (NumberFormatException e) {
            log.debug("ws handshake rejected: bad X-User-Id {}", header);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
