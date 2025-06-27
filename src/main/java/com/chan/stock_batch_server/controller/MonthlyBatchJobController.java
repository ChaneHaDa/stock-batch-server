package com.chan.stock_batch_server.controller;

import java.time.LocalDate;
import java.time.YearMonth;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/batch")
@Tag(name = "Monthly Batch Job", description = "월별 배치 작업 API")
public class MonthlyBatchJobController {
	private final JobLauncher jobLauncher;
	private final Job calcIndexPriceJob;
	private final Job calcStockPriceJob;

	public MonthlyBatchJobController(JobLauncher jobLauncher, @Qualifier("calcIndexPriceJob") Job calcIndexPriceJob,
		@Qualifier("calcStockPriceJob") Job calcStockPriceJob) {
		this.jobLauncher = jobLauncher;
		this.calcIndexPriceJob = calcIndexPriceJob;
		this.calcStockPriceJob = calcStockPriceJob;
	}

	@PostMapping("/monthly-index")
	@Operation(
		summary = "지수 가격 월별 배치 작업 실행",
		description = "특정 년월에 대해 지수 가격 계산 배치 작업을 실행합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "배치 작업이 성공적으로 완료됨",
			content = @Content(
				mediaType = "text/plain",
				examples = @ExampleObject(value = "Job calcIndexPriceJob completed with status: COMPLETED")
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
		@ApiResponse(responseCode = "500", description = "서버 내부 오류")
	})
	public ResponseEntity<String> runMonthlyIndexBatch(
		@Parameter(description = "년도", example = "2024")
		@RequestParam("year") int year,
		@Parameter(description = "월 (1-12)", example = "12")
		@RequestParam("month") int month) throws Exception {

		JobParameters params = new JobParametersBuilder()
			.addLong("year", (long)year)
			.addLong("month", (long)month)
			.addLong("timestamp", System.currentTimeMillis())
			.toJobParameters();

		JobExecution execution = jobLauncher.run(calcIndexPriceJob, params);
		return ResponseEntity.ok(
			String.format("Job %s completed with status: %s", execution.getJobInstance().getJobName(),
				execution.getStatus())
		);
	}

	@PostMapping("/monthly-index-range")
	@Operation(
		summary = "지수 가격 월별 배치 작업 실행 (범위)",
		description = "지정된 날짜 범위에 대해 지수 가격 계산 배치 작업을 월별로 실행합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "배치 작업이 성공적으로 완료됨",
			content = @Content(
				mediaType = "text/plain",
				examples = @ExampleObject(value = "Triggered calcIndexPriceJob for 2024-01 → 2024-12")
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
		@ApiResponse(responseCode = "500", description = "서버 내부 오류")
	})
	public ResponseEntity<String> runMonthlyIndexBatchByRange(
		@Parameter(description = "시작 날짜 (YYYY-MM-DD 형식)", example = "2024-01-01")
		@RequestParam("startDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate startDate,
		@Parameter(description = "종료 날짜 (YYYY-MM-DD 형식)", example = "2024-12-31")
		@RequestParam("endDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate endDate
	) throws Exception {
		YearMonth startYm = YearMonth.from(startDate);
		YearMonth endYm = YearMonth.from(endDate);

		for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
			JobParameters params = new JobParametersBuilder()
				.addLong("year", (long)ym.getYear())
				.addLong("month", (long)ym.getMonthValue())
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			jobLauncher.run(calcIndexPriceJob, params);
		}

		return ResponseEntity.ok(
			String.format("Triggered calcIndexPriceJob for %s → %s", startYm, endYm)
		);
	}

	@PostMapping("/monthly-stock")
	@Operation(
		summary = "주식 가격 월별 배치 작업 실행",
		description = "특정 년월에 대해 주식 가격 계산 배치 작업을 실행합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "배치 작업이 성공적으로 완료됨",
			content = @Content(
				mediaType = "text/plain",
				examples = @ExampleObject(value = "Job calcStockPriceJob completed with status: COMPLETED")
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
		@ApiResponse(responseCode = "500", description = "서버 내부 오류")
	})
	public ResponseEntity<String> runMonthlyStockBatch(
		@Parameter(description = "년도", example = "2024")
		@RequestParam("year") int year,
		@Parameter(description = "월 (1-12)", example = "12")
		@RequestParam("month") int month) throws Exception {

		JobParameters params = new JobParametersBuilder()
			.addLong("year", (long)year)
			.addLong("month", (long)month)
			.addLong("timestamp", System.currentTimeMillis())
			.toJobParameters();

		JobExecution execution = jobLauncher.run(calcStockPriceJob, params);
		return ResponseEntity.ok(
			String.format("Job %s completed with status: %s", execution.getJobInstance().getJobName(),
				execution.getStatus())
		);
	}

	@PostMapping("/monthly-stock-range")
	@Operation(
		summary = "주식 가격 월별 배치 작업 실행 (범위)",
		description = "지정된 날짜 범위에 대해 주식 가격 계산 배치 작업을 월별로 실행합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "배치 작업이 성공적으로 완료됨",
			content = @Content(
				mediaType = "text/plain",
				examples = @ExampleObject(value = "Triggered calcStockPriceJob for 2024-01 → 2024-12")
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
		@ApiResponse(responseCode = "500", description = "서버 내부 오류")
	})
	public ResponseEntity<String> runMonthlyStockBatchByRange(
		@Parameter(description = "시작 날짜 (YYYY-MM-DD 형식)", example = "2024-01-01")
		@RequestParam("startDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate startDate,
		@Parameter(description = "종료 날짜 (YYYY-MM-DD 형식)", example = "2024-12-31")
		@RequestParam("endDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate endDate
	) throws Exception {
		YearMonth startYm = YearMonth.from(startDate);
		YearMonth endYm = YearMonth.from(endDate);

		for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
			JobParameters params = new JobParametersBuilder()
				.addLong("year", (long)ym.getYear())
				.addLong("month", (long)ym.getMonthValue())
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			jobLauncher.run(calcStockPriceJob, params);
		}

		return ResponseEntity.ok(
			String.format("Triggered calcStockPriceJob for %s → %s", startYm, endYm)
		);
	}
}
