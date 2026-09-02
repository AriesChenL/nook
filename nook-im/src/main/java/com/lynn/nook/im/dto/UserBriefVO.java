package com.lynn.nook.im.dto;

/**
 * nook-user 公开资料的本地映射（nook-im 不依赖 nook-user，故自定义 DTO，字段名与 UserVO 对齐由 Jackson 映射）。
 *
 * @param id user 的 public_id（脱敏对外标识，字符串），由 nook-user /user/batch 输出
 */
public record UserBriefVO(String id, String username, String nickname, String avatarUrl) {
}
