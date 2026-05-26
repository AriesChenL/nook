package com.lynn.nook.common.result;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return build(ResultCode.OK.getCode(), ResultCode.OK.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return build(ResultCode.OK.getCode(), ResultCode.OK.getMessage(), data);
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return build(rc.getCode(), rc.getMessage(), null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return build(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return build(ResultCode.SERVER_ERROR.getCode(), message, null);
    }

    private static <T> Result<T> build(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.data = data;
        return r;
    }
}
