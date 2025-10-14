package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chan.stock_batch_server.migration.dto.KRXStockData;
import com.chan.stock_batch_server.model.Stock;
import com.chan.stock_batch_server.model.StockPrice;
import com.chan.stock_batch_server.repository.StockPriceRepository;
import com.chan.stock_batch_server.repository.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
	private final StockRepository stockRepository;
	private final StockPriceRepository stockPriceRepository;

	/**
	 * Stock 데이터 저장 (중복 체크 방식)
	 * 1. 모든 기존 Stock를 한 번에 조회
	 * 2. 신규 Stock만 bulk insert
	 * 3. Stock별로 기존 날짜 조회 후 중복 없는 데이터만 INSERT
	 */
	@Transactional
	public void saveStockData(Map<String, List<KRXStockData>> allStockData) {
		if (allStockData.isEmpty()) {
			log.info("No stock data to save");
			return;
		}

		log.info("Starting bulk save for {} unique stocks", allStockData.size());
		long startTime = System.currentTimeMillis();

		// 1. 기존 모든 Stock 조회 (1번의 쿼리)
		List<Stock> allExistingStocks = stockRepository.findAll();
		Map<String, Stock> existingStockMap = allExistingStocks.stream()
			.collect(Collectors.toMap(Stock::getIsinCode, s -> s));

		log.info("Loaded {} existing stocks from DB", existingStockMap.size());

		// 2. 신규 Stock 필터링 및 생성
		List<Stock> newStocks = new ArrayList<>();
		for (Map.Entry<String, List<KRXStockData>> entry : allStockData.entrySet()) {
			String isinCode = entry.getKey();
			if (!existingStockMap.containsKey(isinCode)) {
				KRXStockData firstData = entry.getValue().get(0);
				Stock stock = Stock.builder()
					.isinCode(isinCode)
					.shortCode(firstData.getSrtnCd())
					.name(firstData.getItmsNm())
					.marketCategory(firstData.getMrktCtg())
					.build();
				newStocks.add(stock);
			}
		}

		// 3. 신규 Stock bulk insert (1번의 쿼리)
		if (!newStocks.isEmpty()) {
			log.info("Bulk inserting {} new stocks", newStocks.size());
			stockRepository.saveAll(newStocks);
			stockRepository.flush();

			// 새로 저장된 Stock을 Map에 추가
			for (Stock stock : newStocks) {
				existingStockMap.put(stock.getIsinCode(), stock);
			}
		}

		// 4. StockPrice 저장 (중복 체크 후 INSERT만)
		log.info("Preparing StockPrice data...");

		// 4-1. 임포트할 데이터의 날짜 범위 추출
		LocalDate minDate = null;
		LocalDate maxDate = null;
		for (List<KRXStockData> dataList : allStockData.values()) {
			for (KRXStockData data : dataList) {
				try {
					LocalDate date = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);
					if (minDate == null || date.isBefore(minDate))
						minDate = date;
					if (maxDate == null || date.isAfter(maxDate))
						maxDate = date;
				} catch (Exception e) {
					// Skip invalid dates
				}
			}
		}

		if (minDate == null || maxDate == null) {
			log.warn("No valid dates found in import data");
			return;
		}

		log.info("Import date range: {} to {}", minDate, maxDate);

		// 4-2. 해당 월의 기존 데이터만 조회 (1번 쿼리!)
		List<StockPrice> monthPrices = stockPriceRepository.findByBaseDateBetween(minDate, maxDate);
		log.info("Loaded {} existing prices for date range", monthPrices.size());

		// 4-3. HashMap으로 stock별 날짜 그룹핑 (메모리에서 처리)
		Map<Integer, java.util.Set<LocalDate>> existingDatesByStock = monthPrices.stream()
			.collect(Collectors.groupingBy(
				p -> p.getStock().getId(),
				Collectors.mapping(StockPrice::getBaseDate, Collectors.toSet())
			));

		// 4-4. 중복 체크 및 신규 데이터만 준비
		List<StockPrice> allPricesToSave = new ArrayList<>();
		int skippedCount = 0;

		for (Map.Entry<String, List<KRXStockData>> entry : allStockData.entrySet()) {
			String isinCode = entry.getKey();
			Stock stock = existingStockMap.get(isinCode);

			if (stock == null) {
				log.warn("Stock not found for ISIN: {}", isinCode);
				continue;
			}

			// HashMap에서 해당 Stock의 기존 날짜 조회 (DB 쿼리 없음!)
			java.util.Set<LocalDate> existingDates = existingDatesByStock.getOrDefault(
				stock.getId(),
				java.util.Collections.emptySet()
			);

			for (KRXStockData data : entry.getValue()) {
				try {
					LocalDate baseDate = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);

					// 중복 체크 - 이미 존재하면 SKIP
					if (existingDates.contains(baseDate)) {
						skippedCount++;
						continue;
					}

					// 신규 데이터만 INSERT
					StockPrice newPrice = StockPrice.builder()
						.stock(stock)
						.baseDate(baseDate)
						.closePrice(data.getClpr())
						.openPrice(data.getMkp())
						.highPrice(data.getHipr())
						.lowPrice(data.getLopr())
						.tradeQuantity(data.getTrqu() != null ? data.getTrqu().intValue() : null)
						.tradeAmount(data.getTrPrc())
						.issuedCount(data.getLstgStCnt())
						.build();

					allPricesToSave.add(newPrice);

				} catch (Exception e) {
					log.error("Error processing stock price for {}: {}", isinCode, e.getMessage());
				}
			}
		}

		// 5. 신규 StockPrice bulk insert
		if (!allPricesToSave.isEmpty()) {
			log.info("Bulk inserting {} stock prices ({} skipped)",
				allPricesToSave.size(), skippedCount);
			stockPriceRepository.saveAll(allPricesToSave);
			stockPriceRepository.flush();
		}

		long endTime = System.currentTimeMillis();
		log.info("Stock data bulk save completed: {} stocks, {} prices in {}ms",
			existingStockMap.size(), allPricesToSave.size(), (endTime - startTime));
	}
}
