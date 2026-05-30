package com.lynn.nook.im.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 群成员视图：会话成员关系 + 聚合的用户公开资料。资料取不到时昵称/头像为 null。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberVO {

    private Long userId;
    /** 1=普通 2=管理员 3=群主 */
    private Short role;
    private OffsetDateTime joinedAt;
    private String username;
    private String nickname;
    private String avatarUrl;
}
