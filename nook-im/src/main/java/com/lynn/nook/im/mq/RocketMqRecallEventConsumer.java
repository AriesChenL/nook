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
 * 撤回事件广播消费：每个 nook-im 实例都收到，把撤回推给注册在本机的 session。
 * 与 {@link RocketMqMessageEventConsumer} 平行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopics.RECALL,
        consumerGroup = "nook-im-recall-consumer",
        messageModel = MessageModel.BROADCASTING
)
public class RocketMqRecallEventConsumer implements RocketMQListener<RecallEvent> {

    private final MessagePushService pushService;

    @Override
    public void onMessage(RecallEvent event) {
        if (event == null || event.getMessageId() == null) return;
        int sent = pushService.pushRecall(event.getMemberUserIds(),
                event.getConversationPublicId(), event.getMessagePublicId());
        if (log.isDebugEnabled()) {
            log.debug("mq recall consumed: conv={}, msg={}, locallyDelivered={}",
                    event.getConversationId(), event.getMessageId(), sent);
        }
    }
}
