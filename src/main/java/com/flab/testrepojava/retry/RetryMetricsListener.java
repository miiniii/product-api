package com.flab.testrepojava.retry;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryMetricsListener implements RetryListener {

    private final MeterRegistry meterRegistry;

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        // 첫 재시도 전에 호출 (최초 1회)
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 재시도 후 최종적으로 호출 (성공 또는 실패 상관없이)
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // 매 retry attempt 마다 호출됨
        String exceptionName = throwable.getClass().getSimpleName();
        log.warn("Retry Attempt - count: {}, exception: {}", context.getRetryCount(), exceptionName);
        meterRegistry.counter("retry_attempts_total", "exception", exceptionName).increment();
    }
}
