package com.flab.testrepojava.service;

import com.flab.testrepojava.dto.DeadLetterLogResponse;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
@Slf4j
public class DLQFailureService {

    private final DeadLetterLogRepository deadLetterLogRepository;

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

}
