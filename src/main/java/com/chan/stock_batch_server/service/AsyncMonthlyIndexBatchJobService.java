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
public class AsyncMonthlyIndexBatchJobService {
    private final JobLauncher jobLauncher;
    private final Job calcIndexPriceJob;

    public AsyncMonthlyIndexBatchJobService(JobLauncher jobLauncher, @Qualifier("calcIndexPriceJob") Job calcIndexPriceJob) {
        this.jobLauncher = jobLauncher;
        this.calcIndexPriceJob = calcIndexPriceJob;
    }


    /**
     * 한 달 단위 비동기 실행
     */
    @Async("batchTaskExecutor")
    public Future<JobExecution> runMonthlyIndexBatch(int year, int month) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("year", (long) year)
                .addLong("month", (long) month)
                .addLong("timestamp", System.currentTimeMillis()) // 중복 방지
                .toJobParameters();

        JobExecution execution = jobLauncher.run(calcIndexPriceJob, params);
        return new AsyncResult<>(execution);
    }

    /**
     * 여러 달 단위 비동기 병렬 실행
     */
    @Async("batchTaskExecutor")
    public Future<List<JobExecution>> runMonthlyIndexBatchByRange(LocalDate startDate, LocalDate endDate) throws Exception {
        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm   = YearMonth.from(endDate);

        List<Future<JobExecution>> futures = new ArrayList<>();
        List<JobExecution> results = new ArrayList<>();

        for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
            // 병렬 실행을 위해 자기 자신을 통해 비동기 호출
            Future<JobExecution> future = this.runMonthlyIndexBatch(ym.getYear(), ym.getMonthValue());
            futures.add(future);
        }

        // 모든 Future 완료 대기
        for (Future<JobExecution> future : futures) {
            JobExecution exec = future.get(); // 완료될 때까지 블로킹
            results.add(exec);
        }

        return new AsyncResult<>(results);
    }
}
