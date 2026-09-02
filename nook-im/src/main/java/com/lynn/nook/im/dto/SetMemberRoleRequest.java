package com.lynn.nook.im.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param role 目标角色：1=普通 2=管理员（不能通过此接口设置群主，群主用转让接口）
 */
public record SetMemberRoleRequest(@NotNull Short role) {
}
