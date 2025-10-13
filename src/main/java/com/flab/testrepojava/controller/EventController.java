package com.flab.testrepojava.controller;

import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.dto.ApiResponse;
import com.flab.testrepojava.dto.StockParticipateRequest;
import com.flab.testrepojava.dto.StockParticipateResponse;
import com.flab.testrepojava.kafka.KafkaProducerService;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import com.flab.testrepojava.redis.RedisLockService;
import com.flab.testrepojava.service.KafkaDLQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/events")
public class EventController {

    private final KafkaProducerService kafkaProducerService;
    private final RedisLockService redisLockService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaDLQService kafkaDLQService;

    @PostMapping("/participate")
    public ResponseEntity<ApiResponse<StockParticipateResponse>> participate(@RequestBody StockParticipateRequest request) {

        Long productId = request.getProductId();
        Long userId = request.getUserId();
        String memberEmail = request.getMemberEmail();
        int limit = 100;

        //Redis 중복 체크
        if (redisLockService.isAlreadyParticipated(productId, userId)) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.fail("이미 참여한 사용자입니다.")
            );
        }

        //선착순 인원 제한 체크
        if (redisLockService.isSoldOut(productId, limit)) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.fail("이벤트 참여 인원이 초과되었습니다.")
            );
        }

        //Kafka 메시지 전송
        kafkaProducerService.sendStockDecreaseEvent(
                StockDecreaseEvent.builder()
                        .productId(productId)
                        .userId(userId)
                        .memberEmail(memberEmail)
                        .build()
        );

        //Redis에 참여 기록 및 카운트 증가
        redisLockService.markParticipated(productId, userId); // 참여 플래그 저장
        redisLockService.increaseParticipantCount(productId); //인원 수 증가
        redisLockService.saveSuccessfulParticipant(productId, userId); // 성공자 목록에 추가

        return ResponseEntity.ok(ApiResponse.success("이벤트 참여 요청 완료", new StockParticipateResponse(productId, userId)));
    }

    //유저가 참여했나 확인
    @GetMapping("/participation-status")
    public ResponseEntity<ApiResponse<Boolean>> checkParticipation(
            @RequestParam("productId") Long productId,
            @RequestParam("userId") Long userId
    ) {
        boolean participated = redisLockService.isAlreadyParticipated(productId, userId);

        return ResponseEntity.ok(
                ApiResponse.success("참여 여부 조회 완료", participated)
        );
    }

    @GetMapping("/success-list")
    public ResponseEntity<ApiResponse<Set<String>>> getSuccessList(@RequestParam("productId") Long productId) {
        Set<String> participants = redisLockService.getSuccessfulParticipants(productId);
        return ResponseEntity.ok(
                ApiResponse.success("성공한 유저 목록 조회 완료", participants)
        );
    }


    //테스트용
    @PostMapping("/send")
    public ResponseEntity<String> sendEvent(@RequestBody StockDecreaseEvent event) {
        kafkaProducerService.sendStockDecreaseEvent(event);
        return ResponseEntity.ok("Kafka 메시지 전송 완료");
    }

}
