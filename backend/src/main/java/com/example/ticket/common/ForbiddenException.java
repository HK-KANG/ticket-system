package com.example.ticket.common;

/**
 * 已登录但没有该操作的权限。
 * 与 {@link AuthException} 同理：响应体统一 Result，HTTP 状态码 403。
 */
public class ForbiddenException extends BizException {

    public ForbiddenException() {
        super(ResultCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ResultCode.FORBIDDEN, message);
    }
}
