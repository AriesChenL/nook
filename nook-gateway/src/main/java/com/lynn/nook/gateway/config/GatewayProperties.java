package com.lynn.nook.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "nook")
public class GatewayProperties {

    private Jwt jwt = new Jwt();
    private Gateway gateway = new Gateway();

    @Data
    public static class Jwt {
        private String secret;
    }

    @Data
    public static class Gateway {
        private List<String> whitelist = new ArrayList<>();
    }
}
