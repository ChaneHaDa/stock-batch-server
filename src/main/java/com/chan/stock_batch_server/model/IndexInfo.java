package com.chan.stock_batch_server.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = { "name", "category" }) })
public class IndexInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String category;
    private LocalDate startAt;
    private LocalDate endAt;

    @OneToMany(mappedBy = "indexInfo")
    private List<IndexPrice> indexPriceList;
}
