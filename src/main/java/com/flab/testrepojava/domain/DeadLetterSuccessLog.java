package com.flab.testrepojava.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class DeadLetterSuccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String eventId;

    private String topic;

    private String payload;

    private LocalDateTime successAt;

    @PrePersist
    public void onCreate() {
        this.successAt = LocalDateTime.now();
    }
}
