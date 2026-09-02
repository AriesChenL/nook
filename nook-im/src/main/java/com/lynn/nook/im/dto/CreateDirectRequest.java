package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param peerUserId 对端的 user public_id
 */
public record CreateDirectRequest(@NotBlank String peerUserId) {
}
