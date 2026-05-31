package com.lynn.nook.im.controller;

import com.lynn.nook.common.constant.RequestHeaders;
import com.lynn.nook.common.result.Result;
import com.lynn.nook.im.dto.PresignRequest;
import com.lynn.nook.im.dto.PresignVO;
import com.lynn.nook.im.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/im/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /** 申请文件直传的预签名 URL。鉴权由网关注入 X-User-Id。 */
    @PostMapping("/presign")
    public Result<PresignVO> presign(@RequestHeader(RequestHeaders.USER_ID) Long userId,
                                     @Valid @RequestBody PresignRequest req) {
        return Result.ok(fileStorageService.presignPut(userId, req));
    }
}
