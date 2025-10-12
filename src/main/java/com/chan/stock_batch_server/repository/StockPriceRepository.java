package com.chan.stock_batch_server.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.Stock;
import com.chan.stock_batch_server.model.StockPrice;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Integer> {
	List<StockPrice> findByStockIdOrderByBaseDate(Integer stockId);

	List<StockPrice> findByStockIdAndBaseDate(Integer stockId, LocalDate baseDate);

	boolean existsByStockAndBaseDate(Stock stock, LocalDate baseDate);
}
