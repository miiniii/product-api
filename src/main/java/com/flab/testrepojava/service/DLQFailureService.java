package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.domain.DeadLetterSuccessLog;
import com.flab.testrepojava.dto.DeadLetterLogResponse;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
@Service
@RequiredArgsConstructor
@Slf4j
public class DLQFailureService {

    private final DeadLetterLogRepository deadLetterLogRepository;
    private final DeadLetterSuccessLogRepository deadLetterSuccessLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public List<DeadLetterLogResponse> getAllFailures() {
        return deadLetterLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(log -> new DeadLetterLogResponse(
                        log.getId(),
                        log.getEventId(),
                        log.getTopic(),
                        log.getReason(),
                        log.getCreatedAt()
                ))
                .toList();
    }


    public String retryMessageById(Long id) {
        DeadLetterLog deadLetterLog = deadLetterLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 실패 메시지입니다."));

        try {
            kafkaTemplate.send(deadLetterLog.getTopic(), deadLetterLog.getPayload()).get();

            // 재처리 성공 시 성공 로그에 기록
            deadLetterSuccessLogRepository.save(
                    DeadLetterSuccessLog.builder()
                            .eventId(deadLetterLog.getEventId())
                            .payload(deadLetterLog.getPayload())
                            .topic(deadLetterLog.getTopic())
                            .successAt(LocalDateTime.now())
                            .build()
            );

            // 실패 로그는 삭제하지 않음 (운영 추적용)
            return "메시지 재처리 성공 (ID: " + id + ")";
        } catch (Exception e) {
            log.error("메시지 재처리 실패 (ID: {})", id, e);
            return "메시지 재처리 실패: " + e.getMessage();
        }
    }
}
