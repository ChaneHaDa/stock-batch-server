package com.chan.stock_batch_server.service;

import com.chan.stock_batch_server.dto.StockDataDto;
import com.chan.stock_batch_server.dto.StockNameHistoryDto;
import com.chan.stock_batch_server.model.Stock;
import com.chan.stock_batch_server.model.StockNameHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StockNameHistoryService {

    public String getCurrentValidName(Stock stock) {
        if (stock.getNameHistoryList().isEmpty()) {
            return stock.getName();
        }
        
        LocalDate now = LocalDate.now();
        return stock.getNameHistoryList().stream()
                .filter(h -> h.getStartAt().isBefore(now.plusDays(1)) && 
                           (h.getEndAt() == null || h.getEndAt().isAfter(now.minusDays(1))))
                .findFirst()
                .map(StockNameHistory::getName)
                .orElse(stock.getName());
    }

    public void processNameChanges(Stock stock, StockDataDto stockData) {
        String currentName = getCurrentValidName(stock);
        
        if (!currentName.equals(stockData.getCurrentName())) {
            closeCurrentNameHistory(stock);
            createNewNameHistory(stock, stockData.getCurrentName());
        }
        
        if (stockData.getNameHistory() != null && !stockData.getNameHistory().isEmpty()) {
            processNameHistoryList(stock, stockData.getNameHistory());
        }
    }

    private void closeCurrentNameHistory(Stock stock) {
        LocalDate now = LocalDate.now();
        stock.getNameHistoryList().stream()
                .filter(h -> h.getEndAt() == null)
                .forEach(h -> {
                    StockNameHistory updatedHistory = StockNameHistory.builder()
                            .id(h.getId())
                            .name(h.getName())
                            .startAt(h.getStartAt())
                            .endAt(now.minusDays(1))
                            .stock(stock)
                            .build();
                    stock.getNameHistoryList().remove(h);
                    stock.getNameHistoryList().add(updatedHistory);
                });
    }

    private void createNewNameHistory(Stock stock, String newName) {
        LocalDate now = LocalDate.now();
        StockNameHistory newHistory = StockNameHistory.builder()
                .name(newName)
                .startAt(now)
                .endAt(null)
                .stock(stock)
                .build();
        
        stock.getNameHistoryList().add(newHistory);
        
        Stock updatedStock = Stock.builder()
                .id(stock.getId())
                .name(newName)
                .shortCode(stock.getShortCode())
                .isinCode(stock.getIsinCode())
                .marketCategory(stock.getMarketCategory())
                .startAt(stock.getStartAt())
                .endAt(stock.getEndAt())
                .stockPriceList(stock.getStockPriceList())
                .calcStockPriceList(stock.getCalcStockPriceList())
                .nameHistoryList(stock.getNameHistoryList())
                .build();
    }

    private void processNameHistoryList(Stock stock, List<StockNameHistoryDto> nameHistoryList) {
        for (StockNameHistoryDto historyDto : nameHistoryList) {
            if (!isDuplicateHistory(stock, historyDto)) {
                StockNameHistory history = StockNameHistory.builder()
                        .name(historyDto.getName())
                        .startAt(historyDto.getStartAt())
                        .endAt(historyDto.getEndAt())
                        .stock(stock)
                        .build();
                stock.getNameHistoryList().add(history);
            }
        }
    }

    private boolean isDuplicateHistory(Stock stock, StockNameHistoryDto historyDto) {
        return stock.getNameHistoryList().stream()
                .anyMatch(existing -> 
                    existing.getName().equals(historyDto.getName()) &&
                    existing.getStartAt().equals(historyDto.getStartAt()) &&
                    ((existing.getEndAt() == null && historyDto.getEndAt() == null) ||
                     (existing.getEndAt() != null && existing.getEndAt().equals(historyDto.getEndAt())))
                );
    }

    public boolean isValidNameHistory(StockNameHistoryDto history) {
        if (history.getName() == null || history.getName().trim().isEmpty()) {
            return false;
        }
        
        if (history.getStartAt() == null) {
            return false;
        }
        
        if (history.getEndAt() != null && history.getStartAt().isAfter(history.getEndAt())) {
            return false;
        }
        
        return true;
    }

    public List<StockNameHistory> getNameHistoryByStock(Stock stock) {
        return stock.getNameHistoryList().stream()
                .sorted((h1, h2) -> h2.getStartAt().compareTo(h1.getStartAt()))
                .toList();
    }
}