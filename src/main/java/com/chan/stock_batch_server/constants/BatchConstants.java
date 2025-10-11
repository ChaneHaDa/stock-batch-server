package com.chan.stock_batch_server.constants;

/**
 * Spring Batch 관련 상수
 */
public final class BatchConstants {

	// 잡 이름
	public static final String KRX_DATA_MIGRATION_JOB = "krxDataMigrationJob";
	public static final String KRX_MIGRATION_JOB_NAME = "krxDataMigrationJob";
	public static final String MONTHLY_STOCK_PRICE_JOB = "monthlyStockPriceJob";
	public static final String MONTHLY_INDEX_PRICE_JOB = "monthlyIndexPriceJob";
	// 스텝 이름
	public static final String KRX_MIGRATION_STEP = "krxMigrationStep";
	public static final String KRX_MIGRATION_STEP_NAME = "krxMigrationStep";
	public static final String MONTHLY_STOCK_PRICE_STEP = "monthlyStockPriceStep";
	public static final String MONTHLY_INDEX_PRICE_STEP = "monthlyIndexPriceStep";
	// 파라미터 키
	public static final String PARAM_TIME = "time";
	public static final String PARAM_YEAR = "year";
	public static final String PARAM_MONTH = "month";
	public static final String PARAM_TYPE = "type";
	// 타입 값
	public static final String TYPE_STOCK = "stock";
	public static final String TYPE_INDEX = "index";
	// 트랜잭션 관련
	public static final String ISOLATION_LEVEL = "ISOLATION_READ_COMMITTED";
	public static final String DATABASE_TYPE_H2 = "H2";
	public static final String BATCH_TABLE_PREFIX = "BATCH_";
	// 청크 사이즈
	public static final int CHUNK_SIZE = 100;
	public static final int PAGE_SIZE = 1000;
	// 스레드 풀
	public static final int CORE_POOL_SIZE = 2;
	public static final int MAX_POOL_SIZE = 5;
	public static final int QUEUE_CAPACITY = 100;
	// 재시도 관련
	public static final int MAX_RETRY_ATTEMPTS = 3;
	public static final long RETRY_DELAY_MS = 1000;

	private BatchConstants() {
	}
}
