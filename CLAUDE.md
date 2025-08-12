# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Continuous build (auto-rebuild on changes)
./gradlew build --continuous
```

## Application Configuration

- **Port**: The application runs on port 9090 (configured in application.properties)
- **API Documentation**: Available at `http://localhost:9090/api-docs` (Swagger UI)
- **Database**: MySQL (localhost:3306/test1, username: root, password: 1234)
- **File Upload Directory**: `./inbound`

## Architecture Overview

This is a Spring Boot 3.4.4 application built with Spring Batch for processing stock data. The architecture follows a layered approach:

### Key Components

- **Batch Jobs**: Two main batch jobs for calculating stock and index prices
  - `calcStockPriceJob`: Processes monthly stock price calculations
  - `calcIndexPriceJob`: Processes monthly index price calculations

- **Controllers**: RESTful APIs for triggering batch jobs
  - `MonthlyBatchJobController`: Synchronous batch job execution
  - `AsyncMonthlyBatchJobController`: Asynchronous batch job execution  
  - `UploadController`: File upload functionality

- **Models/Entities**: JPA entities for stock data
  - `Stock`, `StockPrice`, `IndexInfo`, `IndexPrice`
  - `CalcStockPrice`, `CalcIndexPrice` (calculated entities)
  - `StockNameHistory` (historical data)

### Package Structure

- `config/`: Spring configuration classes including batch job configurations, async config, and Swagger setup
- `controller/`: REST API endpoints
- `dto/`: Data Transfer Objects for API communication
- `model/`: JPA entity classes
- `service/`: Business logic and batch processing services

### Batch Processing Architecture

The application uses Spring Batch with two distinct processing pipelines:
1. **Stock Price Processing**: Calculates monthly aggregated stock prices
2. **Index Price Processing**: Calculates monthly aggregated index prices

Both support:
- Single month processing via REST API
- Date range processing for bulk operations
- Asynchronous execution capabilities

### Technology Stack

- Java 17
- Spring Boot 3.4.4 with Spring Batch and Spring Data JPA
- MySQL 8 with Hibernate
- Swagger/OpenAPI 3 for API documentation
- Lombok for code generation