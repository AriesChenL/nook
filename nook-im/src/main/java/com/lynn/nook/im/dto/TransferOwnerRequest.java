package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferOwnerRequest {

    @NotNull
    private Long newOwnerId;
}
