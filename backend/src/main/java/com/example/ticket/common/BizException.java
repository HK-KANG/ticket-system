package com.example.ticket.common;

/**
 * 业务异常。
 * Service 层遇到可预期的失败（余额不足、状态不允许等）直接抛这个，
 * 由 {@link GlobalExceptionHandler} 统一转成 Result，不要自己 try-catch 后手写返回。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
