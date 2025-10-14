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

		// 5. 모든 IndexPrice를 UPSERT 처리
		log.info("Preparing IndexPrice data for UPSERT...");

		// 5-1. 기존 모든 IndexPrice 조회 (ID 포함) - 1번 쿼리
		List<IndexPrice> allExistingPrices = indexPriceRepository.findAll();
		Map<String, IndexPrice> existingPriceMap = allExistingPrices.stream()
			.collect(Collectors.toMap(
				p -> p.getIndexInfo().getId() + "_" + p.getBaseDate(),
				p -> p
			));
		log.info("Loaded {} existing index prices from DB", existingPriceMap.size());

		// 5-2. INSERT/UPDATE 대상 준비
		List<IndexPrice> allPricesToSave = new ArrayList<>();
		int insertCount = 0;
		int updateCount = 0;

		for (Map.Entry<String, List<KRXIndexData>> entry : groupedData.entrySet()) {
			IndexInfo indexInfo = existingIndexMap.get(entry.getKey());

			if (indexInfo == null) {
				log.warn("IndexInfo not found for key: {}", entry.getKey());
				continue;
			}

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

			// UPSERT 로직
			for (Map.Entry<LocalDate, KRXIndexData> dataEntry : deduplicatedData.entrySet()) {
				try {
					LocalDate baseDate = dataEntry.getKey();
					KRXIndexData data = dataEntry.getValue();
					String key = indexInfo.getId() + "_" + baseDate;

					IndexPrice existingPrice = existingPriceMap.get(key);

					if (existingPrice != null) {
						// UPDATE: 기존 엔티티의 필드 업데이트 (ID 유지)
						existingPrice.setClosePrice(data.getClpr() != null ? data.getClpr().floatValue() : null);
						existingPrice.setOpenPrice(data.getMkp() != null ? data.getMkp().floatValue() : null);
						existingPrice.setHighPrice(data.getHipr() != null ? data.getHipr().floatValue() : null);
						existingPrice.setLowPrice(data.getLopr() != null ? data.getLopr().floatValue() : null);
						existingPrice.setYearlyDiff(data.getFltRt() != null ? data.getFltRt().floatValue() : null);

						allPricesToSave.add(existingPrice);
						updateCount++;
					} else {
						// INSERT: 새 엔티티 생성 (ID null)
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
						insertCount++;
					}

				} catch (Exception e) {
					log.error("Error processing index price for {}: {}", indexInfo.getName(), e.getMessage());
				}
			}
		}

		// 6. 모든 IndexPrice bulk save (JPA가 자동으로 INSERT/UPDATE 구분) - 1번의 쿼리
		if (!allPricesToSave.isEmpty()) {
			log.info("Bulk saving {} index prices ({} inserts, {} updates)",
				allPricesToSave.size(), insertCount, updateCount);
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
