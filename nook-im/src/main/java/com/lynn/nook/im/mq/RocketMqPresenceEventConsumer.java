package com.lynn.nook.im.mq;

import com.lynn.nook.im.ws.MessagePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 在线状态事件广播消费：每个实例都收到，把上/下线推给注册在本机的好友 session。
 * 与 {@link RocketMqMessageEventConsumer} / {@link RocketMqRecallEventConsumer} 平行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopics.PRESENCE,
        consumerGroup = "nook-im-presence-consumer",
        messageModel = MessageModel.BROADCASTING
)
public class RocketMqPresenceEventConsumer implements RocketMQListener<PresenceEvent> {

    private final MessagePushService pushService;

    @Override
    public void onMessage(PresenceEvent event) {
        if (event == null || event.getUserId() == null) return;
        int sent = pushService.pushPresence(event.getFriendUserIds(), event.getUserId(), event.isOnline());
        if (log.isDebugEnabled()) {
            log.debug("mq presence consumed: user={}, online={}, locallyDelivered={}",
                    event.getUserId(), event.isOnline(), sent);
        }
    }
}
