package com.lynn.nook.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFriendRemarkRequest {

    @Size(max = 64)
    private String remark;
}
