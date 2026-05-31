package com.lynn.nook.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAgentRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 人格设定，注入 sysPrompt */
    private String persona;

    @Size(max = 512)
    private String avatarUrl;

    /** 可选；缺省 deepseek-v4-flash */
    @Size(max = 64)
    private String modelName;
}
