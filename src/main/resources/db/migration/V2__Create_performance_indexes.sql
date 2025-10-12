-- Add performance indexes for queries (aligned with backtest-api)

-- Composite indexes for price range queries
CREATE INDEX IF NOT EXISTS idx_stock_price_stock_date
ON stock_price(stock_id, base_date);

CREATE INDEX IF NOT EXISTS idx_index_price_info_date
ON index_price(index_info_id, base_date);

CREATE INDEX IF NOT EXISTS idx_calc_stock_price_stock_date
ON calc_stock_price(stock_id, base_date);

CREATE INDEX IF NOT EXISTS idx_calc_index_price_info_date
ON calc_index_price(index_info_id, base_date);

-- Indexes for stock search functionality
CREATE INDEX IF NOT EXISTS idx_stock_name
ON stock(name);

CREATE INDEX IF NOT EXISTS idx_stock_short_code
ON stock(short_code);

CREATE INDEX IF NOT EXISTS idx_stock_market_category
ON stock(market_category);

-- Index for stock name history queries
CREATE INDEX IF NOT EXISTS idx_stock_name_history_dates
ON stock_name_history(start_at, end_at);

CREATE INDEX IF NOT EXISTS idx_stock_name_history_stock_dates
ON stock_name_history(stock_id, start_at, end_at);
