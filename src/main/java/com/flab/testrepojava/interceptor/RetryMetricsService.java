package com.flab.testrepojava.interceptor;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RetryMetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * 재시도 횟수 측정 (모든 재시도 attempt 시 호출)
     */
    public void countRetryAttempt(Exception e) {
        log.info("[METRICS] 재시도 발생 - 예외: {}", e.getClass().getSimpleName());

        meterRegistry.counter("retry_attempts_total",
                "exception", e.getClass().getSimpleName()
        ).increment();
    }

}
