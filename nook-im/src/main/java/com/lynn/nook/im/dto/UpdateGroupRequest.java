package com.lynn.nook.im.dto;

import jakarta.validation.constraints.Size;

/**
 * @param name      为空表示不改
 * @param avatarUrl 为空表示不改
 */
public record UpdateGroupRequest(

        @Size(max = 128)
        String name,

        @Size(max = 512)
        String avatarUrl
) {
}
