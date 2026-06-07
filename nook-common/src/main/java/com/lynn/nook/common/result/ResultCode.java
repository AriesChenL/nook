package com.lynn.nook.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    OK(200, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 无效"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "HTTP 方法不被允许"),
    SERVER_ERROR(500, "服务器内部错误"),

    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXISTS(1002, "用户名已被占用"),
    PASSWORD_INCORRECT(1003, "密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),
    TOKEN_EXPIRED(1005, "Token 已过期"),
    TOKEN_INVALID(1006, "Token 无效"),
    PASSWORD_TOO_WEAK(1007, "新密码不符合复杂度要求"),
    SAME_AS_OLD_PASSWORD(1008, "新密码不能与旧密码相同"),

    FRIEND_CANNOT_ADD_SELF(2001, "不能添加自己为好友"),
    FRIEND_ALREADY(2002, "已是好友"),
    FRIEND_REQUEST_EXISTS(2003, "已存在待处理的好友申请"),
    FRIEND_REQUEST_NOT_FOUND(2004, "好友申请不存在或已被处理"),
    FRIEND_REQUEST_FORBIDDEN(2005, "无权操作此好友申请"),
    FRIEND_NOT_FOUND(2006, "对方不是你的好友"),

    CONVERSATION_NOT_FOUND(3001, "会话不存在"),
    NOT_CONVERSATION_MEMBER(3002, "不在该会话中"),
    MESSAGE_CONTENT_EMPTY(3003, "消息内容不能为空"),
    MESSAGE_NOT_FOUND(3004, "消息不存在"),
    MESSAGE_RECALL_FORBIDDEN(3005, "只能撤回自己发送的消息"),
    MESSAGE_RECALL_EXPIRED(3006, "消息发送已超过5分钟，无法撤回"),
    MESSAGE_ALREADY_RECALLED(3007, "消息已撤回"),

    CONVERSATION_NOT_GROUP(3008, "该会话不是群聊"),
    GROUP_PERMISSION_DENIED(3009, "无权进行该群操作"),
    GROUP_MEMBER_ALREADY(3010, "用户已在群中"),
    GROUP_MEMBER_NOT_FOUND(3011, "该用户不在群中"),
    GROUP_OWNER_CANNOT_LEAVE(3012, "群主退群前需先转让群主"),
    GROUP_ROLE_INVALID(3013, "非法的成员角色"),

    FILE_TOO_LARGE(3014, "文件超过大小上限"),
    FILE_TYPE_NOT_ALLOWED(3015, "不支持的文件类型"),
    FILE_META_MISSING(3016, "文件消息缺少文件信息"),

    AI_AGENT_NOT_FOUND(4001, "AI 智能体不存在"),
    AI_AGENT_FORBIDDEN(4002, "无权操作该 AI 智能体"),
    AI_CHAT_SESSION_NOT_FOUND(4003, "AI 对话会话不存在"),
    AI_MODEL_ERROR(4004, "AI 模型调用失败"),
    AI_AGENT_NAME_BLANK(4005, "AI 智能体名称不能为空"),

    PAY_PRODUCT_NOT_FOUND(5001, "商品或套餐不存在"),
    PAY_STRIPE_ERROR(5002, "支付渠道调用失败"),
    PAY_WEBHOOK_SIGNATURE_INVALID(5003, "Webhook 签名校验失败"),
    PAY_NOT_CONFIGURED(5004, "支付功能未正确配置"),
    PAY_SUBSCRIPTION_NOT_FOUND(5005, "未找到有效订阅");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
