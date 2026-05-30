package com.lynn.nook.common.constant;

public final class CacheKeys {

    private CacheKeys() {}

    /** 用户登录 Token：token -> userId */
    public static String token(String token) {
        return "nook:auth:token:" + token;
    }

    /** 用户的有效 token 集合：userId -> Set&lt;token&gt;。用于多端踢出（一处登录踢其它端）。 */
    public static String userTokens(long userId) {
        return "nook:auth:user-tokens:" + userId;
    }

    /** 用户在线状态：userId -> sessionId */
    public static String online(long userId) {
        return "nook:im:online:" + userId;
    }
}
