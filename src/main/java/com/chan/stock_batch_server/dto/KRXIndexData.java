package com.chan.stock_batch_server.migration.dto;

import lombok.Data;

@Data
public class KRXIndexData {
	private String basDt;          // 기준일자
	private String idxNm;          // 지수명
	private String idxCsf;         // 지수분류
	private Double clpr;           // 종가
	private Double mkp;            // 시가
	private Double hipr;           // 고가
	private Double lopr;           // 저가
	private Double fltRt;          // 등락률
}
