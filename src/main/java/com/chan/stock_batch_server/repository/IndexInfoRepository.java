package com.chan.stock_batch_server.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chan.stock_batch_server.model.IndexInfo;

@Repository
public interface IndexInfoRepository extends JpaRepository<IndexInfo, Integer> {
	Optional<IndexInfo> findByNameAndCategory(String name, String category);

	boolean existsByNameAndCategory(String name, String category);
}
