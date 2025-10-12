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
	 * Index 데이터 저장 (Proper Bulk Processing)
	 * 1. 데이터를 Index별로 그룹화
	 * 2. 모든 기존 IndexInfo를 한 번에 조회
	 * 3. 신규 IndexInfo만 bulk insert
	 * 4. 모든 IndexPrice를 bulk insert
	 */
	@Transactional
	public void saveIndexData(List<KRXIndexData> indexDataList) {
		if (indexDataList.isEmpty()) {
			log.info("No index data to save");
			return;
		}

		log.info("Starting bulk save for {} index records", indexDataList.size());
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

		// 5. 모든 IndexPrice 생성 및 bulk insert
		log.info("Preparing IndexPrice data...");
		List<IndexPrice> allPricesToSave = new ArrayList<>();

		for (Map.Entry<String, List<KRXIndexData>> entry : groupedData.entrySet()) {
			IndexInfo indexInfo = existingIndexMap.get(entry.getKey());

			if (indexInfo == null) {
				log.warn("IndexInfo not found for key: {}", entry.getKey());
				continue;
			}

			// Index의 기존 가격 데이터 조회 (각 Index당 1번)
			Set<LocalDate> existingDates = indexPriceRepository
				.findByIndexInfoId(indexInfo.getId())
				.stream()
				.map(IndexPrice::getBaseDate)
				.collect(Collectors.toSet());

			// Deduplicate input data by baseDate (keep first occurrence)
			Map<LocalDate, KRXIndexData> deduplicatedData = new HashMap<>();
			for (KRXIndexData data : entry.getValue()) {
				try {
					LocalDate baseDate = LocalDate.parse(data.getBasDt(), DATE_FORMATTER);
					// Only add if we haven't seen this date before
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

			// 신규 가격 데이터만 필터링
			for (Map.Entry<LocalDate, KRXIndexData> dataEntry : deduplicatedData.entrySet()) {
				LocalDate baseDate = dataEntry.getKey();
				KRXIndexData data = dataEntry.getValue();

				if (existingDates.contains(baseDate)) {
					continue; // 중복 스킵
				}

				try {
					IndexPrice price = IndexPrice.builder()
						.indexInfo(indexInfo)
						.baseDate(baseDate)
						.closePrice(data.getClpr() != null ? data.getClpr().floatValue() : null)
						.openPrice(data.getMkp() != null ? data.getMkp().floatValue() : null)
						.highPrice(data.getHipr() != null ? data.getHipr().floatValue() : null)
						.lowPrice(data.getLopr() != null ? data.getLopr().floatValue() : null)
						.yearlyDiff(data.getFltRt() != null ? data.getFltRt().floatValue() : null)
						.build();

					allPricesToSave.add(price);

				} catch (Exception e) {
					log.error("Error building index price for {}: {}", indexInfo.getName(), e.getMessage());
				}
			}
		}

		// 6. 모든 IndexPrice bulk insert (1번의 대량 쿼리)
		if (!allPricesToSave.isEmpty()) {
			log.info("Bulk inserting {} index prices", allPricesToSave.size());
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
