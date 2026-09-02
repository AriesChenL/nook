package com.lynn.nook.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param persona   人格设定，注入 sysPrompt
 * @param modelName 可选；缺省 deepseek-v4-flash
 */
public record CreateAgentRequest(

        @NotBlank
        @Size(max = 64)
        String name,

        String persona,

        @Size(max = 512)
        String avatarUrl,

        @Size(max = 64)
        String modelName
) {
}
