package com.flab.testrepojava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.domain.DeadLetterSuccessLog;
import com.flab.testrepojava.domain.Member;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import com.flab.testrepojava.metrics.DLQMetricsService;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaDLQService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DeadLetterLogRepository deadLetterLogRepository;
    private final DeadLetterSuccessLogRepository deadLetterSuccessLogRepository;
    private final DLQMetricsService dlqMetricsService;
    private final ObjectMapper objectMapper;


    public ResponseEntity<String> retrySingleMessage(String message) {
        try {
            kafkaTemplate.send("stock-decrease", message).get(); // 동기 전송
            return ResponseEntity.ok("DLQ 메시지를 재전송했습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("재전송 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @Transactional
    public DLQRetryResult retryAllDLQMessagesInternal() {
        List<DeadLetterLog> messages = deadLetterLogRepository.findAll();
        int successCount = 0;
        int failureCount = 0;

        for (DeadLetterLog entry : messages) {
            if (entry.getRetryCount() >= 5) {
                log.warn("최대 재시도 초과로 메시지 폐기: {}", entry.getPayload());
                deadLetterLogRepository.delete(entry);
                continue;
            }

            if (!isBackoffTimeReached(entry)) {
                log.debug("아직 backoff 시간 도달 안됨 - eventId={}", entry.getEventId());
                continue;
            }

            try {
                kafkaTemplate.send("stock-decrease", entry.getPayload()).get();
                dlqMetricsService.incrementSuccess();

                StockDecreaseEvent parsed = parseEvent(entry.getPayload());
                deadLetterSuccessLogRepository.save(
                        DeadLetterSuccessLog.builder()
                                .eventId(parsed.getEventId())
                                .topic(entry.getTopic())
                                .payload(entry.getPayload())
                                .build()
                );

                deadLetterLogRepository.delete(entry);
                successCount++;

                // Slack rate limit 대응
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("DLQ 재처리 실패 - eventId={}", entry.getEventId(), e);
                dlqMetricsService.incrementFailure();

                entry.setRetryCount(entry.getRetryCount() + 1);
                entry.setLastRetryAt(LocalDateTime.now());
                deadLetterLogRepository.save(entry);
                failureCount++;
            }
        }

        return new DLQRetryResult(successCount, failureCount);
    }


    // 컨트롤러 호출용
    public String retryAllDLQMessagesWithResult() {
        DLQRetryResult result = retryAllDLQMessagesInternal();
        return "DLQ 전체 재처리 완료 - 성공: " + result.successCount() + "개, 실패: " + result.failureCount() + "개";
    }

    @Transactional
    public void retry(Long id) {
        DeadLetterLog dlq = deadLetterLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DLQ 로그가 존재하지 않습니다."));

        try {
            // Kafka로 DLQ 메시지 재전송 (성공/실패 판단은 Consumer가 함)
            kafkaTemplate.send(dlq.getTopic(), dlq.getPayload()).get();
            log.info("Kafka로 DLQ 메시지 재전송 성공 - eventId={}, retryCount={}", dlq.getEventId(), dlq.getRetryCount());

        } catch (Exception e) {
            // Kafka 전송 자체가 실패한 경우만 처리
            log.error("Kafka 재전송 실패 - eventId={}, 예외: {}", dlq.getEventId(), e.getMessage());

            // 재전송 자체가 실패하면 retryCount 증가 (이건 예외적인 상황)
            dlq.setRetryCount(dlq.getRetryCount() + 1);
            dlq.setLastRetryAt(LocalDateTime.now());
            deadLetterLogRepository.save(dlq);
        }
    }


    private boolean isBackoffTimeReached(DeadLetterLog entry) {
        long secondsSinceLastTry = Duration.between(entry.getLastRetryAt(), LocalDateTime.now()).getSeconds();
        long requiredDelay = (long) Math.pow(2, entry.getRetryCount());
        return secondsSinceLastTry >= requiredDelay;
    }

    private StockDecreaseEvent parseEvent(String payload) throws Exception {
        return objectMapper.readValue(payload, StockDecreaseEvent.class);
    }



    public record DLQRetryResult(int successCount, int failureCount) {}
}