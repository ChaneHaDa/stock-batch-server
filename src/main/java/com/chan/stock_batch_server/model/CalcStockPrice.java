package com.chan.stock_batch_server.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "stock_id")
    private Stock stock;
}
