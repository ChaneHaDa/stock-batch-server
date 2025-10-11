package com.chan.stock_batch_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.IndexPrice;

@Repository
public interface IndexPriceRepository extends JpaRepository<IndexPrice, Integer> {
}
