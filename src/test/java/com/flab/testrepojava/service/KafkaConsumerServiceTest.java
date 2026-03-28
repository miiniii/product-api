package com.flab.testrepojava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.domain.EventParticipationLog;
import com.flab.testrepojava.domain.Member;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import com.flab.testrepojava.email.EmailService;
import com.flab.testrepojava.exception.OutOfStockException;
import com.flab.testrepojava.kafka.KafkaConsumerService;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import com.flab.testrepojava.repository.EventParticipationLogRepository;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("consume - Kafka 메시지 소비에 성공하면 참여 로그를 저장하고 이메일을 전송한다")
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
    @DisplayName("retry - 비즈니스 예외가 발생하면 추가 작업 없이 종료한다")
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
    @DisplayName("retry - 시스템 예외가 발생하면 retryCount를 증가시키고 DLQ를 다시 저장한다")
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

    @Test
    @DisplayName("consume - 비즈니스 예외가 발생하면 예외를 다시 던지지 않고 종료한다")
    void consume_withBusinessException_shouldNotThrow() throws Exception {
        // given
        String message = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 1L, "test@test.com", "event-123");

        given(objectMapper.readValue(eq(message), eq(StockDecreaseEvent.class))).willReturn(event);
        given(memberService.findByEmailOrThrow("test@test.com"))
                .willThrow(new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // when
        kafkaConsumerService.consume(message);

        // then
        verify(productService, never()).decreaseQuantityWithPessimisticLock(anyLong(), anyInt());
        verify(eventParticipationLogRepository, never()).save(any());
        verify(emailService, never()).sendParticipationSuccess(anyLong(), anyLong());
    }

    @Test
    @DisplayName("consume - 시스템 예외가 발생하면 RuntimeException으로 다시 던진다")
    void consume_withSystemException_shouldThrowRuntimeException() throws Exception {
        // given
        String message = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 1L, "test@test.com", "event-123");
        Member member = Member.builder().id(1L).email("test@test.com").build();

        given(objectMapper.readValue(eq(message), eq(StockDecreaseEvent.class))).willReturn(event);
        given(memberService.findByEmailOrThrow("test@test.com")).willReturn(member);
        doThrow(new RuntimeException("DB 장애"))
                .when(productService).decreaseQuantityWithPessimisticLock(1L, 1);

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> kafkaConsumerService.consume(message));

        verify(productService).decreaseQuantityWithPessimisticLock(1L, 1);
        verify(eventParticipationLogRepository, never()).save(any());
        verify(emailService, never()).sendParticipationSuccess(anyLong(), anyLong());
    }

    @Test
    @DisplayName("consumeDLT - JSON 파싱에 실패하면 아무 작업 없이 종료한다")
    void consumeDLT_withInvalidJson_shouldReturn() throws Exception {
        // given
        String invalidMessage = "not-json";

        given(objectMapper.readValue(eq(invalidMessage), eq(StockDecreaseEvent.class)))
                .willThrow(new RuntimeException("json parse fail"));

        // when
        kafkaConsumerService.consumeDLT(invalidMessage);

        // then
        verify(deadLetterSuccessLogRepository, never()).existsByEventId(anyString());
        verify(deadLetterLogRepository, never()).save(any());
        verify(deadLetterSuccessLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("consumeDLT - 이미 성공 로그가 존재하면 DLQ 처리를 건너뛴다")
    void consumeDLT_whenAlreadySucceeded_shouldSkip() throws Exception {
        // given
        String message = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 1L, "test@test.com", "event-123");

        given(objectMapper.readValue(eq(message), eq(StockDecreaseEvent.class))).willReturn(event);
        given(objectMapper.writeValueAsString(event)).willReturn(message);
        given(deadLetterSuccessLogRepository.existsByEventId("event-123")).willReturn(true);

        // when
        kafkaConsumerService.consumeDLT(message);

        // then
        verify(deadLetterSuccessLogRepository).existsByEventId("event-123");
        verify(deadLetterLogRepository, never()).findByEventId(anyString());
        verify(deadLetterLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("consumeDLT - 기존 DLQ가 없으면 새로 저장하고 재처리 성공 시 성공 로그를 남기고 DLQ를 삭제한다")
    void consumeDLT_withNewDlq_shouldSaveAndDeleteOnSuccess() throws Exception {
        // given
        String message = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 1L, "test@test.com", "event-123");
        Member member = Member.builder().id(1L).email("test@test.com").build();

        given(objectMapper.readValue(eq(message), eq(StockDecreaseEvent.class))).willReturn(event);
        given(objectMapper.writeValueAsString(event)).willReturn(message);
        given(deadLetterSuccessLogRepository.existsByEventId("event-123")).willReturn(false);
        given(deadLetterLogRepository.findByEventId("event-123")).willReturn(Optional.empty());
        given(memberService.findByEmailOrThrow("test@test.com")).willReturn(member);

        // when
        kafkaConsumerService.consumeDLT(message);

        // then
        verify(deadLetterLogRepository).save(argThat(dlq ->
                dlq.getEventId().equals("event-123")
                        && dlq.getRetryCount() == 1
                        && dlq.getTopic().equals("stock-decrease")
        ));

        verify(productService).decreaseQuantityWithPessimisticLock(1L, 1);
        verify(eventParticipationLogRepository).save(any(EventParticipationLog.class));
        verify(emailService).sendParticipationSuccess(1L, 1L);
        verify(deadLetterSuccessLogRepository).save(any());
        verify(deadLetterLogRepository).deleteByEventId("event-123");
    }

    @Test
    @DisplayName("consumeDLT - 기존 DLQ가 있으면 retryCount를 증가시키고 재처리를 수행한다")
    void consumeDLT_withExistingDlq_shouldIncreaseRetryCount() throws Exception {
        // given
        String message = "{ \"productId\": 1, \"userId\": 1, \"memberEmail\": \"test@test.com\", \"eventId\": \"event-123\" }";
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 1L, "test@test.com", "event-123");
        Member member = Member.builder().id(1L).email("test@test.com").build();

        DeadLetterLog existing = DeadLetterLog.builder()
                .id(1L)
                .eventId("event-123")
                .payload(message)
                .topic("stock-decrease")
                .retryCount(1)
                .lastRetryAt(LocalDateTime.now().minusMinutes(5))
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();

        given(objectMapper.readValue(eq(message), eq(StockDecreaseEvent.class))).willReturn(event);
        given(objectMapper.writeValueAsString(event)).willReturn(message);
        given(deadLetterSuccessLogRepository.existsByEventId("event-123")).willReturn(false);
        given(deadLetterLogRepository.findByEventId("event-123")).willReturn(Optional.of(existing));
        given(memberService.findByEmailOrThrow("test@test.com")).willReturn(member);

        // when
        kafkaConsumerService.consumeDLT(message);

        // then
        verify(deadLetterLogRepository).save(argThat(dlq ->
                dlq.getEventId().equals("event-123")
                        && dlq.getRetryCount() == 2
                        && dlq.getLastRetryAt() != null
        ));
        verify(deadLetterLogRepository).deleteByEventId("event-123");
    }



}
