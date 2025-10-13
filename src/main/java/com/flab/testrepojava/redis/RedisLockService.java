package com.flab.testrepojava.redis;

import com.flab.testrepojava.metrics.RedisLockMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final RedissonClient redissonClient;
    private final RedisLockMetricsCollector metricsCollector;
    private final StringRedisTemplate redisTemplate;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MILLIS = 100;

    public <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> logic) {
        metricsCollector.incrementAcquireAttempt(key);
        RLock lock = redissonClient.getLock(key);

        int attempts = 0;
        boolean acquired = false;
        long start = System.currentTimeMillis();

        while (attempts < MAX_RETRIES) {
            try {
                acquired = lock.tryLock(waitTime, leaseTime, unit);
                if (acquired) {
                    metricsCollector.incrementSuccess(key);
                    return logic.get();
                }

                attempts++;
                log.warn("[RedisLock] Lock attempt {} failed for key: {}. Retrying...", attempts, key);
                Thread.sleep(RETRY_BACKOFF_MILLIS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("락 시도 중 인터럽트 발생", e);
            }
        }

        metricsCollector.incrementFail(key);
        throw new IllegalStateException("Redis 락 획득 실패 (재시도 " + MAX_RETRIES + "회)");
    }

    public void releaseLock(RLock lock, String key, long startTimeMillis) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                long duration = System.currentTimeMillis() - startTimeMillis;
                metricsCollector.recordLockDuration(key, duration);
            }
        } catch (Exception e) {
            log.error("[RedisLock] 락 해제 중 예외 발생 - key: {}", key, e);
        }
    }

    // 선착순 이벤트에서 유저 중복 참여를 막음
    public boolean tryAcquire(Long productId, Long userId) {
        String key = "event:" + productId + ":user:" + userId;
        return redisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.MINUTES);
    }

    // 이미 참여했는지 확인
    public boolean isAlreadyParticipated(Long productId, Long userId) {
        String key = "event:" + productId + ":user:" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // 중복 방지용 참여 마킹
    public void markParticipated(Long productId, Long userId) {
        String key = "event:" + productId + ":user:" + userId;
        redisTemplate.opsForValue().set(key, "1", 1, TimeUnit.HOURS); //1시간 후 자동 만료
    }

    // 현재 참여 인원 수가 제한 소과인지 확인
    public boolean isSoldOut(Long productId, int limit) {
        String key = "event:" + productId + ":participants";
        String count = redisTemplate.opsForValue().get(key);
        int current = count == null ? 0 : Integer.parseInt(count);
        return current >= limit;
    }

    // 참여 인원 수 증가
    public void increaseParticipantCount(Long productId) {
        String key = "event:" + productId + ":participants";
        redisTemplate.opsForValue().increment(key);
    }

    // 성공 유저 저장
    public void saveSuccessfulParticipant(Long productId, Long userId) {
        String key = "event:" + productId + ":success-users";
        redisTemplate.opsForSet().add(key, String.valueOf(userId));
    }

    // 성공 유저 목록 조회
    public Set<String> getSuccessfulParticipants(Long productId) {
        String key = "event:" + productId + ":success-users";
        return redisTemplate.opsForSet().members(key);
    }
}

