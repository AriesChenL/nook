package com.lynn.nook.im.mq;

/**
 * RabbitMQ 路由常量。
 *
 * <p>采用 <b>topic exchange + 每实例匿名队列</b> 实现广播：所有 nook-im 实例各自声明
 * 独占、自动删除的匿名队列绑定到同一个 exchange，于是每个实例都能收到全量事件流，
 * 各自把消息推送给注册在本机的 WebSocket session（等价于原 RocketMQ 的 BROADCASTING）。
 */
public final class MqRouting {

    private MqRouting() {}

    /** IM 事件广播 exchange（topic 类型，持久化）。 */
    public static final String EXCHANGE = "nook.im.events";

    /** 新消息事件路由键。 */
    public static final String RK_NEW_MESSAGE = "message.new";

    /** 撤回事件路由键。 */
    public static final String RK_RECALL = "message.recall";

    /** 在线状态事件路由键。 */
    public static final String RK_PRESENCE = "presence";
}
