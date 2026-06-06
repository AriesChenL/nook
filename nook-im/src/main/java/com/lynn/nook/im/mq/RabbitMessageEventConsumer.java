package com.lynn.nook.im.mq;

import com.lynn.nook.im.ws.MessagePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 新消息广播消费：监听本实例的匿名队列（绑定 {@link MqRouting#RK_NEW_MESSAGE}），
 * 把消息推送给注册在本机的 session。每个 nook-im 实例都有独立队列，故各收全量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
public class RabbitMessageEventConsumer {

    private final MessagePushService pushService;

    @RabbitListener(queues = "#{newMessageQueue.name}")
    public void onMessage(NewMessageEvent event) {
        if (event == null || event.getMessage() == null) return;
        int sent = pushService.pushNewMessage(event.getMemberUserIds(), event.getMessage());
        if (log.isDebugEnabled()) {
            log.debug("mq consumed: conv={}, message={}, locallyDelivered={}",
                    event.getConversationId(), event.getMessage().getId(), sent);
        }
    }
}
