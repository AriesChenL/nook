package com.lynn.nook.im.mq;

import com.lynn.nook.im.ws.MessagePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 单实例本地分发：跳过 MQ 直接推送给本进程内在线 session。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "false", matchIfMissing = true)
public class LocalMessageEventPublisher implements MessageEventPublisher {

    private final MessagePushService pushService;

    @Override
    public void publishNewMessage(NewMessageEvent event) {
        if (event == null || event.getMessage() == null) return;
        int sent = pushService.pushNewMessage(event.getMemberUserIds(), event.getMessage());
        if (log.isDebugEnabled()) {
            log.debug("local push: conv={}, members={}, delivered={}",
                    event.getConversationId(),
                    event.getMemberUserIds() == null ? 0 : event.getMemberUserIds().size(),
                    sent);
        }
    }

    @Override
    public void publishRecall(RecallEvent event) {
        if (event == null || event.getMessageId() == null) return;
        int sent = pushService.pushRecall(event.getMemberUserIds(), event.getConversationId(), event.getMessageId());
        if (log.isDebugEnabled()) {
            log.debug("local recall push: conv={}, msg={}, delivered={}",
                    event.getConversationId(), event.getMessageId(), sent);
        }
    }

    @Override
    public void publishPresence(PresenceEvent event) {
        if (event == null || event.getUserId() == null) return;
        int sent = pushService.pushPresence(event.getFriendUserIds(), event.getUserId(), event.isOnline());
        if (log.isDebugEnabled()) {
            log.debug("local presence push: user={}, online={}, delivered={}",
                    event.getUserId(), event.isOnline(), sent);
        }
    }
}
