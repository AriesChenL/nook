package com.lynn.nook.im.mq;

public final class MqTopics {

    private MqTopics() {}

    /** 新消息事件：每条 IM 消息落库后投递；多实例 broadcasting 消费。 */
    public static final String NEW_MESSAGE = "nook-im-new-message";
}
