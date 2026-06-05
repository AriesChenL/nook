package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReadCursorRequest {

    /** 已读到的最大消息 public_id。 */
    @NotBlank
    private String lastReadMsgId;
}
