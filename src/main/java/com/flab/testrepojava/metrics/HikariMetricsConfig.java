package com.flab.testrepojava.metrics;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class HikariMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final HikariDataSource hikariDataSource;

    @PostConstruct
    public void bindMetrics() {
        hikariDataSource.setMetricRegistry(meterRegistry);
    }
}
