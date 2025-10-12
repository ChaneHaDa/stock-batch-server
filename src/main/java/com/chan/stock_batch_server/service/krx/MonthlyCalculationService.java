package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chan.stock_batch_server.model.CalcIndexPrice;
import com.chan.stock_batch_server.model.CalcStockPrice;
import com.chan.stock_batch_server.model.IndexInfo;
import com.chan.stock_batch_server.model.IndexPrice;
import com.chan.stock_batch_server.model.Stock;
import com.chan.stock_batch_server.model.StockPrice;
import com.chan.stock_batch_server.repository.CalcIndexPriceRepository;
import com.chan.stock_batch_server.repository.CalcStockPriceRepository;
import com.chan.stock_batch_server.repository.IndexInfoRepository;
import com.chan.stock_batch_server.repository.IndexPriceRepository;
import com.chan.stock_batch_server.repository.StockPriceRepository;
import com.chan.stock_batch_server.repository.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyCalculationService {

	private final StockRepository stockRepository;
	private final StockPriceRepository stockPriceRepository;
	private final CalcStockPriceRepository calcStockPriceRepository;
	private final IndexInfoRepository indexInfoRepository;
	private final IndexPriceRepository indexPriceRepository;
	private final CalcIndexPriceRepository calcIndexPriceRepository;

	/**
	 * 특정 월의 주식 및 지수 월별 계산 수행
	 */
	@Transactional
	public void calculateMonthlyData(int year, int month) {
		log.info("Starting monthly calculation for {}-{}", year, month);

		YearMonth yearMonth = YearMonth.of(year, month);
		LocalDate startDate = yearMonth.atDay(1);
		LocalDate endDate = yearMonth.atEndOfMonth();

		// Calculate stock prices
		calculateStockPrices(startDate, endDate);

		// Calculate index prices
		calculateIndexPrices(startDate, endDate);

		log.info("Monthly calculation completed for {}-{}", year, month);
	}

	/**
	 * 주식 월별 가격 계산
	 */
	private void calculateStockPrices(LocalDate startDate, LocalDate endDate) {
		List<Stock> stocks = stockRepository.findAll();
		log.info("Calculating monthly prices for {} stocks", stocks.size());

		int calculatedCount = 0;
		int skippedCount = 0;

		for (Stock stock : stocks) {
			try {
				// Check if already calculated
				if (calcStockPriceRepository.existsByStockAndBaseDate(stock, endDate)) {
					skippedCount++;
					continue;
				}

				// Find prices in the month
				List<StockPrice> monthPrices = stockPriceRepository.findByStockIdOrderByBaseDate(stock.getId())
					.stream()
					.filter(p -> !p.getBaseDate().isBefore(startDate) && !p.getBaseDate().isAfter(endDate))
					.toList();

				if (monthPrices.isEmpty()) {
					continue;
				}

				// Get last trading day's close price
				StockPrice lastPrice = monthPrices.get(monthPrices.size() - 1);
				Integer closePrice = lastPrice.getClosePrice();

				if (closePrice == null) {
					continue;
				}

				// Calculate monthly rate of return
				Float monthlyRor = calculateStockMonthlyRor(stock, endDate, closePrice);

				// Save calculated price
				CalcStockPrice calcPrice = CalcStockPrice.builder()
					.stock(stock)
					.baseDate(endDate)
					.price(closePrice.floatValue())
					.monthlyRor(monthlyRor)
					.build();

				calcStockPriceRepository.save(calcPrice);
				calculatedCount++;

			} catch (Exception e) {
				log.error("Error calculating monthly price for stock {}", stock.getIsinCode(), e);
			}
		}

		log.info("Stock calculation complete: {} calculated, {} skipped", calculatedCount, skippedCount);
	}

	/**
	 * 지수 월별 가격 계산
	 */
	private void calculateIndexPrices(LocalDate startDate, LocalDate endDate) {
		List<IndexInfo> indices = indexInfoRepository.findAll();
		log.info("Calculating monthly prices for {} indices", indices.size());

		int calculatedCount = 0;
		int skippedCount = 0;

		for (IndexInfo indexInfo : indices) {
			try {
				// Check if already calculated
				if (calcIndexPriceRepository.existsByIndexInfoAndBaseDate(indexInfo, endDate)) {
					skippedCount++;
					continue;
				}

				// Find prices in the month
				List<IndexPrice> monthPrices = indexInfo.getIndexPriceList()
					.stream()
					.filter(p -> !p.getBaseDate().isBefore(startDate) && !p.getBaseDate().isAfter(endDate))
					.toList();

				if (monthPrices.isEmpty()) {
					continue;
				}

				// Get last trading day's close price
				IndexPrice lastPrice = monthPrices.get(monthPrices.size() - 1);
				Float closePrice = lastPrice.getClosePrice();

				if (closePrice == null) {
					continue;
				}

				// Calculate monthly rate of return
				Float monthlyRor = calculateIndexMonthlyRor(indexInfo, endDate, closePrice);

				// Save calculated price
				CalcIndexPrice calcPrice = CalcIndexPrice.builder()
					.indexInfo(indexInfo)
					.baseDate(endDate)
					.price(closePrice)
					.monthlyRor(monthlyRor)
					.build();

				calcIndexPriceRepository.save(calcPrice);
				calculatedCount++;

			} catch (Exception e) {
				log.error("Error calculating monthly price for index {}", indexInfo.getName(), e);
			}
		}

		log.info("Index calculation complete: {} calculated, {} skipped", calculatedCount, skippedCount);
	}

	/**
	 * 주식 월별 수익률 계산
	 */
	private Float calculateStockMonthlyRor(Stock stock, LocalDate currentDate, Integer currentPrice) {
		Optional<CalcStockPrice> previousOpt = calcStockPriceRepository.findPreviousMonthPrice(stock, currentDate);

		if (previousOpt.isEmpty()) {
			return null; // First month, no previous data
		}

		Float previousPrice = previousOpt.get().getPrice();
		if (previousPrice == null || previousPrice == 0) {
			return null;
		}

		// Monthly RoR = (Current Price - Previous Price) / Previous Price * 100
		return ((currentPrice - previousPrice) / previousPrice) * 100;
	}

	/**
	 * 지수 월별 수익률 계산
	 */
	private Float calculateIndexMonthlyRor(IndexInfo indexInfo, LocalDate currentDate, Float currentPrice) {
		Optional<CalcIndexPrice> previousOpt = calcIndexPriceRepository.findPreviousMonthPrice(indexInfo, currentDate);

		if (previousOpt.isEmpty()) {
			return null; // First month, no previous data
		}

		Float previousPrice = previousOpt.get().getPrice();
		if (previousPrice == null || previousPrice == 0) {
			return null;
		}

		// Monthly RoR = (Current Price - Previous Price) / Previous Price * 100
		return ((currentPrice - previousPrice) / previousPrice) * 100;
	}
}
