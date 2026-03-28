package com.flab.testrepojava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.metrics.DLQMetricsService;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import com.flab.testrepojava.service.KafkaDLQService.DLQRetryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class KafkaDLQServiceTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private DeadLetterLogRepository deadLetterLogRepository;
    private DeadLetterSuccessLogRepository deadLetterSuccessLogRepository;
    private DLQMetricsService dlqMetricsService;
    private ObjectMapper objectMapper;

    private KafkaDLQService kafkaDLQService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        deadLetterLogRepository = mock(DeadLetterLogRepository.class);
        deadLetterSuccessLogRepository = mock(DeadLetterSuccessLogRepository.class);
        dlqMetricsService = mock(DLQMetricsService.class);
        objectMapper = new ObjectMapper();

        kafkaDLQService = new KafkaDLQService(kafkaTemplate, deadLetterLogRepository, deadLetterSuccessLogRepository, dlqMetricsService, objectMapper);
    }

    @Test
    @DisplayName("retrySingleMessage - Kafka 전송 성공 시 200 OK 반환")
    void retrySingleMessage_success() throws Exception {
        //given
        String message = "{\"eventId\":\"event-1\"}";

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);

        when(kafkaTemplate.send("stock-decrease", message)).thenReturn(future);
        when(future.get()).thenReturn(null);

        // when
        ResponseEntity<String> response = kafkaDLQService.retrySingleMessage(message);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("DLQ 메시지를 재전송했습니다.");
        verify(kafkaTemplate).send("stock-decrease", message);
    }

    @Test
    @DisplayName("retrySingleMessage - Kafka 전송 실패 시 500 반환")
    void retrySingleMessage_fail() throws Exception {
        // given
        String message = "{\"eventId\":\"event-1\"}";

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);

        when(kafkaTemplate.send("stock-decrease", message)).thenReturn(future);
        when(future.get()).thenThrow(new RuntimeException("kafka send fail"));

        // when
        ResponseEntity<String> response = kafkaDLQService.retrySingleMessage(message);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("재전송 실패");
        verify(kafkaTemplate).send("stock-decrease", message);
    }

    @Test
    @DisplayName("retry - DLQ 로그가 없으면 예외 발생")
    void retry_notFound() {
        // given
        Long id = 1L;
        when(deadLetterLogRepository.findById(id)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> kafkaDLQService.retry(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DLQ 로그가 존재하지 않습니다.");

        verify(deadLetterLogRepository).findById(id);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("retry - kafka 재전송 성공 시 save 호출하지 않음")
    void retry_success() throws Exception {
        //given
        Long id = 1L;

        DeadLetterLog dlq = DeadLetterLog.builder()
                .id(id)
                .eventId("event-1")
                .topic("stock-decrease")
                .payload("{\"eventId\":\"event-1\"}")
                .retryCount(2)
                .lastRetryAt(LocalDateTime.now().minusMinutes(10))
                .build();

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);

        when(deadLetterLogRepository.findById(id)).thenReturn(Optional.of(dlq));
        when(kafkaTemplate.send(dlq.getTopic(), dlq.getPayload())).thenReturn(future);
        when(future.get()).thenReturn(null);

        // when
        kafkaDLQService.retry(id);

        // then
        verify(deadLetterLogRepository).findById(id);
        verify(kafkaTemplate).send(dlq.getTopic(), dlq.getPayload());
        verify(deadLetterLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("retry - Kafka 재전송 실패 시 retryCount 증가 후 save")
    void retry_fail() throws Exception {
        // given
        Long id = 1L;

        DeadLetterLog dlq = DeadLetterLog.builder()
                .id(id)
                .eventId("event-1")
                .topic("stock-decrease")
                .payload("{\"eventId\":\"event-1\"}")
                .retryCount(2)
                .lastRetryAt(LocalDateTime.now().minusMinutes(10))
                .build();

        int beforeRetryCount = dlq.getRetryCount();

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);

        when(deadLetterLogRepository.findById(id)).thenReturn(Optional.of(dlq));
        when(kafkaTemplate.send(dlq.getTopic(), dlq.getPayload())).thenReturn(future);
        when(future.get()).thenThrow(new RuntimeException("send fail"));

        // when
        kafkaDLQService.retry(id);

        // then
        assertThat(dlq.getRetryCount()).isEqualTo(beforeRetryCount + 1);
        assertThat(dlq.getLastRetryAt()).isNotNull();

        verify(deadLetterLogRepository).findById(id);
        verify(kafkaTemplate).send(dlq.getTopic(), dlq.getPayload());
        verify(deadLetterLogRepository).save(dlq);
    }

    @Test
    @DisplayName("retryAllDLQMessagesInternal - retryCount가 5이상이면 삭제하고 폐기")
    void retryAllDLQMessagesInternal_deleteWhenRetryExceeded() {
        // given
        DeadLetterLog entry = DeadLetterLog.builder()
                .id(1L)
                .eventId("event-1")
                .topic("stock-decrease")
                .payload("{\"eventId\":\"event-1\"}")
                .retryCount(5)
                .lastRetryAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(deadLetterLogRepository.findAll()).thenReturn(List.of(entry));

        // then
        DLQRetryResult result = kafkaDLQService.retryAllDLQMessagesInternal();

        // then
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failureCount()).isEqualTo(0);

        verify(deadLetterLogRepository).findAll();
        verify(deadLetterLogRepository).delete(entry);
        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(dlqMetricsService);
        verifyNoInteractions(deadLetterSuccessLogRepository);
    }

    @Test
    @DisplayName("retryAllDLQMessagesInternal - backoff 시간이 아직 안 됐으면 건너뜀")
    void retryAllDLQMessagesInternal_skipWhenBackoffNotReached() {
        // given
        DeadLetterLog entry = DeadLetterLog.builder()
                .id(1L)
                .eventId("event-1")
                .topic("stock-decrease")
                .payload("{\"eventId\":\"event-1\"}")
                .retryCount(3) // requiredDelay = 2^3 = 8초
                .lastRetryAt(LocalDateTime.now()) // 방금 시도한 상태
                .build();

        when(deadLetterLogRepository.findAll()).thenReturn(List.of(entry));

        // when
        DLQRetryResult result = kafkaDLQService.retryAllDLQMessagesInternal();

        // then
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failureCount()).isEqualTo(0);

        verify(deadLetterLogRepository).findAll();
        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(dlqMetricsService);
        verify(deadLetterLogRepository, never()).save(any());
        verify(deadLetterLogRepository, never()).delete(any());
        verifyNoInteractions(deadLetterSuccessLogRepository);
    }

    @Test
    @DisplayName("retryAllDLQMessagesInternal - Kafka 재전송 성공 시 success log 저장 후 원본 삭제")
    void retryAllDLQMessagesInternal_success() throws Exception {
        // given
        String payload = "{\"eventId\":\"event-1\"}";

        DeadLetterLog entry = DeadLetterLog.builder()
                .id(1L)
                .eventId("event-1")
                .topic("stock-decrease")
                .payload(payload)
                .retryCount(1)
                .lastRetryAt(LocalDateTime.now().minusMinutes(10))
                .build();

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);

        when(deadLetterLogRepository.findAll()).thenReturn(List.of(entry));
        when(kafkaTemplate.send("stock-decrease", payload)).thenReturn(future);
        when(future.get()).thenReturn(null);

        // when
        DLQRetryResult result = kafkaDLQService.retryAllDLQMessagesInternal();

        // then
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(0);

        verify(kafkaTemplate).send("stock-decrease", payload);
        verify(dlqMetricsService).incrementSuccess();
        verify(deadLetterSuccessLogRepository).save(argThat(saved ->
                saved.getEventId().equals("event-1")
                        && saved.getTopic().equals("stock-decrease")
                        && saved.getPayload().equals(payload)
        ));
        verify(deadLetterLogRepository).delete(entry);
        verify(deadLetterLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("retryAllDLQMessagesWithResult - 결과 문자열 반환")
    void retryAllDLQMessagesWithResult_success() throws Exception {
        // given
        String payload = "{\"eventId\":\"event-1\"}";

        DeadLetterLog entry = DeadLetterLog.builder()
                .id(1L)
                .eventId("event-1")
                .topic("stock-decrease")
                .payload(payload)
                .retryCount(1)
                .lastRetryAt(LocalDateTime.now().minusMinutes(10))
                .build();

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);

        when(deadLetterLogRepository.findAll()).thenReturn(List.of(entry));
        when(kafkaTemplate.send("stock-decrease", payload)).thenReturn(future);
        when(future.get()).thenReturn(null);

        // when
        String result = kafkaDLQService.retryAllDLQMessagesWithResult();

        // then
        assertThat(result).isEqualTo("DLQ 전체 재처리 완료 - 성공: 1개, 실패: 0개");
    }

}