package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param memberIds 初始成员的 user public_id（不含创建者本人；创建者会自动作为群主加入）
 */
public record CreateGroupRequest(

        @NotBlank
        @Size(max = 128)
        String name,

        @Size(max = 512)
        String avatarUrl,

        @NotEmpty
        List<String> memberIds
) {
}
