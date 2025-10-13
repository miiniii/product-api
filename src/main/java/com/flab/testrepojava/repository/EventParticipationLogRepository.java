package com.flab.testrepojava.repository;

import com.flab.testrepojava.domain.EventParticipationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventParticipationLogRepository extends JpaRepository<EventParticipationLog, Long> {
}
