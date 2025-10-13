package com.flab.testrepojava.slack;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
@RequiredArgsConstructor
public class SlackNotifier {

    private final WebClient slackWebClient;

    @Value("${slack.webhook.url}")
    private String slackWebhookUrl;

    // 메시지를 임시 저장할 큐
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();

    // 외부에서 호출하는 메서드
    public void queueMessage(String message) {
        messageQueue.add(message);
    }

    // 2분마다 한 번씩 슬랙 전송 (초기 10초 지연 후 시작)
    @Scheduled(initialDelay = 10_000, fixedDelay = 120_000)
    public void sendBatchMessages() {
        if (messageQueue.isEmpty()) return;

        StringBuilder combinedMessage = new StringBuilder("📢 Slack 알림 모음\n");

        while (!messageQueue.isEmpty()) {
            String message = messageQueue.poll();
            combinedMessage.append("\n---\n").append(message);
        }

        slackWebClient.post()
                .uri(slackWebhookUrl)
                .bodyValue(Map.of("text", combinedMessage.toString()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.warn("🚫 Slack 전송 실패 - 상태 코드: {}", response.statusCode().value());
                    return Mono.empty();
                })
                .bodyToMono(Void.class)
                .doOnSuccess(unused -> log.info("✅ Slack 메시지 전송 성공"))
                .doOnError(error -> log.error("🔥 Slack 전송 중 예외 발생", error))
                .subscribe();
    }
}

