package com.flab.testrepojava.controller;

import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.dto.ApiResponse;
import com.flab.testrepojava.dto.DeadLetterLogResponse;
import com.flab.testrepojava.service.DLQFailureService;
import com.flab.testrepojava.service.KafkaDLQService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/dlq")
public class DLQFailureController {

    private final KafkaDLQService kafkaDLQService;
    private final DLQFailureService dlqFailureService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 특정 메시지 재처리
    @PostMapping("/retry")
    public ResponseEntity<String> retryDLQMessage(@RequestBody String message) {
        return kafkaDLQService.retrySingleMessage(message);
    }

    // 전체 DLQ 메시지 재처리
    @PostMapping("/retry/all")
    public ResponseEntity<String> retryAllDLQMessages() {
        String result = kafkaDLQService.retryAllDLQMessagesWithResult();
        return ResponseEntity.ok(result);
    }

    // 강제 실패 메시지 전송 (DLQ 테스트용)
    @PostMapping("/test/fail")
    public ResponseEntity<String> sendFailMessage() {
        kafkaTemplate.send("stock-decrease", "fail");
        return ResponseEntity.ok("실패 메시지 전송 완료 (DLQ 테스트용)");
    }

    // 수동확인을 위한 실패 메시지 조회
    @GetMapping("/failures")
    public ResponseEntity<List<DeadLetterLogResponse>> getFailures() {
        return ResponseEntity.ok(dlqFailureService.getAllFailures());
    }

    // DLQ 재처리
    @PostMapping("/retry/{id}")
    public ResponseEntity<ApiResponse<String>> retryDLQ(@PathVariable("id") Long id) {
        try {
            kafkaDLQService.retry(id);
            return ResponseEntity.ok(ApiResponse.success("DLQ 메시지 재처리 성공", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("DLQ 재처리 중 오류 발생"));
        }
    }


    // (선택) 실패 메시지 전체 조회 API는 여기에 추가 가능
}
