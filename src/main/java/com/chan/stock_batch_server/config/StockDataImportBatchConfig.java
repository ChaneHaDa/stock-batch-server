package com.chan.stock_batch_server.config;

import com.chan.stock_batch_server.dto.StockDataDto;
import com.chan.stock_batch_server.dto.StockImportDto;
import com.chan.stock_batch_server.dto.ApiResponseDto;
import com.chan.stock_batch_server.dto.StockItemDto;
import com.chan.stock_batch_server.dto.StockPriceDataDto;
import com.chan.stock_batch_server.model.Stock;
import com.chan.stock_batch_server.model.StockPrice;
import com.chan.stock_batch_server.service.StockNameHistoryService;
import com.chan.stock_batch_server.service.StockService;
import com.chan.stock_batch_server.service.StockDataValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
public class StockDataImportBatchConfig {
    
    private final StockService stockService;
    private final StockNameHistoryService stockNameHistoryService;
    private final StockDataValidationService validationService;
    
    public StockDataImportBatchConfig(StockService stockService, 
                                     StockNameHistoryService stockNameHistoryService,
                                     StockDataValidationService validationService) {
        this.stockService = stockService;
        this.stockNameHistoryService = stockNameHistoryService;
        this.validationService = validationService;
    }

    @Bean
    @StepScope
    public ItemReader<StockItemDto> stockDataReader(
            @Value("#{jobParameters['fileName']}") String fileName) {
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            
            String jsonContent = Files.readString(new File(fileName).toPath());
            ApiResponseDto apiResponse = mapper.readValue(jsonContent, ApiResponseDto.class);
            
            if (apiResponse.getResponse() == null || 
                apiResponse.getResponse().getBody() == null || 
                apiResponse.getResponse().getBody().getItems() == null ||
                apiResponse.getResponse().getBody().getItems().getItem() == null) {
                throw new RuntimeException("Invalid JSON structure in file: " + fileName);
            }
            
            return new ListItemReader<>(apiResponse.getResponse().getBody().getItems().getItem());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON file: " + fileName, e);
        }
    }

    @Bean
    public ItemProcessor<StockItemDto, Stock> stockDataProcessor() {
        return stockItem -> {
            // StockItemDto를 StockDataDto로 변환
            StockDataDto stockDataDto = convertToStockDataDto(stockItem);
            
            // 데이터 검증
            List<String> validationErrors = validationService.validateStockData(stockDataDto);
            if (!validationErrors.isEmpty()) {
                throw new IllegalArgumentException("Validation failed for stock " + 
                    stockDataDto.getIsinCode() + ": " + String.join(", ", validationErrors));
            }
            
            Optional<Stock> existingStock = stockService.findByIsinCode(stockDataDto.getIsinCode());
            
            Stock stock;
            if (existingStock.isPresent()) {
                stock = existingStock.get();
                updateExistingStock(stock, stockDataDto);
            } else {
                stock = createNewStock(stockDataDto);
            }
            
            stockNameHistoryService.processNameChanges(stock, stockDataDto);
            
            if (stockDataDto.getPriceData() != null) {
                processPriceData(stock, stockDataDto.getPriceData());
            }
            
            return stock;
        };
    }
    
    private StockDataDto convertToStockDataDto(StockItemDto stockItem) {
        // basDt를 LocalDate로 변환 (20230103 -> 2023-01-03)
        LocalDate baseDate = LocalDate.parse(stockItem.getBasDt(), 
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 가격 데이터 생성
        StockPriceDataDto priceData = StockPriceDataDto.builder()
            .baseDate(baseDate)
            .openPrice(parseIntegerSafely(stockItem.getMkp()))      // 시가
            .closePrice(parseIntegerSafely(stockItem.getClpr()))     // 종가
            .highPrice(parseIntegerSafely(stockItem.getHipr()))      // 고가
            .lowPrice(parseIntegerSafely(stockItem.getLopr()))       // 저가
            .tradeQuantity(parseIntegerSafely(stockItem.getTrqu()))  // 거래량
            .tradeAmount(parseLongSafely(stockItem.getTrPrc()))      // 거래대금
            .issuedCount(parseLongSafely(stockItem.getLstgStCnt()))  // 상장주식수
            .build();
        
        return StockDataDto.builder()
            .isinCode(stockItem.getIsinCd())        // ISIN 코드
            .currentName(stockItem.getItmsNm())     // 종목명
            .shortCode(stockItem.getSrtnCd())       // 단축코드
            .marketCategory(stockItem.getMrktCtg()) // 시장구분
            .priceData(List.of(priceData))          // 가격 데이터 리스트
            .build();
    }
    
    private Integer parseIntegerSafely(String value) {
        try {
            return value != null && !value.trim().isEmpty() ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Long parseLongSafely(String value) {
        try {
            return value != null && !value.trim().isEmpty() ? Long.parseLong(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 파일명에서 날짜 정보를 추출합니다.
     * 예: "20220101.json" -> LocalDate.of(2022, 1, 1)
     */
    private LocalDate extractDateFromFileName(String fileName) {
        try {
            String baseName = new File(fileName).getName();
            String dateStr = baseName.replace(".json", "");
            
            if (dateStr.matches("\\d{8}")) { // 8자리 숫자인지 확인
                return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            
            return LocalDate.now(); // 기본값으로 오늘 날짜 사용
        } catch (Exception e) {
            return LocalDate.now(); // 파싱 실패 시 오늘 날짜 사용
        }
    }
    
    private void updateExistingStock(Stock stock, StockDataDto stockDataDto) {
        Stock updatedStock = Stock.builder()
                .id(stock.getId())
                .name(stockDataDto.getCurrentName())
                .shortCode(stockDataDto.getShortCode() != null ? stockDataDto.getShortCode() : stock.getShortCode())
                .isinCode(stock.getIsinCode())
                .marketCategory(stockDataDto.getMarketCategory() != null ? stockDataDto.getMarketCategory() : stock.getMarketCategory())
                .startAt(stockDataDto.getStartAt() != null ? stockDataDto.getStartAt() : stock.getStartAt())
                .endAt(stockDataDto.getEndAt() != null ? stockDataDto.getEndAt() : stock.getEndAt())
                .stockPriceList(stock.getStockPriceList())
                .calcStockPriceList(stock.getCalcStockPriceList())
                .nameHistoryList(stock.getNameHistoryList())
                .build();
                
        stock.getStockPriceList().clear();
        stock.getStockPriceList().addAll(updatedStock.getStockPriceList());
    }
    
    private Stock createNewStock(StockDataDto stockDataDto) {
        return Stock.builder()
                .name(stockDataDto.getCurrentName())
                .shortCode(stockDataDto.getShortCode())
                .isinCode(stockDataDto.getIsinCode())
                .marketCategory(stockDataDto.getMarketCategory())
                .startAt(stockDataDto.getStartAt() != null ? stockDataDto.getStartAt() : LocalDate.now())
                .endAt(stockDataDto.getEndAt())
                .stockPriceList(new ArrayList<>())
                .calcStockPriceList(new ArrayList<>())
                .nameHistoryList(new ArrayList<>())
                .build();
    }
    
    private void processPriceData(Stock stock, List<StockPriceDataDto> priceDataList) {
        for (StockPriceDataDto priceDto : priceDataList) {
            boolean exists = stock.getStockPriceList().stream()
                    .anyMatch(existing -> existing.getBaseDate().equals(priceDto.getBaseDate()));
            
            if (!exists) {
                StockPrice stockPrice = StockPrice.builder()
                        .baseDate(priceDto.getBaseDate())
                        .openPrice(priceDto.getOpenPrice())
                        .closePrice(priceDto.getClosePrice())
                        .highPrice(priceDto.getHighPrice())
                        .lowPrice(priceDto.getLowPrice())
                        .tradeQuantity(priceDto.getTradeQuantity())
                        .tradeAmount(priceDto.getTradeAmount())
                        .issuedCount(priceDto.getIssuedCount())
                        .stock(stock)
                        .build();
                
                stock.getStockPriceList().add(stockPrice);
            }
        }
    }

    @Bean
    public ItemWriter<Stock> stockDataWriter(EntityManagerFactory emf) {
        JpaItemWriter<Stock> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(emf);
        return writer;
    }

    @Bean
    public Step stockDataImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager txMgr,
            ItemReader<StockItemDto> reader,
            ItemProcessor<StockItemDto, Stock> processor,
            ItemWriter<Stock> writer
    ) {
        return new StepBuilder("stockDataImportStep", jobRepository)
                .<StockItemDto, Stock>chunk(10, txMgr)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job stockDataImportJob(JobRepository jobRepository, Step stockDataImportStep) {
        return new JobBuilder("stockDataImportJob", jobRepository)
                .start(stockDataImportStep)
                .build();
    }
}