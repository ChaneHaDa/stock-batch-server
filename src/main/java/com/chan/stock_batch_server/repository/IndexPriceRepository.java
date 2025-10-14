package com.chan.stock_batch_server.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.IndexInfo;
import com.chan.stock_batch_server.model.IndexPrice;

@Repository
public interface IndexPriceRepository extends JpaRepository<IndexPrice, Integer> {
	boolean existsByIndexInfoAndBaseDate(IndexInfo indexInfo, LocalDate baseDate);

	List<IndexPrice> findByIndexInfoId(Integer indexInfoId);

	List<IndexPrice> findByBaseDateBetween(LocalDate startDate, LocalDate endDate);
}
