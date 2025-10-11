package com.chan.stock_batch_server.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.chan.stock_batch_server.constants.BatchConstants;
import com.chan.stock_batch_server.service.krx.KRXDataImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KRXMigrationBatchConfig {

	private final KRXDataImportService krxDataImportService;

	@Bean
	public Job krxDataMigrationJob(JobRepository jobRepository,
		Step krxMigrationStep) {
		return new JobBuilder(BatchConstants.KRX_MIGRATION_JOB_NAME, jobRepository)
			.start(krxMigrationStep)
			.build();
	}

	@Bean
	@StepScope
	public Tasklet krxMigrationTasklet(
		@Value("#{jobParameters['year']}") Integer year,
		@Value("#{jobParameters['month']}") Integer month) {
		return (contribution, chunkContext) -> {
			log.info("Starting KRX migration for year={}, month={}", year, month);

			// KRX 데이터 임포트 실행
			krxDataImportService.importMonthData(year, month);

			log.info("KRX migration completed for year={}, month={}", year, month);
			return RepeatStatus.FINISHED;
		};
	}

	@Bean
	public Step krxMigrationStep(JobRepository jobRepository,
		@Qualifier("primaryTransactionManager") PlatformTransactionManager txMgr,
		Tasklet krxMigrationTasklet) {
		return new StepBuilder(BatchConstants.KRX_MIGRATION_STEP_NAME, jobRepository)
			.tasklet(krxMigrationTasklet, txMgr)
			.build();
	}
}
