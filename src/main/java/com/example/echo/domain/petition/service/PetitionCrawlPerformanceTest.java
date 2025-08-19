package com.example.echo.domain.petition.service;

import com.example.echo.domain.petition.crawling.PetitionCrawl;
import com.example.echo.domain.petition.dto.response.PetitionCrawlPerformanceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetitionCrawlPerformanceTest {

    private final PetitionCrawlService petitionCrawlService;

    /**
     * 동기 vs 비동기 크롤링 성능 비교 테스트
     */
    public void runPerformanceComparisonTest(Long memberId, String url) {
        log.info("=== 크롤링 성능 비교 테스트 시작 ===");
        
        // 1. 동기 크롤링 테스트
        LocalDateTime syncStart = LocalDateTime.now();
        try {
            List<PetitionCrawl> syncResult = petitionCrawlService.dynamicCrawl(memberId, url);
            LocalDateTime syncEnd = LocalDateTime.now();
            Duration syncDuration = Duration.between(syncStart, syncEnd);
            
            log.info("동기 크롤링 결과:");
            log.info("- 처리된 청원 수: {}", syncResult.size());
            log.info("- 소요 시간: {}ms", syncDuration.toMillis());
            log.info("- 초당 처리 청원 수: {:.2f}", 
                (double) syncResult.size() / (syncDuration.toMillis() / 1000.0));
            
        } catch (Exception e) {
            log.error("동기 크롤링 테스트 실패: ", e);
        }

        // 2. 비동기 크롤링 테스트
        LocalDateTime asyncStart = LocalDateTime.now();
        try {
            CompletableFuture<List<PetitionCrawl>> asyncFuture = 
                petitionCrawlService.dynamicCrawlAsync(memberId, url);
            
            List<PetitionCrawl> asyncResult = asyncFuture.get();
            LocalDateTime asyncEnd = LocalDateTime.now();
            Duration asyncDuration = Duration.between(asyncStart, asyncEnd);
            
            log.info("비동기 크롤링 결과:");
            log.info("- 처리된 청원 수: {}", asyncResult.size());
            log.info("- 소요 시간: {}ms", asyncDuration.toMillis());
            log.info("- 초당 처리 청원 수: {:.2f}", 
                (double) asyncResult.size() / (asyncDuration.toMillis() / 1000.0));
            
        } catch (Exception e) {
            log.error("비동기 크롤링 테스트 실패: ", e);
        }

        // 3. 성능 지표 조회
        PetitionCrawlPerformanceMetrics metrics = petitionCrawlService.getPerformanceMetrics();
        log.info("=== 전체 성능 지표 ===");
        log.info("- 총 처리된 청원 수: {}", metrics.getTotalPetitionsProcessed());
        log.info("- 총 처리된 페이지 수: {}", metrics.getTotalPagesProcessed());
        log.info("- 총 에러 수: {}", metrics.getTotalErrors());
        log.info("- 평균 크롤링 시간: {}ms", metrics.getAverageCrawlTimeMs());
        log.info("- 초당 처리 청원 수: {:.2f}", metrics.getPetitionsPerSecond());
        log.info("- 초당 처리 페이지 수: {:.2f}", metrics.getPagesPerSecond());
        log.info("- 에러율: {:.2f}%", metrics.getErrorRate());
        log.info("- 완료된 크롤링 수: {}", metrics.getTotalCrawlsCompleted());
        
        log.info("=== 크롤링 성능 비교 테스트 완료 ===");
    }

    /**
     * 스레드 풀 성능 테스트
     */
    public void runThreadPoolPerformanceTest(Long memberId, String url) {
        log.info("=== 스레드 풀 성능 테스트 시작 ===");
        
        // 여러 크롤링 작업을 동시에 실행
        CompletableFuture<List<PetitionCrawl>>[] futures = new CompletableFuture[3];
        
        LocalDateTime start = LocalDateTime.now();
        
        for (int i = 0; i < 3; i++) {
            futures[i] = petitionCrawlService.dynamicCrawlAsync(memberId, url);
        }
        
        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures).join();
        
        LocalDateTime end = LocalDateTime.now();
        Duration totalDuration = Duration.between(start, end);
        
        log.info("동시 크롤링 테스트 결과:");
        log.info("- 동시 실행 크롤링 수: 3");
        log.info("- 총 소요 시간: {}ms", totalDuration.toMillis());
        log.info("- 평균 크롤링 시간: {}ms", totalDuration.toMillis() / 3);
        
        // 성능 지표 확인
        PetitionCrawlPerformanceMetrics metrics = petitionCrawlService.getPerformanceMetrics();
        log.info("- 총 처리된 청원 수: {}", metrics.getTotalPetitionsProcessed());
        log.info("- 초당 처리 청원 수: {:.2f}", metrics.getPetitionsPerSecond());
        
        log.info("=== 스레드 풀 성능 테스트 완료 ===");
    }
}

