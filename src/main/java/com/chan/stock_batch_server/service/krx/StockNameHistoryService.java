package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chan.stock_batch_server.migration.dto.KRXStockData;
import com.chan.stock_batch_server.model.Stock;
import com.chan.stock_batch_server.model.StockNameHistory;
import com.chan.stock_batch_server.repository.StockNameHistoryRepository;
import com.chan.stock_batch_server.repository.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockNameHistoryService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
	private final StockRepository stockRepository;
	private final StockNameHistoryRepository stockNameHistoryRepository;

	/**
	 * 모든 주식의 이름 변경 이력 재생성 (JSON 데이터 기반)
	 * JSON 파일의 KRXStockData를 직접 사용하여 전체 이력을 재계산합니다.
	 */
	@Transactional
	public void regenerateAllStockNameHistories(Map<String, List<KRXStockData>> allStockData) {
		log.info("Starting stock name history regeneration from KRXStockData");
		long startTime = System.currentTimeMillis();

		// 1. 모든 Stock 로드 (1 query)
		List<Stock> allStocks = stockRepository.findAll();
		Map<String, Stock> stockMap = allStocks.stream()
			.collect(Collectors.toMap(Stock::getIsinCode, s -> s));
		log.info("Loaded {} stocks from DB", allStocks.size());

		// 2. 모든 이력을 메모리에서 생성
		List<StockNameHistory> allHistories = new ArrayList<>();
		int processedCount = 0;
		int skippedCount = 0;

		for (Map.Entry<String, List<KRXStockData>> entry : allStockData.entrySet()) {
			try {
				String isinCode = entry.getKey();
				Stock stock = stockMap.get(isinCode);

				if (stock == null) {
					log.warn("Stock not found for ISIN: {}", isinCode);
					skippedCount++;
					continue;
				}

				List<KRXStockData> stockDataList = entry.getValue();
				if (stockDataList.isEmpty()) {
					skippedCount++;
					continue;
				}

				// KRX 데이터로부터 이름 변경 이력 분석
				List<StockNameHistory> histories = analyzeNameChangesFromKRXData(stock, stockDataList);
				allHistories.addAll(histories);
				processedCount++;

				if (processedCount % 500 == 0) {
					log.info("Processed {}/{} stocks", processedCount, allStockData.size());
				}

			} catch (Exception e) {
				log.error("Error processing name history for stock: {}", e.getMessage());
			}
		}

		// 3. 기존 이력 전체 삭제 (1 query)
		stockNameHistoryRepository.deleteAllInBatch();

		// 4. 모든 이력 벌크 INSERT (1 query)
		if (!allHistories.isEmpty()) {
			stockNameHistoryRepository.saveAll(allHistories);
			stockNameHistoryRepository.flush();
		}

		long endTime = System.currentTimeMillis();
		log.info("Stock name history regeneration completed: {} stocks processed, {} skipped, {} histories in {}ms",
			processedCount, skippedCount, allHistories.size(), (endTime - startTime));
	}

	/**
	 * KRXStockData로부터 이름 변경 이력 분석
	 *
	 * @param stock         주식 엔티티
	 * @param stockDataList 해당 주식의 모든 KRX 데이터 (시간순 정렬 필요)
	 * @return 이름 변경 이력 리스트
	 */
	private List<StockNameHistory> analyzeNameChangesFromKRXData(Stock stock, List<KRXStockData> stockDataList) {
		// 1. 날짜순 정렬
		List<KRXStockData> sortedData = stockDataList.stream()
			.sorted(Comparator.comparing(KRXStockData::getBasDt))
			.toList();

		// 2. 이름별 첫 등장일 추적
		Map<String, LocalDate> nameFirstAppearance = new LinkedHashMap<>();

		for (KRXStockData data : sortedData) {
			String stockName = data.getItmsNm();
			if (stockName != null && !stockName.isEmpty()) {
				try {
					LocalDate baseDate = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);
					nameFirstAppearance.putIfAbsent(stockName, baseDate);
				} catch (Exception e) {
					log.error("Error parsing date for stock {}: {}", stock.getIsinCode(), e.getMessage());
				}
			}
		}

		// 3. 이름 변경 이력 레코드 생성
		return createHistoryRecords(stock, nameFirstAppearance);
	}

	/**
	 * 이름 변경 이력 레코드 생성 (start_at, end_at 정확한 계산)
	 *
	 * @param stock     주식 엔티티
	 * @param nameDates 이름별 첫 등장일 맵
	 * @return StockNameHistory 리스트
	 */
	private List<StockNameHistory> createHistoryRecords(Stock stock,
		Map<String, LocalDate> nameDates) {
		List<StockNameHistory> histories = new ArrayList<>();

		// 날짜순 정렬
		List<Map.Entry<String, LocalDate>> sortedEntries = nameDates.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue())
			.collect(Collectors.toList());

		for (int i = 0; i < sortedEntries.size(); i++) {
			String name = sortedEntries.get(i).getKey();
			LocalDate startDate = sortedEntries.get(i).getValue();
			LocalDate endDate = (i + 1 < sortedEntries.size()) ?
				sortedEntries.get(i + 1).getValue() : null;

			StockNameHistory history = StockNameHistory.builder()
				.stock(stock)
				.name(name)
				.startAt(startDate)
				.endAt(endDate)
				.build();

			histories.add(history);
		}

		return histories;
	}

	/**
	 * 이름 변경 이력 검증
	 */
	public List<String> validateNameHistories() {
		List<String> issues = new ArrayList<>();

		List<StockNameHistory> allHistories = stockNameHistoryRepository.findAll();

		// 1. 시간 간격 검증
		for (StockNameHistory history : allHistories) {
			if (history.getEndAt() != null &&
				!history.getStartAt().isBefore(history.getEndAt())) {
				issues.add(String.format("Invalid period for stock %s: %s to %s",
					history.getStock().getName(), history.getStartAt(), history.getEndAt()));
			}
		}

		// 2. 현재 이름 수 확인
		long currentNamesCount = allHistories.stream()
			.filter(h -> h.getEndAt() == null)
			.count();

		long totalStocksCount = stockRepository.count();
		if (currentNamesCount != totalStocksCount) {
			issues.add(String.format("Current names count mismatch: %d histories vs %d stocks",
				currentNamesCount, totalStocksCount));
		}

		return issues;
	}
}
