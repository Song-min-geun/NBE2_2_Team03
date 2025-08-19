package com.example.echo.domain.petition.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PetitionCrawlPerformanceMetrics {
    private int totalPetitionsProcessed;      // 처리된 총 청원 수
    private int totalPagesProcessed;          // 처리된 총 페이지 수
    private int totalErrors;                  // 총 에러 수
    private long averageCrawlTimeMs;          // 평균 크롤링 시간 (밀리초)
    private double petitionsPerSecond;        // 초당 처리된 청원 수
    private double pagesPerSecond;            // 초당 처리된 페이지 수
    private double errorRate;                 // 에러율 (%)
    private int totalCrawlsCompleted;         // 완료된 총 크롤링 수
}

