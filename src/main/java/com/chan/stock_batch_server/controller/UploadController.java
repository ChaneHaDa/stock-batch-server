package com.chan.stock_batch_server.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/batch")
@Tag(name = "File Upload", description = "파일 업로드 API")
public class UploadController {

	private final String uploadDir;

	public UploadController(@Value("${file.upload-dir}") String uploadDir) {
		this.uploadDir = uploadDir;
	}

	@PostMapping(value = "/upload-json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
		summary = "JSON 파일 업로드",
		description = "여러 개의 JSON 파일을 업로드하여 서버에 저장합니다. 현재는 파일 저장만 수행하며, 향후 배치 작업 연동 예정입니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "202",
			description = "파일 업로드가 성공적으로 처리됨",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = List.class),
				examples = @ExampleObject(value = "[\"file1.json — uploaded\", \"file2.json — uploaded\"]")
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "업로드된 파일이 없거나 잘못된 파일 형식",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(value = "[\"No files uploaded\"]")
			)
		),
		@ApiResponse(responseCode = "500", description = "서버 내부 오류")
	})
	public ResponseEntity<List<String>> uploadJson(
		@Parameter(
			description = "업로드할 JSON 파일들 (여러 개 선택 가능)",
			required = true
		)
		@RequestParam("files") List<MultipartFile> files) throws Exception {
		if (files == null || files.isEmpty()) {
			return ResponseEntity.badRequest().body(List.of("No files uploaded"));
		}

		List<String> results = new ArrayList<>();

		for (MultipartFile file : files) {
			String original = file.getOriginalFilename();
			if (original == null || original.trim().isEmpty()) {
				results.add("Unknown file — skipped (no filename)");
				continue;
			}
			
			if (!original.toLowerCase().endsWith(".json")) {
				results.add(original + " — skipped (not a .json)");
				continue;
			}
			
			if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
				results.add(original + " — skipped (file too large)");
				continue;
			}
			
			// Sanitize filename to prevent path traversal
			String sanitizedName = original.replaceAll("[^a-zA-Z0-9.-]", "_");
			if (!sanitizedName.equals(original)) {
				results.add(original + " → renamed to " + sanitizedName);
			}

			// 로컬에 저장
			Path target = Paths.get(uploadDir).resolve(sanitizedName);
			Files.createDirectories(target.getParent());
			Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
			
			results.add(sanitizedName + " — uploaded");

			//            // 2) Batch Job 실행 (파일별로 독립 파라미터)
			//            JobParameters params = new JobParametersBuilder()
			//                    .addString("fileName", target.toString())
			//                    .addLong("timestamp", System.currentTimeMillis())
			//                    .toJobParameters();
			//
			//            JobExecution exec = jobLauncher.run(importJob, params);
			//            results.add(original + " — job status: " + exec.getStatus());
		}
		// todo: job code

		return ResponseEntity
			.status(HttpStatus.ACCEPTED)
			.body(results);
	}
}
