package com.flab.testrepojava.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockParticipateRequest {

    private Long productId;
    private Long userId;
    private String memberEmail;
}
