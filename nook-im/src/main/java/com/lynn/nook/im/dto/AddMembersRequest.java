package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * @param memberIds 待加入成员的 user public_id 列表
 */
public record AddMembersRequest(@NotEmpty List<String> memberIds) {
}
