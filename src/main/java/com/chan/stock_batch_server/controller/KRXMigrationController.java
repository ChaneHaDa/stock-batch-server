package com.chan.stock_batch_server.controller;

import java.util.List;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chan.stock_batch_server.service.krx.KRXDataImportService;
import com.chan.stock_batch_server.service.krx.StockNameHistoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/krx-migration")
@RequiredArgsConstructor
public class KRXMigrationController {

	private final JobLauncher jobLauncher;
	private final Job krxDataMigrationJob;
	private final KRXDataImportService krxDataImportService;
	private final StockNameHistoryService stockNameHistoryService;

	/**
	 * KRX 데이터 마이그레이션 실행
	 */
	@PostMapping("/import")
	public ResponseEntity<String> importKRXData(@RequestParam int year, @RequestParam int month) {
		try {
			JobParameters parameters = new JobParametersBuilder()
				.addLong("time", System.currentTimeMillis())
				.addLong("year", (long)year)
				.addLong("month", (long)month)
				.toJobParameters();

			JobExecution execution = jobLauncher.run(krxDataMigrationJob, parameters);

			return ResponseEntity.ok("KRX migration started for " + year + "-" + month +
				". Job ID: " + execution.getId());

		} catch (JobExecutionAlreadyRunningException |
				 JobRestartException |
				 JobInstanceAlreadyCompleteException |
				 JobParametersInvalidException e) {
			log.error("Error starting KRX migration", e);
			return ResponseEntity.badRequest().body("Failed to start migration: " + e.getMessage());
		}
	}

	/**
	 * Stock Name History 검증
	 */
	@GetMapping("/validate/name-history")
	public ResponseEntity<List<String>> validateStockNameHistory() {
		List<String> issues = stockNameHistoryService.validateNameHistories();

		if (issues.isEmpty()) {
			return ResponseEntity.ok(List.of("No issues found in stock name history"));
		} else {
			return ResponseEntity.badRequest().body(issues);
		}
	}

	/**
	 * 마이그레이션 상태 조회
	 */
	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> getMigrationStatus() {
		return ResponseEntity.ok(Map.of(
			"status", "ready",
			"message", "KRX migration service is ready"
		));
	}
}
