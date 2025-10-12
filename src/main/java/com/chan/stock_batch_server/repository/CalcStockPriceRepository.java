package com.chan.stock_batch_server.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.CalcStockPrice;
import com.chan.stock_batch_server.model.Stock;

@Repository
public interface CalcStockPriceRepository extends JpaRepository<CalcStockPrice, Integer> {
	boolean existsByStockAndBaseDate(Stock stock, LocalDate baseDate);

	@Query("SELECT c FROM CalcStockPrice c WHERE c.stock = :stock AND c.baseDate < :baseDate ORDER BY c.baseDate DESC")
	Optional<CalcStockPrice> findPreviousMonthPrice(@Param("stock") Stock stock, @Param("baseDate") LocalDate baseDate);
}
