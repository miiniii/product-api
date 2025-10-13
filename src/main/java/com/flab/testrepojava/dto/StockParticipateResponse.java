package com.flab.testrepojava.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StockParticipateResponse {

    private Long productId;
    private Long userId;
}
