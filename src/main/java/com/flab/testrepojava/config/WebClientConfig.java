package com.flab.testrepojava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

// WebClient Bean은 Config 클래스에 있어야 함
@Component
public class WebClientConfig {
    @Bean
    public WebClient slackWebClient() {
        return WebClient.builder().build();
    }
}
