package com.lynn.nook.im.client;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.dto.UserBriefVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 调 nook-user 的批量资料接口。直连 {@code lb://nook-user}，绕过网关——
 * nook-user 的 {@code /user/batch} 不读 X-User-Id，是公开脱敏视图，无需鉴权透传。
 */
@FeignClient(name = "nook-user")
public interface UserClient {

    @GetMapping("/user/batch")
    Result<List<UserBriefVO>> listByIds(@RequestParam("ids") List<Long> ids);

    /** 取某用户的好友 userId 列表，用于在线状态广播。 */
    @GetMapping("/user/friends/of/{userId}")
    Result<List<Long>> friendIds(@PathVariable("userId") Long userId);
}
