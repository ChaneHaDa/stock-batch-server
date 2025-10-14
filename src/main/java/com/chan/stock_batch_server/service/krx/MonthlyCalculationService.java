package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
	 * 특정 월의 주식 및 지수 월별 계산 수행 (Bulk Optimized)
	 */
	@Transactional
	public void calculateMonthlyData(int year, int month) {
		log.info("Starting monthly calculation for {}-{} (bulk optimized)", year, month);
		long startTime = System.currentTimeMillis();

		YearMonth yearMonth = YearMonth.of(year, month);
		LocalDate startDate = yearMonth.atDay(1);
		LocalDate endDate = yearMonth.atEndOfMonth();

		// Calculate stock prices
		calculateStockPrices(startDate, endDate);

		// Calculate index prices
		calculateIndexPrices(startDate, endDate);

		long endTime = System.currentTimeMillis();
		log.info("Monthly calculation completed for {}-{} in {}ms", year, month, (endTime - startTime));
	}

	/**
	 * 주식 월별 가격 계산 (Bulk Optimized with HashMap)
	 */
	private void calculateStockPrices(LocalDate startDate, LocalDate endDate) {
		log.info("Starting bulk stock calculation...");
		long stepStart = System.currentTimeMillis();

		// 1. Load all stocks (1 query)
		List<Stock> stocks = stockRepository.findAll();
		log.info("Loaded {} stocks", stocks.size());

		// 2. Load all stock prices (1 query)
		List<StockPrice> allPrices = stockPriceRepository.findAll();
		log.info("Loaded {} stock prices", allPrices.size());

		// 3. Group prices by stock_id (in-memory HashMap)
		Map<Integer, List<StockPrice>> pricesByStock = allPrices.stream()
			.collect(Collectors.groupingBy(
				p -> p.getStock().getId(),
				Collectors.collectingAndThen(
					Collectors.toList(),
					list -> {
						list.sort(Comparator.comparing(StockPrice::getBaseDate));
						return list;
					}
				)
			));
		log.info("Grouped prices for {} stocks", pricesByStock.size());

		// 4. Load all existing CalcStockPrice for duplicate check (1 query)
		List<CalcStockPrice> existingCalcs = calcStockPriceRepository.findAll();
		Map<String, CalcStockPrice> existingCalcMap = existingCalcs.stream()
			.collect(Collectors.toMap(
				c -> c.getStock().getId() + "_" + c.getBaseDate(),
				c -> c
			));
		log.info("Loaded {} existing calc prices", existingCalcs.size());

		// 5. Group CalcStockPrice by stock_id for previous month lookup (in-memory HashMap)
		Map<Integer, List<CalcStockPrice>> calcsByStock = existingCalcs.stream()
			.collect(Collectors.groupingBy(
				c -> c.getStock().getId(),
				Collectors.collectingAndThen(
					Collectors.toList(),
					list -> {
						list.sort(Comparator.comparing(CalcStockPrice::getBaseDate));
						return list;
					}
				)
			));

		// 6. Calculate in memory with UPSERT
		List<CalcStockPrice> allCalcsToSave = new ArrayList<>();
		int insertCount = 0;
		int updateCount = 0;

		for (Stock stock : stocks) {
			try {
				String key = stock.getId() + "_" + endDate;

				// Get prices for this stock from HashMap (no DB!)
				List<StockPrice> stockPrices = pricesByStock.getOrDefault(stock.getId(), new ArrayList<>());

				// Filter prices in the month range
				List<StockPrice> monthPrices = stockPrices.stream()
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

				// Calculate monthly rate of return (from HashMap, no DB!)
				Float monthlyRor = calculateStockMonthlyRor(stock, endDate, closePrice, calcsByStock);

				CalcStockPrice existingCalc = existingCalcMap.get(key);

				if (existingCalc != null) {
					// UPDATE: 기존 엔티티의 필드 업데이트 (ID 유지)
					existingCalc.setPrice(closePrice.floatValue());
					existingCalc.setMonthlyRor(monthlyRor);

					allCalcsToSave.add(existingCalc);
					updateCount++;
				} else {
					// INSERT: 새 엔티티 생성 (ID null)
					CalcStockPrice newCalc = CalcStockPrice.builder()
						.stock(stock)
						.baseDate(endDate)
						.price(closePrice.floatValue())
						.monthlyRor(monthlyRor)
						.build();

					allCalcsToSave.add(newCalc);
					insertCount++;
				}

			} catch (Exception e) {
				log.error("Error calculating monthly price for stock {}: {}", stock.getIsinCode(), e.getMessage());
			}
		}

		// 7. Bulk save all calculations (INSERT/UPDATE) - 1 query
		if (!allCalcsToSave.isEmpty()) {
			log.info("Bulk saving {} stock calculations ({} inserts, {} updates)",
				allCalcsToSave.size(), insertCount, updateCount);
			calcStockPriceRepository.saveAll(allCalcsToSave);
			calcStockPriceRepository.flush();
		}

		long stepEnd = System.currentTimeMillis();
		log.info("Stock calculation complete: {} total ({} inserts, {} updates) in {}ms",
			allCalcsToSave.size(), insertCount, updateCount, (stepEnd - stepStart));
	}

	/**
	 * 지수 월별 가격 계산 (Bulk Optimized with HashMap)
	 */
	private void calculateIndexPrices(LocalDate startDate, LocalDate endDate) {
		log.info("Starting bulk index calculation...");
		long stepStart = System.currentTimeMillis();

		// 1. Load all indices (1 query)
		List<IndexInfo> indices = indexInfoRepository.findAll();
		log.info("Loaded {} indices", indices.size());

		// 2. Load all index prices (1 query)
		List<IndexPrice> allPrices = indexPriceRepository.findAll();
		log.info("Loaded {} index prices", allPrices.size());

		// 3. Group prices by index_info_id (in-memory HashMap)
		Map<Integer, List<IndexPrice>> pricesByIndex = allPrices.stream()
			.collect(Collectors.groupingBy(
				p -> p.getIndexInfo().getId(),
				Collectors.collectingAndThen(
					Collectors.toList(),
					list -> {
						list.sort(Comparator.comparing(IndexPrice::getBaseDate));
						return list;
					}
				)
			));
		log.info("Grouped prices for {} indices", pricesByIndex.size());

		// 4. Load all existing CalcIndexPrice for duplicate check (1 query)
		List<CalcIndexPrice> existingCalcs = calcIndexPriceRepository.findAll();
		Map<String, CalcIndexPrice> existingCalcMap = existingCalcs.stream()
			.collect(Collectors.toMap(
				c -> c.getIndexInfo().getId() + "_" + c.getBaseDate(),
				c -> c
			));
		log.info("Loaded {} existing calc index prices", existingCalcs.size());

		// 5. Group CalcIndexPrice by index_info_id for previous month lookup
		Map<Integer, List<CalcIndexPrice>> calcsByIndex = existingCalcs.stream()
			.collect(Collectors.groupingBy(
				c -> c.getIndexInfo().getId(),
				Collectors.collectingAndThen(
					Collectors.toList(),
					list -> {
						list.sort(Comparator.comparing(CalcIndexPrice::getBaseDate));
						return list;
					}
				)
			));

		// 6. Calculate in memory with UPSERT
		List<CalcIndexPrice> allCalcsToSave = new ArrayList<>();
		int insertCount = 0;
		int updateCount = 0;

		for (IndexInfo indexInfo : indices) {
			try {
				String key = indexInfo.getId() + "_" + endDate;

				// Get prices for this index from HashMap (no DB!)
				List<IndexPrice> indexPrices = pricesByIndex.getOrDefault(indexInfo.getId(), new ArrayList<>());

				// Filter prices in the month range
				List<IndexPrice> monthPrices = indexPrices.stream()
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

				// Calculate monthly rate of return (from HashMap, no DB!)
				Float monthlyRor = calculateIndexMonthlyRor(indexInfo, endDate, closePrice, calcsByIndex);

				CalcIndexPrice existingCalc = existingCalcMap.get(key);

				if (existingCalc != null) {
					// UPDATE: 기존 엔티티의 필드 업데이트 (ID 유지)
					existingCalc.setPrice(closePrice);
					existingCalc.setMonthlyRor(monthlyRor);

					allCalcsToSave.add(existingCalc);
					updateCount++;
				} else {
					// INSERT: 새 엔티티 생성 (ID null)
					CalcIndexPrice newCalc = CalcIndexPrice.builder()
						.indexInfo(indexInfo)
						.baseDate(endDate)
						.price(closePrice)
						.monthlyRor(monthlyRor)
						.build();

					allCalcsToSave.add(newCalc);
					insertCount++;
				}

			} catch (Exception e) {
				log.error("Error calculating monthly price for index {}: {}", indexInfo.getName(), e.getMessage());
			}
		}

		// 7. Bulk save all calculations (INSERT/UPDATE) - 1 query
		if (!allCalcsToSave.isEmpty()) {
			log.info("Bulk saving {} index calculations ({} inserts, {} updates)",
				allCalcsToSave.size(), insertCount, updateCount);
			calcIndexPriceRepository.saveAll(allCalcsToSave);
			calcIndexPriceRepository.flush();
		}

		long stepEnd = System.currentTimeMillis();
		log.info("Index calculation complete: {} total ({} inserts, {} updates) in {}ms",
			allCalcsToSave.size(), insertCount, updateCount, (stepEnd - stepStart));
	}

	/**
	 * 주식 월별 수익률 계산 (HashMap 사용)
	 */
	private Float calculateStockMonthlyRor(Stock stock, LocalDate currentDate, Integer currentPrice,
		Map<Integer, List<CalcStockPrice>> calcsByStock) {

		List<CalcStockPrice> calcs = calcsByStock.getOrDefault(stock.getId(), new ArrayList<>());

		// Find previous month's price (before currentDate)
		CalcStockPrice previous = null;
		for (int i = calcs.size() - 1; i >= 0; i--) {
			if (calcs.get(i).getBaseDate().isBefore(currentDate)) {
				previous = calcs.get(i);
				break;
			}
		}

		if (previous == null) {
			return null; // First month, no previous data
		}

		Float previousPrice = previous.getPrice();
		if (previousPrice == null || previousPrice == 0) {
			return null;
		}

		// Monthly RoR = (Current Price - Previous Price) / Previous Price * 100
		return ((currentPrice - previousPrice) / previousPrice) * 100;
	}

	/**
	 * 지수 월별 수익률 계산 (HashMap 사용)
	 */
	private Float calculateIndexMonthlyRor(IndexInfo indexInfo, LocalDate currentDate, Float currentPrice,
		Map<Integer, List<CalcIndexPrice>> calcsByIndex) {

		List<CalcIndexPrice> calcs = calcsByIndex.getOrDefault(indexInfo.getId(), new ArrayList<>());

		// Find previous month's price (before currentDate)
		CalcIndexPrice previous = null;
		for (int i = calcs.size() - 1; i >= 0; i--) {
			if (calcs.get(i).getBaseDate().isBefore(currentDate)) {
				previous = calcs.get(i);
				break;
			}
		}

		if (previous == null) {
			return null; // First month, no previous data
		}

		Float previousPrice = previous.getPrice();
		if (previousPrice == null || previousPrice == 0) {
			return null;
		}

		// Monthly RoR = (Current Price - Previous Price) / Previous Price * 100
		return ((currentPrice - previousPrice) / previousPrice) * 100;
	}
}
