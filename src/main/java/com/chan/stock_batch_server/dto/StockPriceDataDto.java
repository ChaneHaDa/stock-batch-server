package com.chan.stock_batch_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockPriceDataDto {
    private LocalDate baseDate;
    private Integer openPrice;
    private Integer closePrice;
    private Integer highPrice;
    private Integer lowPrice;
    private Integer tradeQuantity;
    private Long tradeAmount;
    private Long issuedCount;
}