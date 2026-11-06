package com.example.ticket.common;

/**
 * 资源不存在。
 *
 * <p>单独一个异常类型，是为了让 HTTP 状态码也是 404（而不是 200 + body 里 code=404）。
 * 前端、网关、监控都靠状态码判断结果，body 里写 404 而 HTTP 返回 200，
 * 会让"接口成功率"报表永远是 100%，线上真出问题也发现不了。
 */
public class NotFoundException extends BizException {

    public NotFoundException() {
        super(ResultCode.NOT_FOUND);
    }

    public NotFoundException(String message) {
        super(ResultCode.NOT_FOUND, message);
    }
}
