package com.chan.stock_batch_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockItemDto {
    private String basDt;        // 기준일 (20230103)
    private String srtnCd;       // 단축코드 (900110)
    private String isinCd;       // ISIN 코드 (HK0000057197)
    private String itmsNm;       // 종목명 (이스트아시아홀딩스)
    private String mrktCtg;      // 시장구분 (KOSDAQ)
    private String clpr;         // 종가 (171)
    private String vs;           // 전일대비 (-8)
    private String fltRt;        // 등락률 (-4.47)
    private String mkp;          // 시가 (179)
    private String hipr;         // 고가 (180)
    private String lopr;         // 저가 (169)
    private String trqu;         // 거래량 (3599183)
    private String trPrc;        // 거래대금 (621375079)
    private String lstgStCnt;    // 상장주식수 (291932050)
    private String mrktTotAmt;   // 시가총액 (49920380550)
}