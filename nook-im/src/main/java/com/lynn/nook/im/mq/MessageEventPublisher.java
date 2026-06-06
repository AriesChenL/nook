package com.lynn.nook.im.mq;

/**
 * 新消息事件发布器。
 * 实现根据 {@code nook.im.mq.enabled} 自动切换：
 *  - false：进程内直接调用 MessagePushService（单实例够用）
 *  - true（默认）：投递到 RabbitMQ 广播 exchange，所有 nook-im 实例收到后各自推送本机在线 session
 */
public interface MessageEventPublisher {

    void publishNewMessage(NewMessageEvent event);

    /** 撤回事件：与新消息同样的分发语义（本地直推 / MQ broadcasting）。 */
    void publishRecall(RecallEvent event);

    /** 在线状态事件：通知好友某用户上线/下线（本地直推 / MQ broadcasting）。 */
    void publishPresence(PresenceEvent event);
}
