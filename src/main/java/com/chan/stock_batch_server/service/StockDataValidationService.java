package com.chan.stock_batch_server.service;

import com.chan.stock_batch_server.dto.StockDataDto;
import com.chan.stock_batch_server.dto.StockNameHistoryDto;
import com.chan.stock_batch_server.dto.StockPriceDataDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class StockDataValidationService {
    
    private static final Pattern ISIN_PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");
    private static final int MAX_STOCK_NAME_LENGTH = 100;
    private static final int MAX_SHORT_CODE_LENGTH = 20;
    
    public List<String> validateStockData(StockDataDto stockData) {
        List<String> errors = new ArrayList<>();
        
        // ISIN 코드 검증
        if (stockData.getIsinCode() == null || stockData.getIsinCode().trim().isEmpty()) {
            errors.add("ISIN code is required");
        } else if (!ISIN_PATTERN.matcher(stockData.getIsinCode()).matches()) {
            errors.add("ISIN code must follow standard format (2 country letters + 9 alphanumeric + 1 check digit)");
        }
        
        // 주식명 검증
        if (stockData.getCurrentName() == null || stockData.getCurrentName().trim().isEmpty()) {
            errors.add("Stock name is required");
        } else if (stockData.getCurrentName().length() > MAX_STOCK_NAME_LENGTH) {
            errors.add("Stock name must not exceed " + MAX_STOCK_NAME_LENGTH + " characters");
        }
        
        // 단축코드 검증
        if (stockData.getShortCode() != null && stockData.getShortCode().length() > MAX_SHORT_CODE_LENGTH) {
            errors.add("Short code must not exceed " + MAX_SHORT_CODE_LENGTH + " characters");
        }
        
        // 날짜 검증
        if (stockData.getStartAt() != null && stockData.getEndAt() != null) {
            if (stockData.getStartAt().isAfter(stockData.getEndAt())) {
                errors.add("Start date must be before or equal to end date");
            }
        }
        
        // 주식명 이력 검증
        if (stockData.getNameHistory() != null) {
            for (int i = 0; i < stockData.getNameHistory().size(); i++) {
                final int index = i; // final 변수로 복사
                StockNameHistoryDto history = stockData.getNameHistory().get(i);
                List<String> historyErrors = validateNameHistory(history);
                historyErrors.forEach(error -> errors.add("Name history[" + index + "]: " + error));
            }
        }
        
        // 가격 데이터 검증
        if (stockData.getPriceData() != null) {
            for (int i = 0; i < stockData.getPriceData().size(); i++) {
                final int index = i; // final 변수로 복사
                StockPriceDataDto priceData = stockData.getPriceData().get(i);
                List<String> priceErrors = validatePriceData(priceData);
                priceErrors.forEach(error -> errors.add("Price data[" + index + "]: " + error));
            }
        }
        
        return errors;
    }
    
    private List<String> validateNameHistory(StockNameHistoryDto history) {
        List<String> errors = new ArrayList<>();
        
        if (history.getName() == null || history.getName().trim().isEmpty()) {
            errors.add("Name is required");
        } else if (history.getName().length() > MAX_STOCK_NAME_LENGTH) {
            errors.add("Name must not exceed " + MAX_STOCK_NAME_LENGTH + " characters");
        }
        
        if (history.getStartAt() == null) {
            errors.add("Start date is required");
        }
        
        if (history.getStartAt() != null && history.getEndAt() != null) {
            if (history.getStartAt().isAfter(history.getEndAt())) {
                errors.add("Start date must be before or equal to end date");
            }
        }
        
        // 미래 날짜 검증
        LocalDate now = LocalDate.now();
        if (history.getStartAt() != null && history.getStartAt().isAfter(now.plusYears(1))) {
            errors.add("Start date cannot be more than 1 year in the future");
        }
        
        return errors;
    }
    
    private List<String> validatePriceData(StockPriceDataDto priceData) {
        List<String> errors = new ArrayList<>();
        
        if (priceData.getBaseDate() == null) {
            errors.add("Base date is required");
        }
        
        // 가격 데이터는 0 이상이어야 함 (0 허용)
        if (priceData.getOpenPrice() != null && priceData.getOpenPrice() < 0) {
            errors.add("Open price cannot be negative");
        }
        
        if (priceData.getClosePrice() != null && priceData.getClosePrice() < 0) {
            errors.add("Close price cannot be negative");
        }
        
        if (priceData.getHighPrice() != null && priceData.getHighPrice() < 0) {
            errors.add("High price cannot be negative");
        }
        
        if (priceData.getLowPrice() != null && priceData.getLowPrice() < 0) {
            errors.add("Low price cannot be negative");
        }
        
        // 가격 관계 검증 (High >= Low, High >= Open/Close, Low <= Open/Close)
        // 0인 경우는 유효한 값으로 간주하여 검증에서 제외
        if (priceData.getHighPrice() != null && priceData.getLowPrice() != null && 
            priceData.getHighPrice() > 0 && priceData.getLowPrice() > 0) {
            if (priceData.getHighPrice() < priceData.getLowPrice()) {
                errors.add("High price must be greater than or equal to low price");
            }
        }
        
        if (priceData.getHighPrice() != null && priceData.getOpenPrice() != null && 
            priceData.getHighPrice() > 0 && priceData.getOpenPrice() > 0) {
            if (priceData.getHighPrice() < priceData.getOpenPrice()) {
                errors.add("High price must be greater than or equal to open price");
            }
        }
        
        if (priceData.getHighPrice() != null && priceData.getClosePrice() != null && 
            priceData.getHighPrice() > 0 && priceData.getClosePrice() > 0) {
            if (priceData.getHighPrice() < priceData.getClosePrice()) {
                errors.add("High price must be greater than or equal to close price");
            }
        }
        
        if (priceData.getLowPrice() != null && priceData.getOpenPrice() != null && 
            priceData.getLowPrice() > 0 && priceData.getOpenPrice() > 0) {
            if (priceData.getLowPrice() > priceData.getOpenPrice()) {
                errors.add("Low price must be less than or equal to open price");
            }
        }
        
        if (priceData.getLowPrice() != null && priceData.getClosePrice() != null && 
            priceData.getLowPrice() > 0 && priceData.getClosePrice() > 0) {
            if (priceData.getLowPrice() > priceData.getClosePrice()) {
                errors.add("Low price must be less than or equal to close price");
            }
        }
        
        // 거래량과 거래대금은 음수가 될 수 없음
        if (priceData.getTradeQuantity() != null && priceData.getTradeQuantity() < 0) {
            errors.add("Trade quantity cannot be negative");
        }
        
        if (priceData.getTradeAmount() != null && priceData.getTradeAmount() < 0) {
            errors.add("Trade amount cannot be negative");
        }
        
        if (priceData.getIssuedCount() != null && priceData.getIssuedCount() < 0) {
            errors.add("Issued count cannot be negative");
        }
        
        return errors;
    }
    
    public boolean isValidIsinCode(String isinCode) {
        return isinCode != null && ISIN_PATTERN.matcher(isinCode).matches();
    }
    
    public boolean isValidDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return true; // null은 허용
        }
        return !start.isAfter(end);
    }
}