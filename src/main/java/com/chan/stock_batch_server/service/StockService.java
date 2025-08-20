package com.chan.stock_batch_server.service;

import com.chan.stock_batch_server.model.Stock;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.Optional;

@Service
public class StockService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public Optional<Stock> findByIsinCode(String isinCode) {
        try {
            TypedQuery<Stock> query = entityManager.createQuery(
                "SELECT s FROM Stock s WHERE s.isinCode = :isinCode", Stock.class);
            query.setParameter("isinCode", isinCode);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    public Optional<Stock> findById(Integer id) {
        Stock stock = entityManager.find(Stock.class, id);
        return Optional.ofNullable(stock);
    }
    
    public Stock save(Stock stock) {
        if (stock.getId() == null) {
            entityManager.persist(stock);
            return stock;
        } else {
            return entityManager.merge(stock);
        }
    }
}