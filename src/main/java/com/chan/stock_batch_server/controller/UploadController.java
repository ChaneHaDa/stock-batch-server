package com.chan.stock_batch_server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/batch")
public class UploadController {

    private final String uploadDir;

    public UploadController(@Value("${file.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }


    @PostMapping(value = "/upload-json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> uploadJson(@RequestParam("files") List<MultipartFile> files) throws Exception {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(List.of("No files uploaded"));
        }

        List<String> results = new ArrayList<>();

        for (MultipartFile file : files) {
            String original = file.getOriginalFilename();
            if (original == null || !original.endsWith(".json")) {
                results.add(original + " — skipped (not a .json)");
                continue;
            }

            // 로컬에 저장
            Path target = Paths.get(uploadDir).resolve(original);
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

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
