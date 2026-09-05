package com.lynn.nook.pay.service;

import com.stripe.model.Event;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

/** 在业务事务内原子 claim Stripe event；业务失败回滚后 Stripe 重试仍可再次 claim。 */
@Repository
public class WebhookEventStore {

    private final JdbcTemplate jdbc;

    public WebhookEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(Event event, String objectId) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbc.update("""
                INSERT INTO stripe_events
                    (event_id, type, object_id, event_created, livemode, received_at, processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                event.getId(), event.getType(), objectId, event.getCreated(), event.getLivemode(),
                Timestamp.from(now.toInstant()), Timestamp.from(now.toInstant())) == 1;
    }
}
