package com.lynn.nook.im.mq;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（仅 {@code nook.im.mq.enabled=true} 时生效）。
 *
 * <p>广播模型：一个持久化 topic exchange + 每个实例 3 条<b>匿名队列</b>
 * （独占、自动删除、非持久）分别绑定新消息 / 撤回 / 在线状态三种路由键。
 * 实例间互不共享队列，因此每个实例都拿到全量事件，各推本机在线 session。
 *
 * <p>消息体用 JSON 序列化；声明 {@link MessageConverter} 后 Spring Boot 会自动
 * 同时应用到 RabbitTemplate（生产）和监听容器工厂（消费）。
 */
@Configuration
@ConditionalOnProperty(name = "nook.im.mq.enabled", havingValue = "true")
public class RabbitConfig {

    @Bean
    public TopicExchange imEventsExchange() {
        return ExchangeBuilder.topicExchange(MqRouting.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue newMessageQueue() {
        return broadcastQueue();
    }

    @Bean
    public Queue recallQueue() {
        return broadcastQueue();
    }

    @Bean
    public Queue presenceQueue() {
        return broadcastQueue();
    }

    /**
     * 每实例广播队列：匿名（客户端生成 UUID 名）、非持久、独占、自动删除。
     * <p>移除 {@code x-queue-master-locator}：RabbitMQ 4.x 已废弃该特性并默认拒绝
     * 带此参数的队列声明（reply-code=541），而 Spring AMQP 的 AnonymousQueue 仍会默认带上。
     */
    private static Queue broadcastQueue() {
        AnonymousQueue queue = new AnonymousQueue();
        queue.getArguments().remove("x-queue-master-locator");
        return queue;
    }

    @Bean
    public Binding newMessageBinding(Queue newMessageQueue, TopicExchange imEventsExchange) {
        return BindingBuilder.bind(newMessageQueue).to(imEventsExchange).with(MqRouting.RK_NEW_MESSAGE);
    }

    @Bean
    public Binding recallBinding(Queue recallQueue, TopicExchange imEventsExchange) {
        return BindingBuilder.bind(recallQueue).to(imEventsExchange).with(MqRouting.RK_RECALL);
    }

    @Bean
    public Binding presenceBinding(Queue presenceQueue, TopicExchange imEventsExchange) {
        return BindingBuilder.bind(presenceQueue).to(imEventsExchange).with(MqRouting.RK_PRESENCE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
