package com.flab.testrepojava.retry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

class RetryMetricsListenerTest {

    private MeterRegistry meterRegistry;
    private Counter counter;
    private RetryMetricsListener retryMetricsListener;

    @BeforeEach
    void setUp() {
        meterRegistry = mock(MeterRegistry.class);
        counter = mock(Counter.class);
        retryMetricsListener = new RetryMetricsListener(meterRegistry);
    }

    @Test
    @DisplayName("open - 항상 true를 반환한다")
    void open_shouldReturnTrue() {
        // given
        RetryContext context = mock(RetryContext.class);
        @SuppressWarnings("unchecked")
        RetryCallback<Object, Throwable> callback = mock(RetryCallback.class);

        // when
        boolean result = retryMetricsListener.open(context, callback);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("close - 예외 없이 호출된다")
    void close_shouldDoNothing() {
        // given
        RetryContext context = mock(RetryContext.class);
        @SuppressWarnings("unchecked")
        RetryCallback<Object, Throwable> callback = mock(RetryCallback.class);

        // when
        retryMetricsListener.close(context, callback, null);

        // then
        // close는 별도 동작이 없으므로 예외 없이 호출되면 성공
        verifyNoInteractions(meterRegistry);
    }

    @Test
    @DisplayName("onError - 예외 이름으로 retry_attempts_total 카운터를 증가시킨다")
    void onError_shouldIncrementCounter() {
        // given
        RetryContext context = mock(RetryContext.class);
        @SuppressWarnings("unchecked")
        RetryCallback<Object, Throwable> callback = mock(RetryCallback.class);
        RuntimeException throwable = new RuntimeException("retry fail");

        when(context.getRetryCount()).thenReturn(2);
        when(meterRegistry.counter("retry_attempts_total", "exception", "RuntimeException"))
                .thenReturn(counter);

        // when
        retryMetricsListener.onError(context, callback, throwable);

        // then
        verify(meterRegistry).counter("retry_attempts_total", "exception", "RuntimeException");
        verify(counter).increment();
    }
}