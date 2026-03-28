package com.flab.testrepojava.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDecreaseEvent {

    @NotNull
    private Long productId;

    private Long userId;

    @NotBlank(message = "memberEmail은 필수입니다.")
    private String memberEmail;

    @NotBlank
    private String eventId;
}
