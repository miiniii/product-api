package com.flab.testrepojava.config;

import com.flab.testrepojava.retry.RetryMetricsListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate retryTemplate(RetryMetricsListener listener) {
        RetryTemplate template = new RetryTemplate();
        template.registerListener(listener);
        return template;
    }

}
