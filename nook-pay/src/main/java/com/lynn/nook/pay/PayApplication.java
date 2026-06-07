package com.lynn.nook.pay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

import com.lynn.nook.pay.config.StripeProperties;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(StripeProperties.class)
@MapperScan("com.lynn.nook.pay.mapper")
@ComponentScan(basePackages = {"com.lynn.nook.pay", "com.lynn.nook.common"})
public class PayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayApplication.class, args);
    }
}
