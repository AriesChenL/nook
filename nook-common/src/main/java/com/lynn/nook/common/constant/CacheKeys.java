package com.lynn.nook.common.constant;

public final class CacheKeys {

    private CacheKeys() {}

    /** 用户登录 Token：token -> userId */
    public static String token(String token) {
        return "nook:auth:token:" + token;
    }

    /** 用户在线状态：userId -> sessionId */
    public static String online(long userId) {
        return "nook:im:online:" + userId;
    }
}
