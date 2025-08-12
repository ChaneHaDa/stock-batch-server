package com.chan.stock_batch_server.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chan.stock_batch_server.service.AsyncMonthlyIndexBatchJobService;
import com.chan.stock_batch_server.service.AsyncMonthlyStockBatchJobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/async-batch")
@Tag(name = "Async Monthly Batch Job", description = "비동기 월별 배치 작업 API")
public class AsyncMonthlyBatchJobController {
	private final AsyncMonthlyStockBatchJobService asyncMonthlyStockBatchJobService;
	private final AsyncMonthlyIndexBatchJobService asyncMonthlyIndexBatchJobService;

	public AsyncMonthlyBatchJobController(AsyncMonthlyStockBatchJobService asyncMonthlyStockBatchJobService,
		AsyncMonthlyIndexBatchJobService asyncMonthlyIndexBatchJobService) {
		this.asyncMonthlyStockBatchJobService = asyncMonthlyStockBatchJobService;
		this.asyncMonthlyIndexBatchJobService = asyncMonthlyIndexBatchJobService;
	}

	@PostMapping("/monthly-index-range")
	@Operation(
		summary = "비동기 지수 가격 월별 배치 작업 실행 (범위)",
		description = "지정된 날짜 범위에 대해 지수 가격 계산 배치 작업을 비동기로 실행합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "배치 작업이 성공적으로 시작됨",
			content = @Content(
				mediaType = "text/plain",
				examples = @ExampleObject(value = "Triggered Async calcIndexPriceJob")
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
		@ApiResponse(responseCode = "500", description = "서버 내부 오류")
	})
	public ResponseEntity<String> runAsyncMonthlyIndexBatchByRange(
		@Parameter(description = "시작 날짜 (YYYY-MM-DD 형식)", example = "2024-01-01")
		@RequestParam("startDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate startDate,
		@Parameter(description = "종료 날짜 (YYYY-MM-DD 형식)", example = "2024-12-31")
		@RequestParam("endDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate endDate
	) throws Exception {
		asyncMonthlyIndexBatchJobService.runMonthlyIndexBatchByRange(startDate, endDate);

		return ResponseEntity.ok("Triggered Async calcIndexPriceJob");
	}

	@PostMapping("/monthly-stock-range")
	@Operation(
		summary = "비동기 주식 가격 월별 배치 작업 실행 (범위)",
		description = "지정된 날짜 범위에 대해 주식 가격 계산 배치 작업을 비동기로 실행합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "배치 작업이 성공적으로 시작됨",
			content = @Content(
				mediaType = "text/plain",
				examples = @ExampleObject(value = "Triggered Async calcIndexPriceJob")
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
		@ApiResponse(responseCode = "500", description = "서버 내부 오류")
	})
	public ResponseEntity<String> runAsyncMonthlyStockBatchByRange(
		@Parameter(description = "시작 날짜 (YYYY-MM-DD 형식)", example = "2024-01-01")
		@RequestParam("startDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate startDate,
		@Parameter(description = "종료 날짜 (YYYY-MM-DD 형식)", example = "2024-12-31")
		@RequestParam("endDate")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate endDate
	) throws Exception {
		asyncMonthlyStockBatchJobService.runMonthlyStockBatchByRange(startDate, endDate);

		return ResponseEntity.ok("Triggered Async calcIndexPriceJob");
	}
}
