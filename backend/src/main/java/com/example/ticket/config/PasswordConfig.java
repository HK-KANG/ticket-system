package com.example.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密器。
 *
 * <p>用 BCrypt 而不是 MD5/SHA：它自带随机盐，同一个密码每次加密结果都不同，
 * 彩虹表打不穿；而且计算慢（默认 10 轮），暴力破解成本高。
 *
 * <p>这里只引了 spring-security-crypto 这一个 jar，没有引入 Spring Security 整套过滤器链
 * —— 项目用自研拦截器做认证，不需要那套东西。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
