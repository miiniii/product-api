package com.flab.testrepojava.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApiResponse<T> {

    private String status; // "SUCCESS" or "FAIL"
    private String message; // 상세메세지
    private T data; // 실제 데이터
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder()
                .status("FAIL")
                .message(message)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
