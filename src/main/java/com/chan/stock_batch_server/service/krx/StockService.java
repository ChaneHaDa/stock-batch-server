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
	 * Stock 데이터 저장 (UPSERT 방식)
	 * 1. 모든 기존 Stock를 한 번에 조회
	 * 2. 신규 Stock만 bulk insert
	 * 3. 모든 StockPrice를 UPSERT (INSERT or UPDATE)
	 */
	@Transactional
	public void saveStockData(Map<String, List<KRXStockData>> allStockData) {
		if (allStockData.isEmpty()) {
			log.info("No stock data to save");
			return;
		}

		log.info("Starting bulk save for {} unique stocks (UPSERT mode)", allStockData.size());
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

		// 4. 모든 StockPrice를 UPSERT 처리
		log.info("Preparing StockPrice data for UPSERT...");

		// 4-1. 기존 모든 StockPrice 조회 (ID 포함) - 1번 쿼리
		List<StockPrice> allExistingPrices = stockPriceRepository.findAll();
		Map<String, StockPrice> existingPriceMap = allExistingPrices.stream()
			.collect(Collectors.toMap(
				p -> p.getStock().getId() + "_" + p.getBaseDate(),
				p -> p
			));
		log.info("Loaded {} existing stock prices from DB", existingPriceMap.size());

		// 4-2. INSERT/UPDATE 대상 준비
		List<StockPrice> allPricesToSave = new ArrayList<>();
		int insertCount = 0;
		int updateCount = 0;

		for (Map.Entry<String, List<KRXStockData>> entry : allStockData.entrySet()) {
			String isinCode = entry.getKey();
			Stock stock = existingStockMap.get(isinCode);

			if (stock == null) {
				log.warn("Stock not found for ISIN: {}", isinCode);
				continue;
			}

			for (KRXStockData data : entry.getValue()) {
				try {
					LocalDate baseDate = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);
					String key = stock.getId() + "_" + baseDate;

					StockPrice existingPrice = existingPriceMap.get(key);

					if (existingPrice != null) {
						// UPDATE: 기존 엔티티의 필드 업데이트 (ID 유지)
						existingPrice.setClosePrice(data.getClpr());
						existingPrice.setOpenPrice(data.getMkp());
						existingPrice.setHighPrice(data.getHipr());
						existingPrice.setLowPrice(data.getLopr());
						existingPrice.setTradeQuantity(data.getTrqu() != null ? data.getTrqu().intValue() : null);
						existingPrice.setTradeAmount(data.getTrPrc());
						existingPrice.setIssuedCount(data.getLstgStCnt());

						allPricesToSave.add(existingPrice);
						updateCount++;
					} else {
						// INSERT: 새 엔티티 생성 (ID null)
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
						insertCount++;
					}

				} catch (Exception e) {
					log.error("Error processing stock price for {}: {}", isinCode, e.getMessage());
				}
			}
		}

		// 5. 모든 StockPrice bulk save (JPA가 자동으로 INSERT/UPDATE 구분) - 1번의 쿼리
		if (!allPricesToSave.isEmpty()) {
			log.info("Bulk saving {} stock prices ({} inserts, {} updates)",
				allPricesToSave.size(), insertCount, updateCount);
			stockPriceRepository.saveAll(allPricesToSave);
			stockPriceRepository.flush();
		}

		long endTime = System.currentTimeMillis();
		log.info("Stock data bulk save completed: {} stocks, {} prices in {}ms",
			existingStockMap.size(), allPricesToSave.size(), (endTime - startTime));
	}
}
