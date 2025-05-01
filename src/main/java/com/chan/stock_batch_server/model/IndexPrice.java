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
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IndexPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Float closePrice;
    private Float openPrice;
    private Float lowPrice;
    private Float highPrice;
    private Float yearlyDiff;
    private LocalDate baseDate;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "index_info_id")
    private IndexInfo indexInfo;
}