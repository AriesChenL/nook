package com.lynn.nook.im.dto;

import java.time.OffsetDateTime;

/**
 * 群成员视图：会话成员关系 + 聚合的用户公开资料。资料取不到时昵称/头像为 null。
 *
 * @param userId 成员的 user public_id（脱敏对外标识）
 * @param role   1=普通 2=管理员 3=群主
 */
public record MemberVO(String userId, Short role, OffsetDateTime joinedAt,
                       String username, String nickname, String avatarUrl) {
}
