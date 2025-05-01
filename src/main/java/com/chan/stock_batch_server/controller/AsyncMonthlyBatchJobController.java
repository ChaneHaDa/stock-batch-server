package com.chan.stock_batch_server.controller;

import com.chan.stock_batch_server.service.AsyncMonthlyIndexBatchJobService;
import com.chan.stock_batch_server.service.AsyncMonthlyStockBatchJobService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/async-batch")
public class AsyncMonthlyBatchJobController {
    private final AsyncMonthlyStockBatchJobService asyncMonthlyStockBatchJobService;
    private final AsyncMonthlyIndexBatchJobService asyncMonthlyIndexBatchJobService;

    public AsyncMonthlyBatchJobController(AsyncMonthlyStockBatchJobService asyncMonthlyStockBatchJobService, AsyncMonthlyIndexBatchJobService asyncMonthlyIndexBatchJobService) {
        this.asyncMonthlyStockBatchJobService = asyncMonthlyStockBatchJobService;
        this.asyncMonthlyIndexBatchJobService = asyncMonthlyIndexBatchJobService;
    }

    @PostMapping("/monthly-index-range")
    public ResponseEntity<String> runAsyncMonthlyIndexBatchByRange(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) throws Exception {
        asyncMonthlyIndexBatchJobService.runMonthlyIndexBatchByRange(startDate, endDate);

        return ResponseEntity.ok(
                String.format("Triggered Async calcIndexPriceJob for %s → %s")
        );
    }

    @PostMapping("/monthly-stock-range")
    public ResponseEntity<String> runAsyncMonthlyStockBatchByRange(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) throws Exception {
        asyncMonthlyStockBatchJobService.runMonthlyStockBatchByRange(startDate, endDate);

        return ResponseEntity.ok(
                String.format("Triggered Async calcIndexPriceJob for %s → %s")
        );
    }
}
