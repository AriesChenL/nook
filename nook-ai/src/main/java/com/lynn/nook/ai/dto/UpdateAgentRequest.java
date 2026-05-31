package com.lynn.nook.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAgentRequest {

    @Size(max = 64)
    private String name;

    private String persona;

    @Size(max = 512)
    private String avatarUrl;
}
