package com.chan.stock_batch_server.service;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

@Service
public class AsyncMonthlyStockBatchJobService {
    private final JobLauncher jobLauncher;
    private final Job calcStockPriceJob;

    public AsyncMonthlyStockBatchJobService(JobLauncher jobLauncher, @Qualifier("calcStockPriceJob") Job calcStockPriceJob) {
        this.jobLauncher = jobLauncher;
        this.calcStockPriceJob = calcStockPriceJob;
    }

    /**
     * 한 달 단위 배치 비동기 실행
     */
    @Async("batchTaskExecutor")
    public Future<JobExecution> runMonthlyStockBatch(int year, int month) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("year", (long) year)
                .addLong("month", (long) month)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(calcStockPriceJob, params);
        return new AsyncResult<>(execution);
    }

    /**
     * 범위 내 모든 월 병렬 배치 실행 + 결과 추적
     */
    @Async("batchTaskExecutor")
    public Future<List<JobExecution>> runMonthlyStockBatchByRange(LocalDate startDate, LocalDate endDate) throws Exception {
        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm = YearMonth.from(endDate);

        List<Future<JobExecution>> futures = new ArrayList<>();
        List<JobExecution> results = new ArrayList<>();

        for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
            Future<JobExecution> future = this.runMonthlyStockBatch(ym.getYear(), ym.getMonthValue());
            futures.add(future);
        }

        // 모든 비동기 배치 작업 완료 대기
        for (Future<JobExecution> future : futures) {
            JobExecution exec = future.get(); // 예외 발생 시 throw 됨
            results.add(exec);
        }

        return new AsyncResult<>(results);
    }
}
