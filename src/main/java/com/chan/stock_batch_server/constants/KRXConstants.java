package com.chan.stock_batch_server.constants;

/**
 * KRX 데이터 마이그레이션 관련 상수
 */
public final class KRXConstants {

	// 데이터 경로
	public static final String DEFAULT_KRX_DATA_PATH = "../krx-json-data";
	public static final String KRX_DATA_PATH_PROPERTY = "krx.data.path";
	// 파일 패턴
	public static final String STOCK_PATH_PATTERN = "Price/STOCK/%d";
	public static final String ETF_PATH_PATTERN = "Price/ETF/%d";
	public static final String INDEX_BOND_PATH_PATTERN = "Index/BOND/%d";
	public static final String INDEX_STOCK_PATH_PATTERN = "Index/STOCK/%d";
	public static final String INDEX_DERIVATION_PATH_PATTERN = "Index/DERIVATION/%d";
	// 날짜 포맷
	public static final String DATE_FORMAT = "yyyyMMdd";
	public static final String MONTH_PATTERN = "%d%02d";
	// 마켓 카테고리
	public static final String MARKET_STOCK = "STOCK";
	public static final String MARKET_ETF = "ETF";
	public static final String MARKET_ETN = "ETN";
	public static final String INDEX_CATEGORY_BOND = "BOND";
	public static final String INDEX_CATEGORY_STOCK = "STOCK";
	public static final String INDEX_CATEGORY_DERIVATION = "DERIVATION";
	// 파일 확장자
	public static final String JSON_EXTENSION = ".json";
	// 데이터 처리 관련
	public static final int DEFAULT_PAGE_SIZE = 1000;
	public static final int MAX_RETRY_COUNT = 3;
	public static final long BATCH_SIZE = 100;
	// 테스트 데이터
	public static final int TEST_YEAR = 2024;
	public static final int TEST_MONTH = 1;

	private KRXConstants() {
	}
}
