package com.example.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {

    /** 登录名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名过长")
    private String username;

    /** 明文密码，只在传输中出现，服务端立刻用 BCrypt 比对，不落库 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 50, message = "密码过长")
    private String password;
}
