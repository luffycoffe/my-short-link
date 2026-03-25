package com.liu.shortlinkplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {
    @Bean
    public OpenAPI openApi() {
       return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("短链接服务")
                        .description("短链接服务")
                        .version("1.0")
                        .contact(new Contact()
                                .name("liu")
                                .email("3149742103@qq.com"))
                );

    }
}
