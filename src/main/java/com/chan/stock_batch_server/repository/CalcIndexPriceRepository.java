package com.chan.stock_batch_server.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.CalcIndexPrice;
import com.chan.stock_batch_server.model.IndexInfo;

@Repository
public interface CalcIndexPriceRepository extends JpaRepository<CalcIndexPrice, Integer> {
	boolean existsByIndexInfoAndBaseDate(IndexInfo indexInfo, LocalDate baseDate);

	@Query("SELECT c FROM CalcIndexPrice c WHERE c.indexInfo = :indexInfo AND c.baseDate < :baseDate ORDER BY c.baseDate DESC")
	Optional<CalcIndexPrice> findPreviousMonthPrice(@Param("indexInfo") IndexInfo indexInfo,
		@Param("baseDate") LocalDate baseDate);

	List<CalcIndexPrice> findByBaseDateBetween(LocalDate startDate, LocalDate endDate);
}
