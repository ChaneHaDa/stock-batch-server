package com.chan.stock_batch_server.controller;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/batch")
public class MonthlyBatchJobController {
    private final JobLauncher jobLauncher;
    private final Job calcIndexPriceJob;
    private final Job calcStockPriceJob;

    public MonthlyBatchJobController(JobLauncher jobLauncher, @Qualifier("calcIndexPriceJob") Job calcIndexPriceJob, @Qualifier("calcStockPriceJob") Job calcStockPriceJob) {
        this.jobLauncher = jobLauncher;
        this.calcIndexPriceJob = calcIndexPriceJob;
        this.calcStockPriceJob = calcStockPriceJob;
    }

    @PostMapping("/monthly-index")
    public ResponseEntity<String> runMonthlyIndexBatch(
            @RequestParam("year") int year,
            @RequestParam("month") int month) throws Exception {

        JobParameters params = new JobParametersBuilder()
                .addLong("year", (long) year)
                .addLong("month", (long) month)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(calcIndexPriceJob, params);
        return ResponseEntity.ok(
                String.format("Job %s completed with status: %s", execution.getJobInstance().getJobName(), execution.getStatus())
        );
    }

    @PostMapping("/monthly-index-range")
    public ResponseEntity<String> runMonthlyIndexBatchByRange(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) throws Exception {
        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm   = YearMonth.from(endDate);

        for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
            JobParameters params = new JobParametersBuilder()
                    .addLong("year",      (long) ym.getYear())
                    .addLong("month",     (long) ym.getMonthValue())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(calcIndexPriceJob, params);
        }

        return ResponseEntity.ok(
                String.format("Triggered calcIndexPriceJob for %s → %s", startYm, endYm)
        );
    }

    @PostMapping("/monthly-stock")
    public ResponseEntity<String> runMonthlyStockBatch(
            @RequestParam("year") int year,
            @RequestParam("month") int month) throws Exception {

        JobParameters params = new JobParametersBuilder()
                .addLong("year", (long) year)
                .addLong("month", (long) month)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(calcStockPriceJob, params);
        return ResponseEntity.ok(
                String.format("Job %s completed with status: %s", execution.getJobInstance().getJobName(), execution.getStatus())
        );
    }

    @PostMapping("/monthly-stock-range")
    public ResponseEntity<String> runMonthlyStockBatchByRange(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) throws Exception {
        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm   = YearMonth.from(endDate);

        for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
            JobParameters params = new JobParametersBuilder()
                    .addLong("year",      (long) ym.getYear())
                    .addLong("month",     (long) ym.getMonthValue())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(calcStockPriceJob, params);
        }

        return ResponseEntity.ok(
                String.format("Triggered calcStockPriceJob for %s → %s", startYm, endYm)
        );
    }
}
