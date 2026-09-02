package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param lastReadMsgId 已读到的最大消息 public_id
 */
public record ReadCursorRequest(@NotBlank String lastReadMsgId) {
}
