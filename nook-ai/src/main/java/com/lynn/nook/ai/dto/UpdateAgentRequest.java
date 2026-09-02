package com.lynn.nook.ai.dto;

import jakarta.validation.constraints.Size;

public record UpdateAgentRequest(

        @Size(max = 64)
        String name,

        String persona,

        @Size(max = 512)
        String avatarUrl
) {
}
