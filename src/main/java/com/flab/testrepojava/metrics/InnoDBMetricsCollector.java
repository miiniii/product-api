package com.flab.testrepojava.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InnoDBMetricsCollector {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private volatile double currentRowLockWaits = 0;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("innodb_row_lock_waits", this, InnoDBMetricsCollector::getRowLockWaits);
    }

    @Scheduled(fixedRate = 10000) // 10초마다 갱신
    public void updateRowLockWaits() {
        String sql = "SHOW ENGINE INNODB STATUS";
        String statusText = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getString("Status"));

        if (statusText != null) {
            String[] lines = statusText.split("\n");
            for (String line : lines) {
                if (line.contains("row lock waits")) {
                    String[] parts = line.trim().split(" ");
                    try {
                        currentRowLockWaits = Double.parseDouble(parts[0]);
                    } catch (NumberFormatException ignored) {}
                    break;
                }
            }
        }
    }

    public double getRowLockWaits() {
        return currentRowLockWaits;
    }
}

