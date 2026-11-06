package com.example.ticket.security;

/**
 * 当前登录用户，从 JWT 解析出来放进 ThreadLocal。
 *
 * @param userId   用户 ID
 * @param username 登录名
 * @param role     角色
 */
public record LoginUser(Long userId, String username, String role) {
}
