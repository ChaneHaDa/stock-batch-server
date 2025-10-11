package com.chan.stock_batch_server.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.Stock;

@Repository
public interface StockRepository extends JpaRepository<Stock, Integer> {
	Optional<Stock> findByIsinCode(String isinCode);

	boolean existsByIsinCode(String isinCode);
}
