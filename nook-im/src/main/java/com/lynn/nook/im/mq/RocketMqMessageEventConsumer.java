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
 * 广播消费：每个 nook-im 实例都收到完整事件流。
 * 本实例只负责把消息推送给注册在本机的 session；其它实例同步处理它们的本地连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopics.NEW_MESSAGE,
        consumerGroup = "nook-im-new-message-consumer",
        messageModel = MessageModel.BROADCASTING
)
public class RocketMqMessageEventConsumer implements RocketMQListener<NewMessageEvent> {

    private final MessagePushService pushService;

    @Override
    public void onMessage(NewMessageEvent event) {
        if (event == null || event.getMessage() == null) return;
        int sent = pushService.pushNewMessage(event.getMemberUserIds(), event.getMessage());
        if (log.isDebugEnabled()) {
            log.debug("mq consumed: conv={}, message={}, locallyDelivered={}",
                    event.getConversationId(), event.getMessage().getId(), sent);
        }
    }
}
