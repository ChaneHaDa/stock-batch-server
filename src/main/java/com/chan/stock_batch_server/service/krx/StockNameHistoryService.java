package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	 * 모든 주식의 이름 변경 이력 생성
	 */
	@Transactional
	public void generateAllStockNameHistories(Map<String, List<KRXStockData>> allStockData) {
		log.info("Starting stock name history generation for {} stocks", allStockData.size());

		for (Map.Entry<String, List<KRXStockData>> entry : allStockData.entrySet()) {
			String isinCode = entry.getKey();
			List<KRXStockData> stockDataList = entry.getValue();

			try {
				List<StockNameHistory> histories = analyzeNameChanges(isinCode, stockDataList);
				saveStockNameHistories(isinCode, histories);

				log.debug("Generated {} name history records for ISIN: {}", histories.size(), isinCode);
			} catch (Exception e) {
				log.error("Error processing name history for ISIN: {}", isinCode, e);
			}
		}

		log.info("Stock name history generation completed");
	}

	/**
	 * ISIN 코드별 이름 변경 이력 분석 (핵심 알고리즘)
	 */
	public List<StockNameHistory> analyzeNameChanges(String isinCode, List<KRXStockData> stockDataList) {
		// 1. 시간순 정렬
		stockDataList.sort(Comparator.comparing(data ->
			LocalDate.parse(data.getBasDt(), DATE_FORMATTER)));

		// 2. 이름별 첫 등장일 추적
		Map<String, LocalDate> nameFirstAppearance = new LinkedHashMap<>();

		for (KRXStockData data : stockDataList) {
			String stockName = data.getItmsNm();
			LocalDate date = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);

			nameFirstAppearance.putIfAbsent(stockName, date);
		}

		// 3. 이름 변경 이력 생성
		return createHistoryRecords(isinCode, nameFirstAppearance);
	}

	/**
	 * 이름 변경 이력 레코드 생성 (start_at, end_at 정확한 계산)
	 */
	private List<StockNameHistory> createHistoryRecords(String isinCode,
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

			histories.add(StockNameHistory.builder()
				.name(name)
				.startAt(startDate)
				.endAt(endDate)
				.build());
		}

		return histories;
	}

	/**
	 * StockNameHistory 저장
	 */
	@Transactional
	public void saveStockNameHistories(String isinCode, List<StockNameHistory> histories) {
		// Stock 조회
		Optional<Stock> stockOpt = stockRepository.findByIsinCode(isinCode);
		if (stockOpt.isEmpty()) {
			log.warn("Stock not found for ISIN: {}", isinCode);
			return;
		}

		Stock stock = stockOpt.get();

		// 기존 이력 삭제 (중복 방지)
		stockNameHistoryRepository.deleteByStockId(stock.getId());

		// 새로운 이력 저장
		for (StockNameHistory history : histories) {
			history.setStock(stock);
			stockNameHistoryRepository.save(history);
		}

		log.debug("Saved {} name history records for stock: {}", histories.size(), stock.getName());
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
