package com.example.ticket;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 售后工单系统启动类。
 *
 * @author ticket-system
 */
@SpringBootApplication
@MapperScan("com.example.ticket.mapper")
@EnableAsync
@EnableScheduling
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
