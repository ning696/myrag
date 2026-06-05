package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {
    private String provider = "qweather";
    private String apiKey;
    private String apiHost;
    private String endpoint = "https://devapi.qweather.com/v7";
    private int timeout = 5000;
}
