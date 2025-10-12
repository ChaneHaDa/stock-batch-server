package com.chan.stock_batch_server.migration.dto;

import lombok.Data;

@Data
public class KRXStockData {
	private String basDt;          // 기준일자
	private String srtnCd;         // 종목코드
	private String isinCd;         // ISIN 코드
	private String itmsNm;         // 종목명
	private String mrktCtg;        // 시장구분
	private Integer clpr;          // 종가
	private Integer mkp;           // 시가
	private Integer hipr;          // 고가
	private Integer lopr;          // 저가
	private Long trqu;             // 거래량
	private Long trPrc;            // 거래대금
	private Long lstgStCnt;        // 상장주식수
}
