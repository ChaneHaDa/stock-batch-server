package com.chan.stock_batch_server.service.krx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chan.stock_batch_server.migration.dto.KRXIndexData;
import com.chan.stock_batch_server.migration.dto.KRXStockData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KRXDataImportService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
	private final ObjectMapper objectMapper;
	private final StockService stockService;
	private final IndexInfoService indexInfoService;
	private final StockNameHistoryService stockNameHistoryService;
	@Value("${krx.data.path:../krx-json-data}")
	private String krxDataPath;

	/**
	 * 특정 년월의 KRX 데이터 임포트
	 */
	public void importMonthData(int year, int month) {
		log.info("Starting KRX data import for {}-{}", year, month);

		try {
			// 1. STOCK/ETF 데이터 임포트
			Map<String, List<KRXStockData>> stockDataMap = importStockData(year, month);

			// 2. Index 데이터 임포트
			List<KRXIndexData> indexDataList = importIndexData(year, month);

			// 3. Stock Name History 생성 (가장 중요)
			stockNameHistoryService.generateAllStockNameHistories(stockDataMap);

			// 4. Calc 데이터 계산
			calculateMonthlyData(year, month);

			log.info("KRX data import completed for {}-{}", year, month);

		} catch (Exception e) {
			log.error("Error importing KRX data for {}-{}", year, month, e);
			throw new RuntimeException("KRX data import failed", e);
		}
	}

	/**
	 * STOCK/ETF 데이터 임포트
	 */
	private Map<String, List<KRXStockData>> importStockData(int year, int month) throws IOException {
		Map<String, List<KRXStockData>> allStockData = new HashMap<>();

		// STOCK 데이터 처리
		String stockPath = Paths.get(krxDataPath, "Price", "STOCK", String.valueOf(year)).toString();
		processStockFiles(stockPath, year, month, "STOCK", allStockData);

		// ETF 데이터 처리
		String etfPath = Paths.get(krxDataPath, "Price", "ETF", String.valueOf(year)).toString();
		processStockFiles(etfPath, year, month, "ETF", allStockData);

		// Stock 기본 정보 및 가격 데이터 저장
		stockService.saveStockData(allStockData);

		return allStockData;
	}

	/**
	 * 주식 파일 처리
	 */
	private void processStockFiles(String directoryPath, int year, int month,
		String marketCategory, Map<String, List<KRXStockData>> allStockData) throws IOException {
		Path directory = Paths.get(directoryPath);
		if (!Files.exists(directory)) {
			log.warn("Directory not found: {}", directoryPath);
			return;
		}

		String monthPattern = String.format("%d%02d", year, month);

		try (Stream<Path> files = Files.list(directory)) {
			files.filter(file -> file.getFileName().toString().startsWith(monthPattern))
				.filter(file -> file.getFileName().toString().endsWith(".json"))
				.sorted()
				.forEach(file -> {
					try {
						List<KRXStockData> stockDataList = parseStockFile(file, marketCategory);
						mergeStockData(allStockData, stockDataList);
					} catch (IOException e) {
						log.error("Error processing file: {}", file, e);
					}
				});
		}
	}

	/**
	 * 주식 JSON 파일 파싱
	 */
	private List<KRXStockData> parseStockFile(Path filePath, String marketCategory) throws IOException {
		List<KRXStockData> stockDataList = new ArrayList<>();

		JsonNode rootNode = objectMapper.readTree(filePath.toFile());
		JsonNode items = rootNode.path("response").path("body").path("items").path("item");

		if (items.isArray()) {
			for (JsonNode item : items) {
				KRXStockData data = convertToStockData(item, marketCategory);
				if (data != null) {
					stockDataList.add(data);
				}
			}
		} else if (items.isObject()) {
			KRXStockData data = convertToStockData(items, marketCategory);
			if (data != null) {
				stockDataList.add(data);
			}
		}

		return stockDataList;
	}

	/**
	 * JsonNode를 KRXStockData로 변환
	 */
	private KRXStockData convertToStockData(JsonNode item, String marketCategory) {
		try {
			KRXStockData data = new KRXStockData();
			data.setBasDt(item.path("basDt").asText());
			data.setSrtnCd(item.path("srtnCd").asText());
			data.setIsinCd(item.path("isinCd").asText());
			data.setItmsNm(item.path("itmsNm").asText());
			data.setMrktCtg(marketCategory); // 파일 경로 기반 설정

			// 가격 데이터 (null 또는 0 값 처리)
			data.setClpr(getSafeInteger(item, "clpr"));
			data.setMkp(getSafeInteger(item, "mkp"));
			data.setHipr(getSafeInteger(item, "hipr"));
			data.setLopr(getSafeInteger(item, "lopr"));
			data.setTrqu(getSafeLong(item, "trqu"));
			data.setTrPrc(getSafeLong(item, "trPrc"));

			// ETF vs STOCK 발행주식수 필드 차이 처리
			if ("ETF".equals(marketCategory)) {
				data.setLstgStCnt(getSafeLong(item, "stLstgCnt"));
			} else {
				data.setLstgStCnt(getSafeLong(item, "lstgStCnt"));
			}

			return data;
		} catch (Exception e) {
			log.error("Error converting stock data: {}", item, e);
			return null;
		}
	}

	/**
	 * Index 데이터 임포트
	 */
	private List<KRXIndexData> importIndexData(int year, int month) throws IOException {
		List<KRXIndexData> allIndexData = new ArrayList<>();

		// BOND, STOCK, DERIVATION 순서로 처리
		String[] categories = {"BOND", "STOCK", "DERIVATION"};

		for (String category : categories) {
			String indexPath = Paths.get(krxDataPath, "Index", category, String.valueOf(year)).toString();
			List<KRXIndexData> indexDataList = processIndexFiles(indexPath, year, month, category);
			allIndexData.addAll(indexDataList);
		}

		// Index 기본 정보 및 가격 데이터 저장
		indexInfoService.saveIndexData(allIndexData);

		return allIndexData;
	}

	/**
	 * 지수 파일 처리
	 */
	private List<KRXIndexData> processIndexFiles(String directoryPath, int year, int month,
		String category) throws IOException {
		List<KRXIndexData> indexDataList = new ArrayList<>();

		Path directory = Paths.get(directoryPath);
		if (!Files.exists(directory)) {
			log.warn("Index directory not found: {}", directoryPath);
			return indexDataList;
		}

		String monthPattern = String.format("%d%02d", year, month);

		try (Stream<Path> files = Files.list(directory)) {
			files.filter(file -> file.getFileName().toString().startsWith(monthPattern))
				.filter(file -> file.getFileName().toString().endsWith(".json"))
				.sorted()
				.forEach(file -> {
					try {
						List<KRXIndexData> data = parseIndexFile(file, category);
						indexDataList.addAll(data);
					} catch (IOException e) {
						log.error("Error processing index file: {}", file, e);
					}
				});
		}

		return indexDataList;
	}

	/**
	 * 지수 JSON 파일 파싱
	 */
	private List<KRXIndexData> parseIndexFile(Path filePath, String category) throws IOException {
		List<KRXIndexData> indexDataList = new ArrayList<>();

		JsonNode rootNode = objectMapper.readTree(filePath.toFile());
		JsonNode items = rootNode.path("response").path("body").path("items").path("item");

		if (items.isArray()) {
			for (JsonNode item : items) {
				KRXIndexData data = convertToIndexData(item, category);
				if (data != null) {
					indexDataList.add(data);
				}
			}
		} else if (items.isObject()) {
			KRXIndexData data = convertToIndexData(items, category);
			if (data != null) {
				indexDataList.add(data);
			}
		}

		return indexDataList;
	}

	/**
	 * JsonNode를 KRXIndexData로 변환
	 */
	private KRXIndexData convertToIndexData(JsonNode item, String category) {
		try {
			KRXIndexData data = new KRXIndexData();
			data.setBasDt(item.path("basDt").asText());
			data.setIdxNm(item.path("idxNm").asText());
			data.setIdxCsf(category);

			// 지수별로 다른 필드명 처리
			if ("BOND".equals(category)) {
				data.setClpr(getSafeDouble(item, "totBnfIdxClpr"));
				data.setMkp(getSafeDouble(item, "mrktPrcIdxClpr"));
			} else {
				data.setClpr(getSafeDouble(item, "clpr"));
				data.setMkp(getSafeDouble(item, "mkp"));
			}

			data.setHipr(getSafeDouble(item, "hipr"));
			data.setLopr(getSafeDouble(item, "lopr"));
			data.setFltRt(getSafeDouble(item, "fltRt"));

			return data;
		} catch (Exception e) {
			log.error("Error converting index data: {}", item, e);
			return null;
		}
	}

	/**
	 * 주식 데이터 병합
	 */
	private void mergeStockData(Map<String, List<KRXStockData>> allStockData, List<KRXStockData> newData) {
		for (KRXStockData data : newData) {
			String isinCode = data.getIsinCd();
			if (isinCode != null && !isinCode.isEmpty()) {
				allStockData.computeIfAbsent(isinCode, k -> new ArrayList<>()).add(data);
			}
		}
	}

	/**
	 * 월별 계산 데이터 생성
	 */
	private void calculateMonthlyData(int year, int month) {
		// 기존의 MonthlyStockPriceBatchConfig 활용
		// Spring Batch Job으로 실행
		log.info("Calculating monthly data for {}-{}", year, month);
		log.info("Monthly batch calculation will be implemented in future release");
	}

	// 안전한 숫자 변환 헬퍼 메서드들
	private Integer getSafeInteger(JsonNode node, String fieldName) {
		JsonNode field = node.path(fieldName);
		if (field.isMissingNode() || field.asText().isEmpty() || "0".equals(field.asText())) {
			return null;
		}
		try {
			return field.asInt();
		} catch (Exception e) {
			return null;
		}
	}

	private Long getSafeLong(JsonNode node, String fieldName) {
		JsonNode field = node.path(fieldName);
		if (field.isMissingNode() || field.asText().isEmpty() || "0".equals(field.asText())) {
			return null;
		}
		try {
			return field.asLong();
		} catch (Exception e) {
			return null;
		}
	}

	private Double getSafeDouble(JsonNode node, String fieldName) {
		JsonNode field = node.path(fieldName);
		if (field.isMissingNode() || field.asText().isEmpty() || "0".equals(field.asText())) {
			return null;
		}
		try {
			return field.asDouble();
		} catch (Exception e) {
			return null;
		}
	}
}
