package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.dto.DeadLetterLogResponse;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DLQFailureServiceTest {

    private DeadLetterLogRepository deadLetterLogRepository;
    private DLQFailureService dlqFailureService;

    @BeforeEach
    void setUp() {
        deadLetterLogRepository = mock(DeadLetterLogRepository.class);
        dlqFailureService = new DLQFailureService(deadLetterLogRepository);
    }

    @Test
    @DisplayName("getAllFailures - 생성일 내림차순으로 조회 후 응답 DTO로 변환")
    void getAllFailures_success() {
        // given
        LocalDateTime now = LocalDateTime.now();

        DeadLetterLog log1 = new DeadLetterLog();
        log1.setId(1L);
        log1.setEventId("event-1");
        log1.setTopic("stock-decrease-dlt");
        log1.setReason("json parse error");
        log1.setCreatedAt(now.minusMinutes(1));

        DeadLetterLog log2 = new DeadLetterLog();
        log2.setId(2L);
        log2.setEventId("event-2");
        log2.setTopic("stock-decrease-dlt");
        log2.setReason("db timeout");
        log2.setCreatedAt(now);

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        when(deadLetterLogRepository.findAll(sort)).thenReturn(List.of(log2, log1));

        // when
        List<DeadLetterLogResponse> result = dlqFailureService.getAllFailures();

        // then
        assertThat(result).hasSize(2);

        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(0).getEventId()).isEqualTo("event-2");
        assertThat(result.get(0).getTopic()).isEqualTo("stock-decrease-dlt");
        assertThat(result.get(0).getReason()).isEqualTo("db timeout");
        assertThat(result.get(0).getCreatedAt()).isEqualTo(now);

        assertThat(result.get(1).getId()).isEqualTo(1L);
        assertThat(result.get(1).getEventId()).isEqualTo("event-1");
        assertThat(result.get(1).getTopic()).isEqualTo("stock-decrease-dlt");
        assertThat(result.get(1).getReason()).isEqualTo("json parse error");
        assertThat(result.get(1).getCreatedAt()).isEqualTo(now.minusMinutes(1));

        verify(deadLetterLogRepository).findAll(sort);
    }

    @Test
    @DisplayName("getAllFailures - 실패 로그가 없으면 빈 리스트 반환")
    void getAllFailures_empty() {
        // given
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        when(deadLetterLogRepository.findAll(sort)).thenReturn(List.of());

        // when
        List<DeadLetterLogResponse> result = dlqFailureService.getAllFailures();

        // then
        assertThat(result).isEmpty();
        verify(deadLetterLogRepository).findAll(sort);
    }
}