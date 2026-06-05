package com.lynn.nook.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
    /** 用户对外标识：public_id（脱敏，字符串）。 */
    private String id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private String phone;
    private Short status;
}
