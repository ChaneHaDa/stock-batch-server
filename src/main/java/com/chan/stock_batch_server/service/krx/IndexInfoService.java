package com.chan.stock_batch_server.service.krx;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chan.stock_batch_server.migration.dto.KRXIndexData;
import com.chan.stock_batch_server.model.IndexInfo;
import com.chan.stock_batch_server.model.IndexPrice;
import com.chan.stock_batch_server.repository.IndexInfoRepository;
import com.chan.stock_batch_server.repository.IndexPriceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexInfoService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
	private final IndexInfoRepository indexInfoRepository;
	private final IndexPriceRepository indexPriceRepository;

	/**
	 * Index 데이터 저장 (UPSERT 방식)
	 * 1. 데이터를 Index별로 그룹화
	 * 2. 모든 기존 IndexInfo를 한 번에 조회
	 * 3. 신규 IndexInfo만 bulk insert
	 * 4. 모든 IndexPrice를 UPSERT (INSERT or UPDATE)
	 */
	@Transactional
	public void saveIndexData(List<KRXIndexData> indexDataList) {
		if (indexDataList.isEmpty()) {
			log.info("No index data to save");
			return;
		}

		log.info("Starting bulk save for {} index records (UPSERT mode)", indexDataList.size());
		long startTime = System.currentTimeMillis();

		// 1. 데이터를 Index별로 그룹화
		Map<String, List<KRXIndexData>> groupedData = groupByIndex(indexDataList);
		log.info("Grouped into {} unique indices", groupedData.size());

		// 2. 기존 모든 IndexInfo 조회 (1번의 쿼리)
		List<IndexInfo> allExistingIndices = indexInfoRepository.findAll();
		Map<String, IndexInfo> existingIndexMap = allExistingIndices.stream()
			.collect(Collectors.toMap(
				idx -> idx.getName() + "_" + idx.getCategory(),
				idx -> idx
			));

		log.info("Loaded {} existing indices from DB", existingIndexMap.size());

		// 3. 신규 IndexInfo 필터링 및 생성
		List<IndexInfo> newIndices = new ArrayList<>();
		for (Map.Entry<String, List<KRXIndexData>> entry : groupedData.entrySet()) {
			if (!existingIndexMap.containsKey(entry.getKey())) {
				KRXIndexData firstData = entry.getValue().get(0);
				IndexInfo indexInfo = IndexInfo.builder()
					.name(firstData.getIdxNm())
					.category(firstData.getIdxCsf())
					.build();
				newIndices.add(indexInfo);
			}
		}

		// 4. 신규 IndexInfo bulk insert (1번의 쿼리)
		if (!newIndices.isEmpty()) {
			log.info("Bulk inserting {} new indices", newIndices.size());
			indexInfoRepository.saveAll(newIndices);
			indexInfoRepository.flush();

			// 새로 저장된 IndexInfo를 Map에 추가
			for (IndexInfo indexInfo : newIndices) {
				String key = indexInfo.getName() + "_" + indexInfo.getCategory();
				existingIndexMap.put(key, indexInfo);
			}
		}

		// 5. IndexPrice 저장 (중복 체크 후 INSERT만)
		log.info("Preparing IndexPrice data...");

		// 5-1. 임포트할 데이터의 날짜 범위 추출
		LocalDate minDate = null;
		LocalDate maxDate = null;
		for (List<KRXIndexData> dataList : groupedData.values()) {
			for (KRXIndexData data : dataList) {
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
			log.warn("No valid dates found in index import data");
			return;
		}

		log.info("Index import date range: {} to {}", minDate, maxDate);

		// 5-2. 해당 월의 기존 데이터만 조회 (1번 쿼리!)
		List<IndexPrice> monthPrices = indexPriceRepository.findByBaseDateBetween(minDate, maxDate);
		log.info("Loaded {} existing index prices for date range", monthPrices.size());

		// 5-3. HashMap으로 index별 날짜 그룹핑 (메모리에서 처리)
		Map<Integer, java.util.Set<LocalDate>> existingDatesByIndex = monthPrices.stream()
			.collect(Collectors.groupingBy(
				p -> p.getIndexInfo().getId(),
				Collectors.mapping(IndexPrice::getBaseDate, Collectors.toSet())
			));

		// 5-4. 중복 체크 및 신규 데이터만 준비
		List<IndexPrice> allPricesToSave = new ArrayList<>();
		int skippedCount = 0;

		for (Map.Entry<String, List<KRXIndexData>> entry : groupedData.entrySet()) {
			IndexInfo indexInfo = existingIndexMap.get(entry.getKey());

			if (indexInfo == null) {
				log.warn("IndexInfo not found for key: {}", entry.getKey());
				continue;
			}

			// HashMap에서 해당 Index의 기존 날짜 조회 (DB 쿼리 없음!)
			java.util.Set<LocalDate> existingDates = existingDatesByIndex.getOrDefault(
				indexInfo.getId(),
				java.util.Collections.emptySet()
			);

			// Deduplicate input data by baseDate (keep first occurrence)
			Map<LocalDate, KRXIndexData> deduplicatedData = new HashMap<>();
			for (KRXIndexData data : entry.getValue()) {
				try {
					LocalDate baseDate = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);
					deduplicatedData.putIfAbsent(baseDate, data);
				} catch (Exception e) {
					log.error("Error parsing date for {}: {}", indexInfo.getName(), e.getMessage());
				}
			}

			// Log if duplicates were found
			int originalCount = entry.getValue().size();
			int deduplicatedCount = deduplicatedData.size();
			if (originalCount > deduplicatedCount) {
				log.warn("Found {} duplicate dates for index {}, keeping {} unique records",
					(originalCount - deduplicatedCount), indexInfo.getName(), deduplicatedCount);
			}

			// 신규 데이터만 INSERT
			for (Map.Entry<LocalDate, KRXIndexData> dataEntry : deduplicatedData.entrySet()) {
				try {
					LocalDate baseDate = dataEntry.getKey();
					KRXIndexData data = dataEntry.getValue();

					// 중복 체크 - 이미 존재하면 SKIP
					if (existingDates.contains(baseDate)) {
						skippedCount++;
						continue;
					}

					IndexPrice newPrice = IndexPrice.builder()
						.indexInfo(indexInfo)
						.baseDate(baseDate)
						.closePrice(data.getClpr() != null ? data.getClpr().floatValue() : null)
						.openPrice(data.getMkp() != null ? data.getMkp().floatValue() : null)
						.highPrice(data.getHipr() != null ? data.getHipr().floatValue() : null)
						.lowPrice(data.getLopr() != null ? data.getLopr().floatValue() : null)
						.yearlyDiff(data.getFltRt() != null ? data.getFltRt().floatValue() : null)
						.build();

					allPricesToSave.add(newPrice);

				} catch (Exception e) {
					log.error("Error processing index price for {}: {}", indexInfo.getName(), e.getMessage());
				}
			}
		}

		// 6. 신규 IndexPrice bulk insert
		if (!allPricesToSave.isEmpty()) {
			log.info("Bulk inserting {} index prices ({} skipped)",
				allPricesToSave.size(), skippedCount);
			indexPriceRepository.saveAll(allPricesToSave);
			indexPriceRepository.flush();
		}

		long endTime = System.currentTimeMillis();
		log.info("Index data bulk save completed: {} indices, {} prices in {}ms",
			existingIndexMap.size(), allPricesToSave.size(), (endTime - startTime));
	}

	private Map<String, List<KRXIndexData>> groupByIndex(List<KRXIndexData> indexDataList) {
		Map<String, List<KRXIndexData>> grouped = new HashMap<>();

		for (KRXIndexData data : indexDataList) {
			String key = data.getIdxNm() + "_" + data.getIdxCsf();
			grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(data);
		}

		return grouped;
	}
}
