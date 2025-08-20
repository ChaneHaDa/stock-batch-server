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
public class StockNameHistoryDto {
    private String name;
    private LocalDate startAt;
    private LocalDate endAt;
}