package com.lynn.nook.gateway.filter;

import com.lynn.nook.common.constant.CacheKeys;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.common.security.JwtUtil;
import com.lynn.nook.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HDR_USER_ID = "X-User-Id";
    private static final String HDR_USERNAME = "X-Username";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewayProperties props;
    private final ReactiveStringRedisTemplate redis;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
        }
        String token = auth.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = JwtUtil.parse(props.getJwt().getSecret(), token);
        } catch (JwtException e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return unauthorized(exchange, ResultCode.TOKEN_INVALID);
        }

        String userId = claims.getSubject();
        String username = claims.get("username", String.class);

        return redis.hasKey(CacheKeys.token(token))
                .flatMap(exists -> {
                    if (Boolean.FALSE.equals(exists)) {
                        return unauthorized(exchange, ResultCode.TOKEN_EXPIRED);
                    }
                    ServerHttpRequest mutated = request.mutate()
                            .header(HDR_USER_ID, userId)
                            .header(HDR_USERNAME, username == null ? "" : username)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : props.getGateway().getWhitelist()) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode rc) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + rc.getCode() + ",\"message\":\"" + rc.getMessage() + "\"}";
        DataBuffer buf = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return -100; // 越小越优先
    }
}
