package com.lynn.nook.im.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 在线状态变更事件：某用户上线/下线，需通知其好友。
 * 跨实例通过 MQ broadcasting 分发——好友可能连在任意 nook-im 实例上。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceEvent {

    /** 状态发生变化的用户（内部数字 id，路由/日志用）。 */
    private Long userId;
    /** 状态发生变化用户的 public_id（推给前端的 presence 帧用）。 */
    private String userPublicId;
    /** true=上线 false=下线 */
    private boolean online;
    /** 该用户的好友 userId 列表（推送目标，内部数字）。Consumer 据此找本实例在线好友并推送。 */
    private List<Long> friendUserIds;
}
