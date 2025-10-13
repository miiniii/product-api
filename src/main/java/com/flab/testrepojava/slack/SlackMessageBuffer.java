package com.flab.testrepojava.slack;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Getter
@Component
public class SlackMessageBuffer {

    private final List<String> buffer = new CopyOnWriteArrayList<>();

    public void add(String message) {
        buffer.add(message);
    }

    public List<String> drainMessages() {
        List<String> drained = List.copyOf(buffer);
        buffer.clear();
        return drained;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }
}
