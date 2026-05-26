package com.lynn.nook.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    OK(200, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 无效"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器内部错误"),

    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXISTS(1002, "用户名已被占用"),
    PASSWORD_INCORRECT(1003, "密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),
    TOKEN_EXPIRED(1005, "Token 已过期"),
    TOKEN_INVALID(1006, "Token 无效");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
