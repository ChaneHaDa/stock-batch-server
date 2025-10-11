-- 성능 최적화를 위한 인덱스 생성

-- Stock 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_stock_isin_code ON stock(isin_code);
CREATE INDEX IF NOT EXISTS idx_stock_market_category ON stock(market_category);

-- StockPrice 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_stock_price_stock_id ON stock_price(stock_id);
CREATE INDEX IF NOT EXISTS idx_stock_price_base_date ON stock_price(base_date);
CREATE INDEX IF NOT EXISTS idx_stock_price_stock_date ON stock_price(stock_id, base_date);

-- IndexInfo 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_index_info_name_category ON index_info(name, category);

-- IndexPrice 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_index_price_index_info_id ON index_price(index_info_id);
CREATE INDEX IF NOT EXISTS idx_index_price_base_date ON index_price(base_date);
CREATE INDEX IF NOT EXISTS idx_index_price_info_date ON index_price(index_info_id, base_date);

-- CalcStockPrice 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_calc_stock_price_stock_id ON calc_stock_price(stock_id);
CREATE INDEX IF NOT EXISTS idx_calc_stock_price_base_date ON calc_stock_price(base_date);
CREATE INDEX IF NOT EXISTS idx_calc_stock_price_stock_date ON calc_stock_price(stock_id, base_date);

-- CalcIndexPrice 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_calc_index_price_index_info_id ON calc_index_price(index_info_id);
CREATE INDEX IF NOT EXISTS idx_calc_index_price_base_date ON calc_index_price(base_date);
CREATE INDEX IF NOT EXISTS idx_calc_index_price_info_date ON calc_index_price(index_info_id, base_date);

-- StockNameHistory 테이블 인덱스 (가장 중요)
CREATE INDEX IF NOT EXISTS idx_stock_name_history_stock_id ON stock_name_history(stock_id);
CREATE INDEX IF NOT EXISTS idx_stock_name_history_dates ON stock_name_history(start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_stock_name_history_stock_dates ON stock_name_history(stock_id, start_at, end_at);