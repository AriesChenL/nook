package com.lynn.nook.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateFriendRemarkRequest(@Size(max = 64) String remark) {
}
