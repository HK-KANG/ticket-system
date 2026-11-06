package com.example.ticket.common;

/**
 * 业务错误码。
 * 约定：0 成功；4xx 客户端错误；1xxx 业务错误；5xx 系统错误。
 */
public enum ResultCode {

    SUCCESS(0, "成功"),

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    BIZ_ERROR(1000, "业务处理失败"),

    SYSTEM_ERROR(500, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
