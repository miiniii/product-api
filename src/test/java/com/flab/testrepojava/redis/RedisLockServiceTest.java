package com.flab.testrepojava.redis;

import com.flab.testrepojava.metrics.RedisLockMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RedisLockServiceTest {

    private RedissonClient redissonClient;
    private RedisLockMetricsCollector metricsCollector;
    private StringRedisTemplate redisTemplate;

    private RLock lock;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;

    private RedisLockService redisLockService;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        metricsCollector = mock(RedisLockMetricsCollector.class);
        redisTemplate = mock(StringRedisTemplate.class);

        lock = mock(RLock.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);

        redisLockService = new RedisLockService(redissonClient, metricsCollector, redisTemplate);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("executeWithLock - 락 획득 성공 시 로직 실행 후 결과 반환")
    void executeWithLock_success() throws Exception {
        // given
        String key = "lock:product:1";
        when(redissonClient.getLock(key)).thenReturn(lock);
        when(lock.tryLock(1L, 3L, TimeUnit.SECONDS)).thenReturn(true);

        Supplier<String> logic = mock(Supplier.class);
        when(logic.get()).thenReturn("success");

        // when
        String result = redisLockService.executeWithLock(key, 1L, 3L, TimeUnit.SECONDS, logic);

        // then
        assertThat(result).isEqualTo("success");
        verify(metricsCollector).incrementAcquireAttempt(key);
        verify(metricsCollector).incrementSuccess(key);
        verify(metricsCollector, never()).incrementFail(key);
        verify(logic).get();
    }

    @Test
    @DisplayName("executeWithLock - 재시도 3회 모두 실패하면 예외 발생")
    void executeWithLock_failAfterMaxRetries() throws Exception {
        // given
        String key = "lock:product:1";
        when(redissonClient.getLock(key)).thenReturn(lock);
        when(lock.tryLock(1L, 3L, TimeUnit.SECONDS))
                .thenReturn(false, false, false);

        Supplier<String> logic = mock(Supplier.class);

        // when & then
        assertThatThrownBy(() -> redisLockService.executeWithLock(key, 1L, 3L, TimeUnit.SECONDS, logic))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis 락 획득 실패");

        verify(metricsCollector).incrementAcquireAttempt(key);
        verify(metricsCollector).incrementFail(key);
        verify(metricsCollector, never()).incrementSuccess(key);
        verify(logic, never()).get();
    }

    @Test
    @DisplayName("executeWithLock - tryLock 중 InterruptedException 발생 시 RuntimeException")
    void executeWithLock_interrupted() throws Exception {
        // given
        String key = "lock:product:1";
        when(redissonClient.getLock(key)).thenReturn(lock);
        when(lock.tryLock(1L, 3L, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        Supplier<String> logic = mock(Supplier.class);

        // when & then
        assertThatThrownBy(() -> redisLockService.executeWithLock(key, 1L, 3L, TimeUnit.SECONDS, logic))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("락 시도 중 인터럽트 발생");

        verify(metricsCollector).incrementAcquireAttempt(key);
        verify(metricsCollector, never()).incrementSuccess(key);
        verify(metricsCollector, never()).incrementFail(key);
        verify(logic, never()).get();
    }

    @Test
    @DisplayName("isAlreadyParticipated - Redis key가 있으면 true 반환")
    void isAlreadyParticipated_true() {
        // given
        Long productId = 1L;
        Long userId = 100L;
        String key = "event:1:user:100";

        when(redisTemplate.hasKey(key)).thenReturn(true);

        // when
        boolean result = redisLockService.isAlreadyParticipated(productId, userId);

        // then
        assertThat(result).isTrue();
        verify(redisTemplate).hasKey(key);
    }

    @Test
    @DisplayName("isAlreadyParticipated - Redis key가 없으면 false 반환")
    void isAlreadyParticipated_false() {
        // given
        Long productId = 1L;
        Long userId = 100L;
        String key = "event:1:user:100";

        when(redisTemplate.hasKey(key)).thenReturn(false);

        // when
        boolean result = redisLockService.isAlreadyParticipated(productId, userId);

        // then
        assertThat(result).isFalse();
        verify(redisTemplate).hasKey(key);
    }

    @Test
    @DisplayName("markParticipated - 참여 마킹 저장")
    void markParticipated_success() {
        // given
        Long productId = 1L;
        Long userId = 100L;
        String key = "event:1:user:100";

        // when
        redisLockService.markParticipated(productId, userId);

        // then
        verify(valueOperations).set(key, "1", 1, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("isSoldOut - 현재 참여 인원이 limit 이상이면 true 반환")
    void isSoldOut_true() {
        // given
        Long productId = 1L;
        int limit = 10;
        String key = "event:1:participants";

        when(valueOperations.get(key)).thenReturn("10");

        // when
        boolean result = redisLockService.isSoldOut(productId, limit);

        // then
        assertThat(result).isTrue();
        verify(valueOperations).get(key);
    }

    @Test
    @DisplayName("isSoldOut - 현재 참여 인원이 limit 미만이면 false 반환")
    void isSoldOut_false() {
        // given
        Long productId = 1L;
        int limit = 10;
        String key = "event:1:participants";

        when(valueOperations.get(key)).thenReturn("7");

        // when
        boolean result = redisLockService.isSoldOut(productId, limit);

        // then
        assertThat(result).isFalse();
        verify(valueOperations).get(key);
    }

    @Test
    @DisplayName("isSoldOut - 참여 인원 값이 없으면 0명으로 간주")
    void isSoldOut_whenCountIsNull() {
        // given
        Long productId = 1L;
        int limit = 10;
        String key = "event:1:participants";

        when(valueOperations.get(key)).thenReturn(null);

        // when
        boolean result = redisLockService.isSoldOut(productId, limit);

        // then
        assertThat(result).isFalse();
        verify(valueOperations).get(key);
    }

    @Test
    @DisplayName("increaseParticipantCount - 참여 인원 수 증가")
    void increaseParticipantCount_success() {
        // given
        Long productId = 1L;
        String key = "event:1:participants";

        // when
        redisLockService.increaseParticipantCount(productId);

        // then
        verify(valueOperations).increment(key);
    }

    @Test
    @DisplayName("saveSuccessfulParticipant - 성공 유저 저장")
    void saveSuccessfulParticipant_success() {
        // given
        Long productId = 1L;
        Long userId = 100L;
        String key = "event:1:success-users";

        // when
        redisLockService.saveSuccessfulParticipant(productId, userId);

        // then
        verify(setOperations).add(key, "100");
    }

    @Test
    @DisplayName("getSuccessfulParticipants - 성공 유저 목록 조회")
    void getSuccessfulParticipants_success() {
        // given
        Long productId = 1L;
        String key = "event:1:success-users";
        Set<String> users = Set.of("100", "101");

        when(setOperations.members(key)).thenReturn(users);

        // when
        Set<String> result = redisLockService.getSuccessfulParticipants(productId);

        // then
        assertThat(result).isEqualTo(users);
        verify(setOperations).members(key);
    }
}