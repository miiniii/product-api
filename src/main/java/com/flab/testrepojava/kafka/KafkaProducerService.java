package com.flab.testrepojava.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    public void sendStockDecreaseEvent(StockDecreaseEvent event) {
        // eventId가 비어 있으면 자동 생성
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }

        // 유효성 검사
        Set<ConstraintViolation<StockDecreaseEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("StockDecreaseEvent 유효성 검증 실패: " + violations.iterator().next().getMessage());
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("stock-decrease", json);
            log.info("Kafka 메시지 전송 완료: {}", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Kafka 메시지 직렬화 실패", e);
        }
    }


}
