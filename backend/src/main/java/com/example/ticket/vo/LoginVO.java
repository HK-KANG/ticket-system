package com.example.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /** JWT 令牌，前端放进 Authorization: Bearer xxx */
    private String token;

    /** 登录用户信息，前端据此决定进哪个首页、显示哪些菜单 */
    private UserVO user;
}
