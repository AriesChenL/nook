package com.lynn.nook.im.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * nook-user 公开资料的本地映射（nook-im 不依赖 nook-user，故自定义 DTO，字段名与 UserVO 对齐由 Jackson 映射）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO {

    /** user 的 public_id（脱敏对外标识，字符串），由 nook-user /user/batch 输出。 */
    private String id;
    private String username;
    private String nickname;
    private String avatarUrl;
}
