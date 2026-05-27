package com.lynn.nook.common.constant;

/**
 * Gateway 透传给下游服务的请求头。
 * 下游通过 @RequestHeader 读取，无需自行解析 JWT。
 */
public final class RequestHeaders {

    private RequestHeaders() {}

    public static final String USER_ID  = "X-User-Id";
    public static final String USERNAME = "X-Username";
}
