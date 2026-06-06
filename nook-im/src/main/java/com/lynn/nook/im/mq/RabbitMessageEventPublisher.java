package com.lynn.nook.im.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 发布器：把 IM 事件投递到广播 exchange，所有 nook-im 实例各自消费后推送本机在线 session。
 * 仅 {@code nook.im.mq.enabled=true} 时生效；否则由 {@link LocalMessageEventPublisher} 进程内直推。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
public class RabbitMessageEventPublisher implements MessageEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishNewMessage(NewMessageEvent event) {
        if (event == null || event.getMessage() == null) return;
        send(MqRouting.RK_NEW_MESSAGE, event, "conv=" + event.getConversationId());
    }

    @Override
    public void publishRecall(RecallEvent event) {
        if (event == null || event.getMessageId() == null) return;
        send(MqRouting.RK_RECALL, event, "conv=" + event.getConversationId());
    }

    @Override
    public void publishPresence(PresenceEvent event) {
        if (event == null || event.getUserId() == null) return;
        send(MqRouting.RK_PRESENCE, event, "user=" + event.getUserId());
    }

    /** 发送失败只告警、不抛出：MQ 抖动不应阻断主流程（消息已落库，历史拉取可补齐）。 */
    private void send(String routingKey, Object event, String ctx) {
        try {
            rabbitTemplate.convertAndSend(MqRouting.EXCHANGE, routingKey, event);
            if (log.isDebugEnabled()) {
                log.debug("mq send ok: rk={}, {}", routingKey, ctx);
            }
        } catch (Exception e) {
            log.warn("mq publish error: rk={}, {}, err={}", routingKey, ctx, e.getMessage());
        }
    }
}
