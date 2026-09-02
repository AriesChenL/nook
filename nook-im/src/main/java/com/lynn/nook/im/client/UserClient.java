package com.lynn.nook.im.client;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.dto.UserBriefVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 调 nook-user 的批量资料接口。直连 {@code lb://nook-user}，绕过网关——
 * nook-user 的 {@code /user/batch} 不读 X-User-Id，是公开脱敏视图，无需鉴权透传。
 */
@FeignClient(name = "nook-user")
public interface UserClient {

    /**
     * 批量取用户公开资料。入参 ids 为内部<b>数字</b> userId（im 持有数字 sender_id），
     * 返回的 {@link UserBriefVO#id()} 是 user 的 <b>public_id 字符串</b>（脱敏对外标识）。
     */
    @GetMapping("/user/batch")
    Result<List<UserBriefVO>> listByIds(@RequestParam("ids") List<Long> ids);

    /**
     * 把前端传来的 user <b>public_id 字符串</b>批量解析回内部数字 userId。
     * 用于「按 public_id 和某人建直聊」「按 public_id 找人」等入口。
     * <p>返回 {@code Map<public_id, 数字 userId>}；解析不到的 public_id 不出现在 map 中，
     * 由调用方据此抛资源不存在。
     * <p><b>签名待与 nook-user 对齐</b>：当前按「入参 publicIds 逗号分隔字符串列表，返回 Map&lt;String,Long&gt;」实现。
     */
    @GetMapping("/user/resolve")
    Result<Map<String, Long>> resolve(@RequestParam("publicIds") List<String> publicIds);

    /** 取某用户的好友 userId 列表（内部数字），用于在线状态广播。保持数字，不脱敏。 */
    @GetMapping("/user/friends/of/{userId}")
    Result<List<Long>> friendIds(@PathVariable("userId") Long userId);
}
