package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddMembersRequest {

    /** 待加入成员的 user public_id 列表。 */
    @NotEmpty
    private List<String> memberIds;
}
