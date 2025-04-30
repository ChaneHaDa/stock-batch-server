package com.chan.stock_batch_server.config;

import com.chan.stock_batch_server.dto.MonthlyPrice;
import com.chan.stock_batch_server.model.CalcIndexPrice;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.Map;

/**
 * 월별 수익률을 계산하여 CalcStockPrice 엔티티로 저장하는 Batch 설정
 * JobParameters로 전달된 'year'와 'month'를 기준으로 처리합니다.
 */
@Configuration
public class MonthlyStockPriceBatchConfig {
    /**
     * JobParameters로 받은 연도(year)와 월(month)에 해당하는 월별 시가·종가 집계 Reader
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<MonthlyPrice> monthlyPriceReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['year']}") Integer year,
            @Value("#{jobParameters['month']}") Integer month
    ) {
        String jpql = """
            SELECT new com.chan.stock_batch_server.dto.MonthlyPrice(
                YEAR(p.baseDate),
                MONTH(p.baseDate),
                (SELECT p2.closePrice
                 FROM IndexPrice p2
                 WHERE p2.indexInfo = p.indexInfo
                   AND YEAR(p2.baseDate) = :year
                   AND MONTH(p2.baseDate) = :month
                   AND p2.baseDate = (
                       SELECT MIN(p3.baseDate)
                       FROM IndexPrice p3
                       WHERE p3.indexInfo = p.indexInfo
                         AND YEAR(p3.baseDate) = :year
                         AND MONTH(p3.baseDate) = :month
                   )
                ),
                (SELECT p2.closePrice
                 FROM IndexPrice p2
                 WHERE p2.indexInfo = p.indexInfo
                   AND YEAR(p2.baseDate) = :year
                   AND MONTH(p2.baseDate) = :month
                   AND p2.baseDate = (
                       SELECT MAX(p3.baseDate)
                       FROM IndexPrice p3
                       WHERE p3.indexInfo = p.indexInfo
                         AND YEAR(p3.baseDate) = :year
                         AND MONTH(p3.baseDate) = :month
                   )
                ),
                AVG(p.closePrice),
                p.indexInfo
            )
            FROM IndexPrice p
            WHERE YEAR(p.baseDate)  = :year
              AND MONTH(p.baseDate) = :month
            GROUP BY p.indexInfo, YEAR(p.baseDate), MONTH(p.baseDate)
            ORDER BY p.indexInfo.id, YEAR(p.baseDate), MONTH(p.baseDate)
        """;
        return new JpaPagingItemReaderBuilder<MonthlyPrice>()
                .name("monthlyPriceReader")
                .entityManagerFactory(emf)
                .queryString(jpql)
                .parameterValues(Map.of("year", year, "month", month))
                .pageSize(100)
                .build();
    }

    /**
     * 월별 수익률을 계산하여 CalcIndexPrice 객체 생성
     */
    @Bean
    public ItemProcessor<MonthlyPrice, CalcIndexPrice> monthlyPriceProcessor() {
        return monthly -> {
            float ror = (float) ((monthly.getEndPrice() - monthly.getStartPrice()) / monthly.getStartPrice());
            LocalDate baseDate = LocalDate.of(monthly.getYear(), monthly.getMonth(), 1);
            return CalcIndexPrice.builder()
                    .price(monthly.getAveragePrice().floatValue())
                    .monthlyRor(ror)
                    .baseDate(baseDate)
                    .indexInfo(monthly.getIndexInfo())
                    .build();
        };
    }

    /**
     * CalcIndexPrice 저장을 위한 JPA Writer
     */
    @Bean
    public JpaItemWriter<CalcIndexPrice> calcPriceWriter(EntityManagerFactory emf) {
        JpaItemWriter<CalcIndexPrice> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(emf);
        return writer;
    }

    /**
     * Step 구성: Reader, Processor, Writer를 100건 단위 청크로 묶음
     */
    @Bean
    public Step calcPriceStep(
            JobRepository jobRepository,
            PlatformTransactionManager txMgr,
            JpaPagingItemReader<MonthlyPrice> reader,
            ItemProcessor<MonthlyPrice, CalcIndexPrice> processor,
            JpaItemWriter<CalcIndexPrice> writer
    ) {
        return new StepBuilder("calcPriceStep", jobRepository)
                .<MonthlyPrice, CalcIndexPrice>chunk(100, txMgr)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /**
     * Job 구성: 하나의 Step으로 실행
     */
    @Bean
    public Job calcPriceJob(JobRepository jobRepository, Step calcPriceStep) {
        return new JobBuilder("calcPriceJob", jobRepository)
                .start(calcPriceStep)
                .build();
    }
}
