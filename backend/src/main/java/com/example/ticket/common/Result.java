package com.example.ticket.common;

import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一返回体。
 * 所有接口（包括异常）都必须返回这个结构：code / message / data / traceId。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** 业务码，0 表示成功，其余见 {@link ResultCode} */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 链路追踪 ID，与日志中的 traceId 一致，方便排查 */
    private String traceId;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        r.traceId = MDC.get(TraceIdFilter.TRACE_ID);
        return r;
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.traceId = MDC.get(TraceIdFilter.TRACE_ID);
        return r;
    }
}
