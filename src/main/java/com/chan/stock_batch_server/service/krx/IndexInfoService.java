package com.chan.stock_batch_server.service.krx;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chan.stock_batch_server.migration.dto.KRXIndexData;
import com.chan.stock_batch_server.model.IndexInfo;
import com.chan.stock_batch_server.repository.IndexInfoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexInfoService {

	private final IndexInfoRepository indexInfoRepository;

	@Transactional
	public void saveIndexData(List<KRXIndexData> indexDataList) {
		log.info("Saving index data for {} records", indexDataList.size());

		for (KRXIndexData data : indexDataList) {
			try {
				if (!indexInfoRepository.existsByNameAndCategory(data.getIdxNm(), data.getIdxCsf())) {
					IndexInfo indexInfo = IndexInfo.builder()
						.name(data.getIdxNm())
						.category(data.getIdxCsf())
						.build();

					indexInfoRepository.save(indexInfo);
				}
			} catch (Exception e) {
				log.error("Error saving index info: {}", data.getIdxNm(), e);
			}
		}

		log.info("Index data save completed");
	}
}
