package com.chan.stock_batch_server.controller;

import com.chan.stock_batch_server.dto.ApiResponse;
import com.chan.stock_batch_server.dto.StockNameHistoryDto;
import com.chan.stock_batch_server.model.Stock;
import com.chan.stock_batch_server.model.StockNameHistory;
import com.chan.stock_batch_server.service.StockNameHistoryService;
import com.chan.stock_batch_server.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/stocks")
@Tag(name = "Stock Management", description = "주식 정보 및 주식명 이력 관리 API")
public class StockController {
    
    private final StockService stockService;
    private final StockNameHistoryService stockNameHistoryService;
    
    public StockController(StockService stockService, StockNameHistoryService stockNameHistoryService) {
        this.stockService = stockService;
        this.stockNameHistoryService = stockNameHistoryService;
    }
    
    @GetMapping("/{stockId}/name-history")
    @Operation(
        summary = "주식명 변경 이력 조회",
        description = "특정 주식의 주식명 변경 이력을 시간순으로 조회합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "주식명 이력 조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\n  \"success\": true,\n  \"data\": [\n    {\n      \"name\": \"삼성전자\",\n      \"startAt\": \"2020-01-01\",\n      \"endAt\": null\n    }\n  ]\n}")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주식을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<com.chan.stock_batch_server.dto.ApiResponse<List<StockNameHistoryDto>>> getNameHistory(
            @Parameter(description = "주식 ID", example = "1")
            @PathVariable Integer stockId) {
        
        try {
            Optional<Stock> stockOpt = stockService.findById(stockId);
            
            if (stockOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Stock stock = stockOpt.get();
            List<StockNameHistory> histories = stockNameHistoryService.getNameHistoryByStock(stock);
            
            List<StockNameHistoryDto> historyDtos = histories.stream()
                    .map(history -> StockNameHistoryDto.builder()
                            .name(history.getName())
                            .startAt(history.getStartAt())
                            .endAt(history.getEndAt())
                            .build())
                    .toList();
            
            return ResponseEntity.ok(
                com.chan.stock_batch_server.dto.ApiResponse.success(historyDtos)
            );
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                com.chan.stock_batch_server.dto.ApiResponse.error("Failed to get name history: " + e.getMessage())
            );
        }
    }
    
    @GetMapping("/{stockId}/current-name")
    @Operation(
        summary = "현재 유효한 주식명 조회",
        description = "특정 주식의 현재 시점에서 유효한 주식명을 조회합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "현재 주식명 조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\n  \"success\": true,\n  \"data\": \"삼성전자\"\n}")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주식을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<com.chan.stock_batch_server.dto.ApiResponse<String>> getCurrentName(
            @Parameter(description = "주식 ID", example = "1")
            @PathVariable Integer stockId) {
        
        try {
            Optional<Stock> stockOpt = stockService.findById(stockId);
            
            if (stockOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Stock stock = stockOpt.get();
            String currentName = stockNameHistoryService.getCurrentValidName(stock);
            
            return ResponseEntity.ok(
                com.chan.stock_batch_server.dto.ApiResponse.success(currentName)
            );
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                com.chan.stock_batch_server.dto.ApiResponse.error("Failed to get current name: " + e.getMessage())
            );
        }
    }
    
    @GetMapping("/isin/{isinCode}")
    @Operation(
        summary = "ISIN 코드로 주식 조회",
        description = "ISIN 코드를 사용하여 주식 정보를 조회합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "주식 조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주식을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<com.chan.stock_batch_server.dto.ApiResponse<Stock>> getStockByIsinCode(
            @Parameter(description = "ISIN 코드", example = "KR7005930003")
            @PathVariable String isinCode) {
        
        try {
            Optional<Stock> stockOpt = stockService.findByIsinCode(isinCode);
            
            if (stockOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(
                com.chan.stock_batch_server.dto.ApiResponse.success(stockOpt.get())
            );
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                com.chan.stock_batch_server.dto.ApiResponse.error("Failed to get stock: " + e.getMessage())
            );
        }
    }
}