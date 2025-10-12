-- KRX Stock and Index Data Schema (aligned with backtest-api)

-- Create Stock table
CREATE TABLE stock (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    short_code VARCHAR(255),
    isin_code VARCHAR(255) UNIQUE,
    market_category VARCHAR(255)
);

-- Create IndexInfo table
CREATE TABLE index_info (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    category VARCHAR(255),
    CONSTRAINT uk_index_info_name_category UNIQUE (name, category)
);

-- Create StockPrice table
CREATE TABLE stock_price (
    id SERIAL PRIMARY KEY,
    close_price INTEGER,
    open_price INTEGER,
    low_price INTEGER,
    high_price INTEGER,
    trade_quantity INTEGER,
    trade_amount BIGINT,
    issued_count BIGINT,
    base_date DATE,
    stock_id INTEGER REFERENCES stock(id),
    CONSTRAINT uk_stock_price_stock_date UNIQUE (stock_id, base_date)
);

-- Create IndexPrice table
CREATE TABLE index_price (
    id SERIAL PRIMARY KEY,
    close_price REAL,
    open_price REAL,
    low_price REAL,
    high_price REAL,
    yearly_diff REAL,
    base_date DATE,
    index_info_id INTEGER REFERENCES index_info(id),
    CONSTRAINT uk_index_price_info_date UNIQUE (index_info_id, base_date)
);

-- Create CalcStockPrice table
CREATE TABLE calc_stock_price (
    id SERIAL PRIMARY KEY,
    price REAL,
    monthly_ror REAL,
    base_date DATE,
    stock_id INTEGER REFERENCES stock(id),
    CONSTRAINT uk_calc_stock_price_stock_date UNIQUE (stock_id, base_date)
);

-- Create CalcIndexPrice table
CREATE TABLE calc_index_price (
    id SERIAL PRIMARY KEY,
    price REAL,
    monthly_ror REAL,
    base_date DATE,
    index_info_id INTEGER REFERENCES index_info(id),
    CONSTRAINT uk_calc_index_price_info_date UNIQUE (index_info_id, base_date)
);

-- Create StockNameHistory table
CREATE TABLE stock_name_history (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    start_at DATE,
    end_at DATE,
    stock_id INTEGER NOT NULL REFERENCES stock(id)
);

-- Create basic indexes for performance
CREATE INDEX idx_stock_isin_code ON stock(isin_code);
CREATE INDEX idx_stock_price_stock_id ON stock_price(stock_id);
CREATE INDEX idx_stock_price_base_date ON stock_price(base_date);
CREATE INDEX idx_index_price_index_info_id ON index_price(index_info_id);
CREATE INDEX idx_index_price_base_date ON index_price(base_date);
CREATE INDEX idx_stock_name_history_stock_id ON stock_name_history(stock_id);
