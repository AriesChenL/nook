package com.lynn.nook.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @Size(max = 128)
    private String title;
}
