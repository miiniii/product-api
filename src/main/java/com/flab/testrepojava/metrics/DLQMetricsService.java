package com.flab.testrepojava.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DLQMetricsService {

    private final MeterRegistry meterRegistry;

    private Counter retrySuccessCounter;
    private Counter retryFailureCounter;

    @PostConstruct
    public void init() {
        retrySuccessCounter = Counter.builder("dead_letter_retry_success_total")
                .description("DLQ 재처리 성공 횟수")
                .register(meterRegistry);

        retryFailureCounter = Counter.builder("dead_letter_retry_failure_total")
                .description("DLQ 재처리 실패 횟수")
                .register(meterRegistry);
    }

    public void incrementSuccess() {
        retrySuccessCounter.increment();
    }

    public void incrementFailure() {
        retryFailureCounter.increment();
    }
}

