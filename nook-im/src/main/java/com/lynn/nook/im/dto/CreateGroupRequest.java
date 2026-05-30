package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateGroupRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String avatarUrl;

    /** 初始成员（不含创建者本人；创建者会自动作为群主加入）。 */
    @NotEmpty
    private List<Long> memberIds;
}
