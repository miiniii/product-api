package com.flab.testrepojava.metrics;

import com.flab.testrepojava.slack.SlackNotifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ApiRequestCounter {

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final SlackNotifier slackNotifier;

    public ApiRequestCounter(SlackNotifier slackNotifier) {
        this.slackNotifier = slackNotifier;
    }

    public void increment() {
        int count = requestCount.incrementAndGet();
        if (count >= 10) {
            slackNotifier.queueMessage("API 요청이 10건 이상 발생했습니다! 현재 : " + count);
            requestCount.set(0); //알람 후 카운터 초기화
        }
    }
}
