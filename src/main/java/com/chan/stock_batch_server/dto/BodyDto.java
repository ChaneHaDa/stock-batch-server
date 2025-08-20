package com.chan.stock_batch_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BodyDto {
    private Integer numOfRows;
    private Integer pageNo;
    private Integer totalCount;
    private ItemsDto items;
}