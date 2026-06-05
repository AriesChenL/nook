package com.lynn.nook.im.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 消息撤回事件，跨实例通过 MQ broadcasting 分发——让所有 nook-im 实例都能把撤回推给本机在线成员。
 * 与 {@link NewMessageEvent} 平行；保持 POJO + 零业务依赖，方便序列化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallEvent {

    private Long conversationId;
    private Long messageId;
    /** 会话 public_id（推给前端的撤回帧用）。 */
    private String conversationPublicId;
    /** 消息 public_id（推给前端的撤回帧用）。 */
    private String messagePublicId;
    /** 该会话全体成员 userId（内部路由用，保持数字）。Consumer 据此找本实例在线者并推送撤回。 */
    private List<Long> memberUserIds;
}
