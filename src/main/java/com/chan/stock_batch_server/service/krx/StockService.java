package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
	 * Stock 데이터 저장
	 */
	public void saveStockData(Map<String, List<KRXStockData>> allStockData) {
		log.info("Saving stock data for {} unique stocks", allStockData.size());

		for (Map.Entry<String, List<KRXStockData>> entry : allStockData.entrySet()) {
			String isinCode = entry.getKey();
			List<KRXStockData> stockDataList = entry.getValue();

			try {
				// Stock 기본 정보 저장 또는 업데이트
				Stock stock = saveOrUpdateStock(isinCode, stockDataList);

				// StockPrice 데이터 저장
				saveStockPrices(stock, stockDataList);

			} catch (Exception e) {
				log.error("Error saving stock data for ISIN: {}", isinCode, e);
			}
		}

		log.info("Stock data save completed");
	}

	/**
	 * Stock 기본 정보 저장 또는 업데이트
	 */
	@Transactional
	public Stock saveOrUpdateStock(String isinCode, List<KRXStockData> stockDataList) {
		Optional<Stock> existingStock = stockRepository.findByIsinCode(isinCode);

		if (existingStock.isPresent()) {
			return existingStock.get();
		}

		// 첫 번째 데이터로 Stock 생성
		KRXStockData firstData = stockDataList.get(0);
		Stock stock = Stock.builder()
			.isinCode(isinCode)
			.shortCode(firstData.getSrtnCd())
			.name(firstData.getItmsNm())
			.marketCategory(firstData.getMrktCtg())
			.startAt(LocalDate.parse(firstData.getBasDt(), DATE_FORMATTER))
			.build();

		return stockRepository.saveAndFlush(stock);
	}

	/**
	 * StockPrice 데이터 저장
	 */
	@Transactional
	public void saveStockPrices(Stock stock, List<KRXStockData> stockDataList) {
		for (KRXStockData data : stockDataList) {
			try {
				StockPrice stockPrice = StockPrice.builder()
					.stock(stock)
					.baseDate(LocalDate.parse(data.getBasDt(), DATE_FORMATTER))
					.closePrice(data.getClpr())
					.openPrice(data.getMkp())
					.highPrice(data.getHipr())
					.lowPrice(data.getLopr())
					.tradeQuantity(data.getTrqu() != null ? data.getTrqu().intValue() : null)
					.tradeAmount(data.getTrPrc())
					.issuedCount(data.getLstgStCnt())
					.build();

				stockPriceRepository.save(stockPrice);

			} catch (Exception e) {
				log.error("Error saving stock price for stock {} on date {}",
					stock.getIsinCode(), data.getBasDt(), e);
			}
		}
		stockPriceRepository.flush();
	}
}
