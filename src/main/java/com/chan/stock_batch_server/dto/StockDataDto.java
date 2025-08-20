package com.chan.stock_batch_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockDataDto {
    private String isinCode;
    private String currentName;
    private String shortCode;
    private String marketCategory;
    private LocalDate startAt;
    private LocalDate endAt;
    private List<StockNameHistoryDto> nameHistory;
    private List<StockPriceDataDto> priceData;
}