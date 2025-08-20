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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalcStockPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Float price;
    private Float monthlyRor;
    private LocalDate baseDate;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    @JsonBackReference
    private Stock stock;

    public void setStock(Stock stock) {
        this.stock = stock;
        if (stock != null && !stock.getCalcStockPriceList().contains(this)) {
            stock.getCalcStockPriceList().add(this);
        }
    }
}
