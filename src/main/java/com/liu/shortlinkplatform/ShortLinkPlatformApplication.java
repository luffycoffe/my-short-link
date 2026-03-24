package com.liu.shortlinkplatform;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync//启动异步任务
@MapperScan("com.liu.shortlinkplatform.mapper")
@SpringBootApplication
public class ShortLinkPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkPlatformApplication.class, args);
    }

}
