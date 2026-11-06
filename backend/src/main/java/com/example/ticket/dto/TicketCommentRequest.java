package com.example.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工单回复请求。
 */
@Data
public class TicketCommentRequest {

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容最长 1000 字")
    private String content;

    /**
     * 是否内部备注：true 表示只有坐席能看到，客户查详情时会被过滤掉。
     * 客户角色传 true 会被服务端强制改成 false——权限判断必须在服务端做。
     */
    private Boolean internal = false;
}
