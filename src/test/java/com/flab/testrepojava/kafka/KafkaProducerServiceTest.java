package com.flab.testrepojava.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class KafkaProducerServiceTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;

    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = mock(ObjectMapper.class);

        kafkaProducerService = new KafkaProducerService(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("sendStockDecreaseEvent - eventId가 없으면 자동 생성 후 Kafka로 전송한다")
    void sendStockDecreaseEvent_shouldGenerateEventIdAndSend() throws Exception {
        // given
        StockDecreaseEvent event = new StockDecreaseEvent();
        event.setProductId(1L);
        event.setUserId(1L);
        event.setMemberEmail("test@test.com");
        event.setEventId(null);

        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventId\":\"generated\"}");

        // when
        kafkaProducerService.sendStockDecreaseEvent(event);

        // then
        assertThat(event.getEventId()).isNotBlank();
        verify(objectMapper).writeValueAsString(event);
        verify(kafkaTemplate).send("stock-decrease", "{\"eventId\":\"generated\"}");
    }

    @Test
    @DisplayName("sendStockDecreaseEvent - eventId가 이미 있으면 그대로 Kafka로 전송한다")
    void sendStockDecreaseEvent_shouldKeepExistingEventId() throws Exception {
        // given
        StockDecreaseEvent event = new StockDecreaseEvent();
        event.setProductId(1L);
        event.setUserId(1L);
        event.setMemberEmail("test@test.com");
        event.setEventId("event-123");

        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventId\":\"event-123\"}");

        // when
        kafkaProducerService.sendStockDecreaseEvent(event);

        // then
        assertThat(event.getEventId()).isEqualTo("event-123");
        verify(objectMapper).writeValueAsString(event);
        verify(kafkaTemplate).send("stock-decrease", "{\"eventId\":\"event-123\"}");
    }

    @Test
    @DisplayName("sendStockDecreaseEvent - 유효성 검증에 실패하면 예외를 던진다")
    void sendStockDecreaseEvent_shouldThrowWhenValidationFails() {
        // given
        StockDecreaseEvent event = new StockDecreaseEvent();
        event.setProductId(null); // 유효성 검증 실패 유도
        event.setUserId(1L);
        event.setMemberEmail("test@test.com");
        event.setEventId("event-123");

        // when & then
        assertThatThrownBy(() -> kafkaProducerService.sendStockDecreaseEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("StockDecreaseEvent 유효성 검증 실패");

        verifyNoInteractions(objectMapper);
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("sendStockDecreaseEvent - JSON 직렬화에 실패하면 RuntimeException을 던진다")
    void sendStockDecreaseEvent_shouldThrowWhenSerializationFails() throws Exception {
        // given
        StockDecreaseEvent event = new StockDecreaseEvent();
        event.setProductId(1L);
        event.setUserId(1L);
        event.setMemberEmail("test@test.com");
        event.setEventId("event-123");

        when(objectMapper.writeValueAsString(event))
                .thenThrow(new JsonProcessingException("json error") {});

        // when & then
        assertThatThrownBy(() -> kafkaProducerService.sendStockDecreaseEvent(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Kafka 메시지 직렬화 실패");

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

}