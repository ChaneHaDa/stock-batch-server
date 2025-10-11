-- KRX 데이터 마이그레이션을 위한 스키마 생성

-- Stock 테이블
CREATE TABLE IF NOT EXISTS stock (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    short_code VARCHAR(255),
    isin_code VARCHAR(255) UNIQUE,
    market_category VARCHAR(255),
    start_at DATE,
    end_at DATE
);

-- IndexInfo 테이블
CREATE TABLE IF NOT EXISTS index_info (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    category VARCHAR(255),
    start_at DATE,
    end_at DATE,
    CONSTRAINT uk_index_info_name_category UNIQUE (name, category)
);

-- StockPrice 테이블
CREATE TABLE IF NOT EXISTS stock_price (
    id SERIAL PRIMARY KEY,
    close_price INTEGER,
    open_price INTEGER,
    low_price INTEGER,
    high_price INTEGER,
    trade_quantity INTEGER,
    trade_amount BIGINT,
    issued_count BIGINT,
    base_date DATE,
    stock_id INTEGER REFERENCES stock(id)
);

-- IndexPrice 테이블
CREATE TABLE IF NOT EXISTS index_price (
    id SERIAL PRIMARY KEY,
    close_price REAL,
    open_price REAL,
    low_price REAL,
    high_price REAL,
    yearly_diff REAL,
    base_date DATE,
    index_info_id INTEGER REFERENCES index_info(id)
);

-- CalcStockPrice 테이블
CREATE TABLE IF NOT EXISTS calc_stock_price (
    id SERIAL PRIMARY KEY,
    price REAL,
    monthly_ror REAL,
    base_date DATE,
    stock_id INTEGER REFERENCES stock(id)
);

-- CalcIndexPrice 테이블
CREATE TABLE IF NOT EXISTS calc_index_price (
    id SERIAL PRIMARY KEY,
    price REAL,
    monthly_ror REAL,
    base_date DATE,
    index_info_id INTEGER REFERENCES index_info(id)
);

-- StockNameHistory 테이블 (가장 중요)
CREATE TABLE IF NOT EXISTS stock_name_history (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    start_at DATE,
    end_at DATE,
    stock_id INTEGER NOT NULL REFERENCES stock(id),
    CONSTRAINT uk_stock_name_history_unique UNIQUE (stock_id, name, start_at)
);