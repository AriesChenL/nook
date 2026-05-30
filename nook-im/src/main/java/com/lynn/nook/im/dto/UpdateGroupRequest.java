package com.lynn.nook.im.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGroupRequest {

    /** 为空表示不改 */
    @Size(max = 128)
    private String name;

    /** 为空表示不改 */
    @Size(max = 512)
    private String avatarUrl;
}
