package com.chan.stock_batch_server.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.StockNameHistory;

@Repository
public interface StockNameHistoryRepository extends JpaRepository<StockNameHistory, Integer> {

	List<StockNameHistory> findByStockIdOrderByStartAtDesc(Integer stockId);

	List<StockNameHistory> findByStockIdAndEndAtIsNull(Integer stockId);

	@Modifying
	@Query("DELETE FROM StockNameHistory s WHERE s.stock.id = :stockId")
	void deleteByStockId(@Param("stockId") Integer stockId);

	boolean existsByStockIdAndNameAndStartAt(Integer stockId, String name, java.time.LocalDate startAt);
}
