package com.chan.stock_batch_server.service;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class AsyncMonthlyBatchJobService {
    private final JobLauncher jobLauncher;
    private final Job calcPriceJob;

    public AsyncMonthlyBatchJobService(JobLauncher jobLauncher, @Qualifier("calcPriceJob") Job calcPriceJob) {
        this.jobLauncher = jobLauncher;
        this.calcPriceJob = calcPriceJob;
    }

    @Async
    public void runMonthlyIndexBatch(int year, int month) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("year", (long) year)
                .addLong("month", (long) month)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(calcPriceJob, params);
    }

    @Async
    public void runMonthlyIndexBatchByRange(LocalDate startDate, LocalDate endDate) throws Exception {
        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm   = YearMonth.from(endDate);

        for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
            JobParameters params = new JobParametersBuilder()
                    .addLong("year",      (long) ym.getYear())
                    .addLong("month",     (long) ym.getMonthValue())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(calcPriceJob, params);
        }
    }
}
