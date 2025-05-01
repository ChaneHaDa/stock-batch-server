package com.chan.stock_batch_server.dto;

import com.chan.stock_batch_server.model.Stock;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonthlyStockPrice {
    private Integer year;
    private Integer month;
    private Integer startPrice;
    private Integer endPrice;
    private Double averagePrice;
    private Stock stock;
}
