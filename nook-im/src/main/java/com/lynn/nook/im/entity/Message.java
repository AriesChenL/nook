package com.lynn.nook.im.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Table("messages")
public class Message {

    public static final short TYPE_TEXT   = 1;
    public static final short TYPE_IMAGE  = 2;
    public static final short TYPE_FILE   = 3;
    public static final short TYPE_SYSTEM = 4;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 不可枚举的对外标识（UUID），前端可见的消息 id 用它。内部仍用数字主键。 */
    @Column("public_id")
    private String publicId;

    @Column("conversation_id")
    private Long conversationId;

    @Column("sender_id")
    private Long senderId;

    @Column("content_type")
    private Short contentType;

    private String content;

    /** 文件消息（contentType=2/3）专用：下载/预览地址、原始文件名、字节大小、MIME。 */
    @Column("file_url")
    private String fileUrl;

    @Column("file_name")
    private String fileName;

    @Column("file_size")
    private Long fileSize;

    @Column("media_type")
    private String mediaType;

    /** 0=正常 1=已撤回 */
    private Short recalled;

    @Column("recalled_at")
    private OffsetDateTime recalledAt;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
