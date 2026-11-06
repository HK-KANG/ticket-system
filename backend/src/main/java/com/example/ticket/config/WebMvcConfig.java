package com.example.ticket.config;

import com.example.ticket.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器。
 *
 * <p>拦截范围是 /api/**，也就是说**默认所有接口都要登录**，
 * 只有白名单里的能匿名访问。反过来做（默认放行、逐个加注解）一定会漏，
 * 漏一个就是越权漏洞。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",     // 登录本身不能要登录态
                        "/api/health",         // 健康检查给运维用
                        "/api/health/**"
                );
    }
}
