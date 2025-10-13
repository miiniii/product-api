package com.flab.testrepojava.repository;

import com.flab.testrepojava.domain.DeadLetterLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeadLetterLogRepository extends JpaRepository<DeadLetterLog, Long> {

    @Query("SELECT d FROM DeadLetterLog d WHERE d.eventId = :eventId")
    Optional<DeadLetterLog> findByEventId(@Param("eventId") String eventId);

    void deleteByEventId(String eventId);
}
