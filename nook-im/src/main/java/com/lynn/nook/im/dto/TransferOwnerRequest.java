package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TransferOwnerRequest {

    /** 新群主的 user public_id。 */
    @NotBlank
    private String newOwnerId;
}
