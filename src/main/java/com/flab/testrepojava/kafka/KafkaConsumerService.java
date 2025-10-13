package com.flab.testrepojava.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.domain.DeadLetterSuccessLog;
import com.flab.testrepojava.domain.EventParticipationLog;
import com.flab.testrepojava.domain.Member;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import com.flab.testrepojava.email.EmailService;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import com.flab.testrepojava.repository.EventParticipationLogRepository;
import com.flab.testrepojava.service.MemberService;
import com.flab.testrepojava.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.flab.testrepojava.exception.DLQExceptionFilter.isBusinessException;


@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ProductService productService;
    private final EmailService emailService;
    private final MemberService memberService;
    private final EventParticipationLogRepository eventParticipationLogRepository;
    private final DeadLetterLogRepository deadLetterLogRepository;
    private final DeadLetterSuccessLogRepository deadLetterSuccessLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock-decrease", groupId = "product-consumer-group")
    public void consume(String message) {
        try {
            StockDecreaseEvent event = objectMapper.readValue(message, StockDecreaseEvent.class);

            validateEvent(event);
            Member member = memberService.findByEmailOrThrow(event.getMemberEmail());
            productService.decreaseQuantityWithPessimisticLock(event.getProductId(), 1);

            eventParticipationLogRepository.save(EventParticipationLog.builder()
                    .eventId(event.getEventId())
                    .productId(event.getProductId())
                    .userId(member.getId())
                    .status("SUCCESS")
                    .build());

            emailService.sendParticipationSuccess(member.getId(), event.getProductId());

            // DLQ에 해당 메시지가 존재하면, 성공 로그로 이동
            deadLetterLogRepository.findByEventId(event.getEventId()).ifPresent(dlq -> {
                deadLetterSuccessLogRepository.save(DeadLetterSuccessLog.builder()
                        .eventId(dlq.getEventId())
                        .payload(dlq.getPayload())
                        .topic(dlq.getTopic())
                        .successAt(LocalDateTime.now())
                        .build());

                deadLetterLogRepository.delete(dlq); // DLQ에서 삭제
                log.info("DLQ 메시지 재처리 성공 → DLQ 제거 및 성공 로그 저장 완료 - eventId={}", dlq.getEventId());
            });

        } catch (IllegalArgumentException e) {
            log.warn("비즈니스 예외 발생 → 재처리 없이 폐기: {}", e.getMessage());
        } catch (Exception e) {
            log.error("시스템 예외 발생, Kafka가 재시도 후 실패 시 DLT로 전송됨: {}", e.getMessage());
            throw new RuntimeException(e); // Kafka에게 넘긴다
        }
    }


    @Transactional
    @KafkaListener(topics = "stock-decrease.DLT", groupId = "product-consumer-group")
    public void consumeDLT(String message) {
        StockDecreaseEvent event;
        try {
            event = objectMapper.readValue(message, StockDecreaseEvent.class);
            log.warn("DLT 메시지 수신: {}", message);
        } catch (Exception e) {
            log.warn("DLT 메시지 파싱 실패 → 메시지 폐기");
            return;
        }

        final String eventId = event.getEventId();
        final String jsonPayload = convertToJson(event);

        // 이미 재처리 성공한 경우 → DLQ 처리 스킵
        if (deadLetterSuccessLogRepository.existsByEventId(eventId)) {
            log.warn("이미 재처리 성공한 eventId={} → DLQ 처리 스킵", eventId);
            return;
        }

        // DLQ 저장 or 갱신
        DeadLetterLog dlq = deadLetterLogRepository.findByEventId(eventId)
                .map(existing -> {
                    existing.setRetryCount(existing.getRetryCount() + 1);
                    existing.setLastRetryAt(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> DeadLetterLog.builder()
                        .eventId(eventId)
                        .topic("stock-decrease")
                        .payload(jsonPayload)
                        .reason("DLT 최초 수신")
                        .retryCount(1)
                        .lastRetryAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .build());

        deadLetterLogRepository.save(dlq);

        // 재처리 시도
        try {
            processRetry(event);

            // 성공 → SuccessLog 저장 + DLQ 삭제
            deadLetterSuccessLogRepository.save(
                    DeadLetterSuccessLog.builder()
                            .eventId(eventId)
                            .topic("stock-decrease")
                            .payload(jsonPayload)
                            .build()
            );
            deadLetterLogRepository.deleteByEventId(eventId);
            log.info("DLT 재처리 성공 - DLQ 삭제 완료");

        } catch (Exception e) {
            if (isBusinessException(e)) {
                log.warn("비즈니스 예외로 재처리 중단 - eventId={}: {}", eventId, e.getMessage());
            } else {
                // StackTrace 제거: 핵심 메시지만 출력
                log.warn("시스템 예외로 재처리 실패 - eventId={}, 예외: {} - {}",
                        eventId, e.getClass().getSimpleName(), e.getMessage());
            }
            // Kafka 재시도 방지 → 예외 throw 하지 않음
        }
    }



    private void processRetry(StockDecreaseEvent event) throws Exception {
        validateEvent(event);

        Member member = memberService.findByEmailOrThrow(event.getMemberEmail());
        productService.decreaseQuantityWithPessimisticLock(event.getProductId(), 1);

        eventParticipationLogRepository.save(EventParticipationLog.builder()
                .eventId(event.getEventId())
                .productId(event.getProductId())
                .userId(member.getId())
                .status("RETRY_SUCCESS")
                .build());

        emailService.sendParticipationSuccess(member.getId(), event.getProductId());
    }

    private String convertToJson(StockDecreaseEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("DLT 이벤트 JSON 변환 실패: {}", e.getMessage());
            return "{}";
        }
    }

    private void validateEvent(StockDecreaseEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId 누락");
        }
        if (event.getMemberEmail() == null || event.getMemberEmail().isBlank()) {
            throw new IllegalArgumentException("memberEmail 누락");
        }
        if (event.getProductId() == null) {
            throw new IllegalArgumentException("productId 누락");
        }
    }
}
