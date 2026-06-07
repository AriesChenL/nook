package com.lynn.nook.pay.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 已处理的 Stripe 事件去重表。Stripe 会重试投递，靠 event_id 唯一约束保证幂等。
 */
@Data
@Table("stripe_events")
public class StripeEvent {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** Stripe 事件 id（evt_xxx），唯一。 */
    @Column("event_id")
    private String eventId;

    private String type;

    @Column("received_at")
    private OffsetDateTime receivedAt;
}
