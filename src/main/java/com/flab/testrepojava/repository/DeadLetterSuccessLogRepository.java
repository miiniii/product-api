package com.flab.testrepojava.repository;

import com.flab.testrepojava.domain.DeadLetterSuccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterSuccessLogRepository extends JpaRepository<DeadLetterSuccessLog, Long> {

    boolean existsByEventId(String eventId);
}
