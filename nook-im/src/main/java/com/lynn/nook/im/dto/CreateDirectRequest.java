package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDirectRequest {

    /** 对端的 user public_id。 */
    @NotBlank
    private String peerUserId;
}
