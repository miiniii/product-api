package com.flab.testrepojava.metrics;


import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisLockMetricsCollector {

    private final MeterRegistry meterRegistry;

    public void incrementAcquireAttempt(String key) {
        meterRegistry.counter("redis_lock_acquire_total", "key", key).increment();
    }

    public void incrementSuccess(String key) {
        meterRegistry.counter("redis_lock_success_total", "key", key).increment();
    }

    public void incrementFail(String key) {
        meterRegistry.counter("redis_lock_fail_total", "key", key).increment();
    }

    public void recordLockDuration(String key, long durationMillis) {
        Timer.builder("redis_lock_duration_seconds")
                .description("Duration the Redis lock is held")
                .tags("key",key)
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
