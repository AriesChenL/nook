package com.lynn.nook.im.mq;

import com.lynn.nook.im.dto.MessageVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * IM 新消息事件，跨实例通过 MQ broadcasting 分发。
 * 注意：保持 POJO + 零业务依赖，方便序列化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewMessageEvent {

    private Long conversationId;
    /** 该会话的全体成员 userId。Consumer 据此找本实例在线者并推送。 */
    private List<Long> memberUserIds;
    private MessageVO message;
}
