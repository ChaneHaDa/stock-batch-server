package com.chan.stock_batch_server.dto;

import com.chan.stock_batch_server.model.IndexInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonthlyPrice {
    private Integer year;
    private Integer month;
    private Float startPrice;
    private Float endPrice;
    private Double averagePrice;
    private IndexInfo indexInfo;
}
