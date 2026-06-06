package com.lynn.nook.im.mq;

import com.lynn.nook.im.ws.MessagePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 在线状态事件广播消费：把上/下线推给注册在本机的好友 session。
 * 与 {@link RabbitMessageEventConsumer} / {@link RabbitRecallEventConsumer} 平行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
public class RabbitPresenceEventConsumer {

    private final MessagePushService pushService;

    @RabbitListener(queues = "#{presenceQueue.name}")
    public void onMessage(PresenceEvent event) {
        if (event == null || event.getUserId() == null) return;
        int sent = pushService.pushPresence(event.getFriendUserIds(), event.getUserPublicId(), event.isOnline());
        if (log.isDebugEnabled()) {
            log.debug("mq presence consumed: user={}, online={}, locallyDelivered={}",
                    event.getUserId(), event.isOnline(), sent);
        }
    }
}
