package com.anchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;

@SpringBootApplication
@EnableScheduling
public class AnChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnChatApplication.class, args);
    }
    /**
     * 功能描述
     * 添加静态资源md5版本控制
     */
    @Bean
    public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new ResourceUrlEncodingFilter();
    }

}
