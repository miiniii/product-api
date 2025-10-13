package com.flab.testrepojava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.domain.DeadLetterSuccessLog;
import com.flab.testrepojava.domain.EventParticipationLog;
import com.flab.testrepojava.domain.Member;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import com.flab.testrepojava.email.EmailService;
import com.flab.testrepojava.exception.OutOfStockException;
import com.flab.testrepojava.kafka.KafkaConsumerService;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import com.flab.testrepojava.repository.EventParticipationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.net.ConnectException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaConsumerServiceTest {

    @InjectMocks
    private KafkaConsumerService kafkaConsumerService;

    @InjectMocks
    private KafkaDLQService kafkaDLQService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    @Mock
    private EventParticipationLogRepository eventParticipationLogRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private DeadLetterLogRepository deadLetterLogRepository;

    @Mock
    private DeadLetterSuccessLogRepository deadLetterSuccessLogRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    //Kafka 메시지 소비 → DB 저장 → 이메일 전송 흐름 검증
    void consume_success_shouldSaveLogAndSendEmail() throws Exception {
        // given
        String message = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 1L, "test@test.com", "event-123");
        Member member = Member.builder().id(1L).email("test@test.com").build();

        given(objectMapper.readValue(eq(message), eq(StockDecreaseEvent.class))).willReturn(event);
        given(memberService.findByEmailOrThrow("test@test.com")).willReturn(member);

        // when
        kafkaConsumerService.consume(message);

        // then
        verify(productService).decreaseQuantityWithPessimisticLock(1L, 1);
        verify(eventParticipationLogRepository).save(any(EventParticipationLog.class));
        verify(emailService).sendParticipationSuccess(1L, 1L);
    }


    @Test
    //비즈니스 예외 발생 시 아무 작업도 하지 않음
    void retry_withBusinessException_shouldDoNothing() throws Exception {
        // given
        Long id = 1L;
        String payload = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";

        DeadLetterLog dlq = DeadLetterLog.builder()
                .id(id)
                .eventId("event-123")
                .payload(payload)
                .topic("stock-decrease")
                .retryCount(1)
                .build();

        given(deadLetterLogRepository.findById(id)).willReturn(Optional.of(dlq));

        // ObjectMapper가 readValue 호출 시 BusinessException 던지도록 설정
        given(objectMapper.readValue(eq(payload), eq(StockDecreaseEvent.class)))
                .willThrow(new OutOfStockException("재고 부족"));  // 또는 new BusinessException("비즈니스 예외")

        // when
        kafkaDLQService.retry(id);

        // then
        verify(deadLetterLogRepository, never()).delete(any());
        verify(deadLetterSuccessLogRepository, never()).save(any());
    }

    @Test
    void retry_withSystemException_shouldIncrementRetryCountAndSaveAgain() throws Exception {
        // given
        Long id = 1L;
        String payload = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";

        DeadLetterLog dlq = DeadLetterLog.builder()
                .id(id)
                .eventId("event-123")
                .payload(payload)
                .topic("stock-decrease")
                .retryCount(3)
                .lastRetryAt(LocalDateTime.now().minusMinutes(5))
                .build();

        // DLQ 조회 성공
        given(deadLetterLogRepository.findById(id)).willReturn(Optional.of(dlq));

        // JSON 파싱 성공
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 1L, "test@test.com", "event-123");
        given(objectMapper.readValue(payload, StockDecreaseEvent.class)).willReturn(event);
        given(objectMapper.writeValueAsString(event)).willReturn(payload);

        // Kafka 전송 실패 (ConnectException)
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new ConnectException("연결 실패"));
        given(kafkaTemplate.send("stock-decrease", payload)).willReturn(future);

        // when
        kafkaDLQService.retry(id);

        // then
        assertEquals(4, dlq.getRetryCount()); // 3 → 4
        assertTrue(Duration.between(dlq.getLastRetryAt(), LocalDateTime.now()).getSeconds() < 10);
        verify(deadLetterLogRepository).save(dlq);
    }



}
