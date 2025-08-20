# Stock Batch Server API 가이드

## 📋 **개요**

Stock Batch Server는 주식 데이터를 처리하고 주식명 변경 이력을 자동으로 관리하는 Spring Boot 배치 시스템입니다.

### **주요 기능**
- 📊 실제 공공데이터 API 응답 형태 JSON 파일 처리
- 🔄 StockNameHistory 자동 관리 (주식명 변경 감지 및 이력 생성)
- 📦 완전한 배치 처리 파이프라인 (Reader → Processor → Writer)
- 🛡️ 포괄적인 데이터 검증 및 예외 처리
- 📱 REST API를 통한 주식 정보 조회

---

## 🚀 **시작하기**

### **애플리케이션 실행**
```bash
# 빌드
./gradlew build -x test

# 실행
java -jar build/libs/stock-batch-server-0.0.1-SNAPSHOT.jar
```

### **접속 정보**
- **애플리케이션**: http://localhost:9090
- **API 문서 (Swagger)**: http://localhost:9090/api-docs

---

## 📁 **JSON 파일 형식**

### **지원하는 파일명 형식**
```
20230103.json  (YYYYMMDD.json)
20240115.json
20241201.json
```

### **JSON 구조 (실제 공공데이터 API 응답 형태)**
```json
{
  "response": {
    "header": {
      "resultCode": "00",
      "resultMsg": "NORMAL SERVICE."
    },
    "body": {
      "numOfRows": 10000,
      "pageNo": 1,
      "totalCount": 2690,
      "items": {
        "item": [
          {
            "basDt": "20230103",           // 기준일
            "srtnCd": "900110",            // 단축코드
            "isinCd": "HK0000057197",      // ISIN 코드
            "itmsNm": "이스트아시아홀딩스", // 종목명
            "mrktCtg": "KOSDAQ",           // 시장구분
            "clpr": "171",                 // 종가
            "mkp": "179",                  // 시가
            "hipr": "180",                 // 고가
            "lopr": "169",                 // 저가
            "trqu": "3599183",             // 거래량
            "trPrc": "621375079",          // 거래대금
            "lstgStCnt": "291932050",      // 상장주식수
            "mrktTotAmt": "49920380550"    // 시가총액
          }
        ]
      }
    }
  }
}
```

---

## 🔌 **API 엔드포인트**

### **1. 파일 업로드 및 배치 처리**

#### `POST /api/v1/batch/upload-json`
JSON 파일을 업로드하고 배치 작업을 실행합니다.

**파라미터:**
- `files`: MultipartFile[] (JSON 파일들)
- `processImmediately`: boolean (기본값: true)

**응답 예시:**
```json
{
  "success": true,
  "message": "Files processed successfully",
  "data": [
    "20230103.json — uploaded",
    "20230103.json — batch job status: COMPLETED"
  ]
}
```

**cURL 예시:**
```bash
curl -X POST http://localhost:9090/api/v1/batch/upload-json \
  -F "files=@20230103.json" \
  -F "processImmediately=true"
```

---

### **2. 주식 정보 조회**

#### `GET /api/v1/stocks/{stockId}/name-history`
특정 주식의 주식명 변경 이력을 조회합니다.

**응답 예시:**
```json
{
  "success": true,
  "data": [
    {
      "name": "이스트아시아홀딩스",
      "startAt": "2023-01-03",
      "endAt": null
    },
    {
      "name": "이스트아시아홀딩스(구)",
      "startAt": "2020-01-01",
      "endAt": "2023-01-02"
    }
  ]
}
```

#### `GET /api/v1/stocks/{stockId}/current-name`
현재 유효한 주식명을 조회합니다.

**응답 예시:**
```json
{
  "success": true,
  "data": "이스트아시아홀딩스"
}
```

#### `GET /api/v1/stocks/isin/{isinCode}`
ISIN 코드로 주식 정보를 조회합니다.

**예시:**
```bash
GET /api/v1/stocks/isin/HK0000057197
```

---

## 🔄 **StockNameHistory 자동 관리 시스템**

### **작동 원리**
1. **첫 번째 데이터 처리**: 새로운 종목 발견 시 첫 이력 생성
2. **이름 변경 감지**: 기존 종목의 이름이 변경된 경우 자동 처리
   - 기존 이력의 종료일 설정
   - 새로운 이력 생성 (시작일: 오늘, 종료일: null)

### **예시 시나리오**
```
1단계: 20230103.json 업로드
  → "이스트아시아홀딩스" 이력 생성 (2023-01-03 ~ null)

2단계: 20240101.json 업로드 (이름이 "이스트아시아홀딩스 주식회사"로 변경)
  → 기존 이력 종료 (2023-01-03 ~ 2023-12-31)
  → 새 이력 생성 (2024-01-01 ~ null)
```

---

## 🛡️ **데이터 검증 규칙**

### **ISIN 코드**
- 형식: `KR + 10자리 숫자` (예: KR7005930003)
- 필수 입력

### **종목명**
- 최대 길이: 100자
- 필수 입력

### **가격 데이터**
- 모든 가격은 양수여야 함
- 가격 관계: 고가 ≥ 저가, 고가 ≥ 시가/종가, 저가 ≤ 시가/종가
- 거래량, 거래대금, 상장주식수는 음수 불가

### **날짜**
- 시작일 ≤ 종료일
- 미래 날짜는 1년 이내만 허용

---

## 📊 **배치 작업 구조**

### **처리 플로우**
```
JSON 파일 → Reader → Processor → Writer → 데이터베이스
            ↓         ↓          ↓
          파싱     데이터검증    StockNameHistory
                   및 변환      자동 관리
```

### **청크 단위**
- 기본 청크 크기: 10건
- 트랜잭션 단위로 처리

---

## 🗄️ **데이터베이스 스키마**

### **주요 테이블**
- `stock`: 주식 기본 정보
- `stock_price`: 일별 가격 데이터
- `stock_name_history`: 주식명 변경 이력 ⭐
- `calc_stock_price`: 계산된 월별 수익률

### **StockNameHistory 테이블**
```sql
CREATE TABLE stock_name_history (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  start_at DATE NOT NULL,
  end_at DATE NULL,
  stock_id INT NOT NULL,
  FOREIGN KEY (stock_id) REFERENCES stock(id)
);
```

---

## 🚨 **에러 처리**

### **일반적인 에러 응답**
```json
{
  "success": false,
  "message": "Validation failed for stock HK0000057197: ISIN code must follow pattern 'KR' + 10 digits"
}
```

### **주요 에러 코드**
- `400`: 잘못된 요청 (파일 형식, 데이터 검증 실패)
- `404`: 리소스를 찾을 수 없음
- `500`: 서버 내부 오류

---

## 🔧 **설정 정보**

### **application.properties**
```properties
# 포트
server.port=9090

# 파일 업로드
file.upload-dir=./inbound
spring.servlet.multipart.max-file-size=10MB

# 데이터베이스
spring.datasource.url=jdbc:mysql://localhost:3306/test1
spring.datasource.username=root
spring.datasource.password=1234
```

---

## 🎯 **사용 예시**

### **1. 기본 사용 시나리오**
```bash
# 1단계: 첫 번째 데이터 업로드
curl -X POST http://localhost:9090/api/v1/batch/upload-json \
  -F "files=@20230103.json"

# 2단계: 주식명 이력 확인
curl http://localhost:9090/api/v1/stocks/1/name-history

# 3단계: 현재 주식명 확인
curl http://localhost:9090/api/v1/stocks/1/current-name
```

### **2. 주식명 변경 테스트**
```bash
# 1단계: 원본 데이터 업로드
curl -X POST http://localhost:9090/api/v1/batch/upload-json \
  -F "files=@20230103.json"

# 2단계: 변경된 주식명 데이터 업로드  
curl -X POST http://localhost:9090/api/v1/batch/upload-json \
  -F "files=@20240101.json"

# 3단계: 이력 확인 (2개의 이력이 생성됨)
curl http://localhost:9090/api/v1/stocks/1/name-history
```

---

## 📋 **개발 참고사항**

### **주요 클래스**
- `StockNameHistoryService`: 주식명 이력 관리 핵심 로직
- `StockDataImportBatchConfig`: 배치 작업 설정
- `StockDataValidationService`: 데이터 검증
- `StockController`: REST API 엔드포인트

### **확장 포인트**
- 새로운 데이터 소스 추가
- 추가적인 검증 규칙 구현
- 배치 작업 모니터링 기능
- 실시간 알림 시스템

---

## 🤝 **기여하기**

이 프로젝트는 Claude Code와 함께 개발되었습니다. 개선사항이나 버그 리포트는 GitHub 이슈로 등록해 주세요.

---

**🤖 Generated with [Claude Code](https://claude.ai/code)**