package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddMembersRequest {

    @NotEmpty
    private List<Long> memberIds;
}
