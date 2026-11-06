package com.example.ticket.common;

/**
 * 未登录 / 登录态失效。
 * 单独一类而不是复用 BizException，是因为它要同时满足两件事：
 * 响应体是统一 Result 结构，HTTP 状态码是 401。
 */
public class AuthException extends BizException {

    public AuthException() {
        super(ResultCode.UNAUTHORIZED);
    }

    public AuthException(String message) {
        super(ResultCode.UNAUTHORIZED, message);
    }
}
