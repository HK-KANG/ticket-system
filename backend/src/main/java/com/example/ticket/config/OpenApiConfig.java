package com.example.ticket.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档基本信息。
 * 访问 http://localhost:8080/swagger-ui.html 查看。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticketOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("售后工单系统 API")
                .description("3 端（客户 / 坐席 / 管理）共用的后端接口文档")
                .version("1.0.0"));
    }
}
