package com.lynn.nook.pay.dto;

import jakarta.validation.constraints.NotBlank;

public record SyncSubscriptionRequest(@NotBlank String sessionId) {
}
