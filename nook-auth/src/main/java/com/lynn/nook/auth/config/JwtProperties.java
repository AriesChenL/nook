package com.lynn.nook.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "nook.jwt")
public class JwtProperties {

    private String secret;
    private long expireMinutes = 1440;
}
