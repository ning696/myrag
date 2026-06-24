package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mail")
public class MailProperties {
    private String host;
    private int port = 465;
    private String username;
    private String password;
    private String from;
    private boolean sslEnable = true;
    private String sslProtocols = "TLSv1.2";
    private boolean debug = false;
    private int connectionTimeout = 10000;
    private int timeout = 10000;
    private int writeTimeout = 10000;
}
