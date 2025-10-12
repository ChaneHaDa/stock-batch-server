package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	 * Stock 데이터 저장 (Proper Bulk Processing)
	 * 1. 모든 기존 Stock를 한 번에 조회
	 * 2. 신규 Stock만 bulk insert
	 * 3. 모든 StockPrice를 bulk insert
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

		// 4. 모든 StockPrice 생성 및 bulk insert
		log.info("Preparing StockPrice data...");
		List<StockPrice> allPricesToSave = new ArrayList<>();

		for (Map.Entry<String, List<KRXStockData>> entry : allStockData.entrySet()) {
			String isinCode = entry.getKey();
			Stock stock = existingStockMap.get(isinCode);

			if (stock == null) {
				log.warn("Stock not found for ISIN: {}", isinCode);
				continue;
			}

			// Stock의 기존 가격 데이터 조회 (각 Stock당 1번)
			Set<LocalDate> existingDates = stockPriceRepository
				.findByStockIdOrderByBaseDate(stock.getId())
				.stream()
				.map(StockPrice::getBaseDate)
				.collect(Collectors.toSet());

			// 신규 가격 데이터만 필터링
			for (KRXStockData data : entry.getValue()) {
				try {
					LocalDate baseDate = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);

					if (existingDates.contains(baseDate)) {
						continue; // 중복 스킵
					}

					StockPrice price = StockPrice.builder()
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

					allPricesToSave.add(price);

				} catch (Exception e) {
					log.error("Error parsing stock price for {}: {}", isinCode, e.getMessage());
				}
			}
		}

		// 5. 모든 StockPrice bulk insert (1번의 대량 쿼리)
		if (!allPricesToSave.isEmpty()) {
			log.info("Bulk inserting {} stock prices", allPricesToSave.size());
			stockPriceRepository.saveAll(allPricesToSave);
			stockPriceRepository.flush();
		}

		long endTime = System.currentTimeMillis();
		log.info("Stock data bulk save completed: {} stocks, {} prices in {}ms",
			existingStockMap.size(), allPricesToSave.size(), (endTime - startTime));
	}
}
