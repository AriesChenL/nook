package com.lynn.nook.im.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
public class RocketMqMessageEventPublisher implements MessageEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void publishNewMessage(NewMessageEvent event) {
        if (event == null || event.getMessage() == null) return;
        try {
            // 用消息 id 做 key 便于排查；conversationId 作为消息分片键（hash 到同一队列保序）
            rocketMQTemplate.asyncSendOrderly(
                    MqTopics.NEW_MESSAGE,
                    event,
                    String.valueOf(event.getConversationId()),
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            if (log.isDebugEnabled()) {
                                log.debug("mq send ok: msgId={}, queueId={}",
                                        sendResult.getMsgId(), sendResult.getMessageQueue());
                            }
                        }
                        @Override
                        public void onException(Throwable e) {
                            log.warn("mq send failed: conv={}, err={}",
                                    event.getConversationId(), e.getMessage());
                        }
                    }
            );
        } catch (Exception e) {
            log.warn("mq publish error: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publishRecall(RecallEvent event) {
        if (event == null || event.getMessageId() == null) return;
        try {
            // 同会话撤回与新消息共用 conversationId 分片键，保证相对顺序
            rocketMQTemplate.asyncSendOrderly(
                    MqTopics.RECALL,
                    event,
                    String.valueOf(event.getConversationId()),
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            if (log.isDebugEnabled()) {
                                log.debug("mq recall send ok: msgId={}, queueId={}",
                                        sendResult.getMsgId(), sendResult.getMessageQueue());
                            }
                        }
                        @Override
                        public void onException(Throwable e) {
                            log.warn("mq recall send failed: conv={}, err={}",
                                    event.getConversationId(), e.getMessage());
                        }
                    }
            );
        } catch (Exception e) {
            log.warn("mq recall publish error: {}", e.getMessage(), e);
        }
    }
}
