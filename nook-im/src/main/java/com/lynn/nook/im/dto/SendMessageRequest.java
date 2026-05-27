package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull
    private Long conversationId;

    /** 1=text 2=image 3=file；缺省为 text */
    private Short contentType;

    @NotBlank
    @Size(max = 8000)
    private String content;
}
