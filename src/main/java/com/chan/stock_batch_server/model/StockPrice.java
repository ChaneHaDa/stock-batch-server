package com.chan.stock_batch_server.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer closePrice;
    private Integer openPrice;
    private Integer lowPrice;
    private Integer highPrice;
    private Integer tradeQuantity;
    private Long tradeAmount;
    private Long issuedCount;
    private LocalDate baseDate;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    @JsonBackReference
    private Stock stock;

    public void setStock(Stock stock) {
        this.stock = stock;
        if (stock != null && !stock.getStockPriceList().contains(this)) {
            stock.getStockPriceList().add(this);
        }
    }
}
