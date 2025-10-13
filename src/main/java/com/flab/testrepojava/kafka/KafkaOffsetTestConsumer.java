package com.flab.testrepojava.kafka;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class KafkaOffsetTestConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> COMMIT_EVENTS = Set.of("test-1", "test-2", "test-3");

    @KafkaListener(topics = "stock-decrease-test", groupId = "offset-test-group")
    public void consumeForOffsetTest(ConsumerRecord<String, String> record, Acknowledgment ack) throws InterruptedException {
        try {
            // 1. 빈 메시지 방어
            if (record.value() == null || record.value().trim().isEmpty()) {
                log.warn("[OffsetTestConsumer] 빈 메시지 수신 → skip 및 커밋");
                ack.acknowledge();
                return;
            }

            StockDecreaseEvent event = objectMapper.readValue(record.value(), StockDecreaseEvent.class);
            String eventId = event.getEventId();
            log.info("[OffsetTestConsumer] 메시지 수신 :eventId: {} partition: {} offset: {} topic: {} ",
                    eventId, record.partition(), record.offset(), record.topic());

            if (COMMIT_EVENTS.contains(eventId)) {
                ack.acknowledge();
                log.info("[OffsetTestConsumer] 커밋 완료: {}", eventId);
            } else {
                log.warn("[OffsetTestConsumer] 커밋하지 않음 (테스트 목적): {}", eventId);
                Thread.sleep(1500); // 로그 보기 편하게
                System.exit(0);
            }

        } catch (Exception e) {
            log.error("[OffsetTestConsumer] 예외 발생", e);
            ack.acknowledge(); // 2. 예외 발생 시에도 커밋
        }
    }


    @KafkaListener(
            topics = "stock-decrease-test",
            groupId = "manual-commit-test-group",
            containerFactory = "manualAckContainerFactory"
    )
    public void manualCommitTest(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String message = record.value();
        log.info("[ManualCommit] 받은 메시지: {}", message);

        // 강제로 장애 발생시켜봄
//        if (message.contains("test-fail")) {
//            log.warn("[ManualCommit] 강제 종료 발생!");
//            System.exit(1);  // 커밋하지 않고 종료
//        }

        // 메시지 처리 완료 후 커밋
        ack.acknowledge();
    }
}
