package com.flab.testrepojava.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterLogResponse {

    private Long id;
    private String eventId;
    private String topic;
    private String reason;
    private LocalDateTime createdAt;
}
