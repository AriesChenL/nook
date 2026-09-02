package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param newOwnerId 新群主的 user public_id
 */
public record TransferOwnerRequest(@NotBlank String newOwnerId) {
}
