package com.example.echo.domain.petition.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PetitionCrawlPerformanceMonitor {
    
    private final AtomicLong totalCrawlTime = new AtomicLong(0);
    private final AtomicInteger totalPetitionsProcessed = new AtomicInteger(0);
    private final AtomicInteger totalPagesProcessed = new AtomicInteger(0);
    private final AtomicInteger totalErrors = new AtomicInteger(0);
    private final ConcurrentHashMap<String, LocalDateTime> crawlStartTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Duration> crawlDurations = new ConcurrentHashMap<>();
    
    public void startCrawl(String crawlId) {
        crawlStartTimes.put(crawlId, LocalDateTime.now());
        log.info("크롤링 시작: {}", crawlId);
    }
    
    public void endCrawl(String crawlId) {
        LocalDateTime startTime = crawlStartTimes.get(crawlId);
        if (startTime != null) {
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            crawlDurations.put(crawlId, duration);
            totalCrawlTime.addAndGet(duration.toMillis());
            crawlStartTimes.remove(crawlId);
            log.info("크롤링 완료: {} (소요시간: {}ms)", crawlId, duration.toMillis());
        }
    }
    
    public void incrementPetitionsProcessed(int count) {
        totalPetitionsProcessed.addAndGet(count);
    }
    
    public void incrementPagesProcessed() {
        totalPagesProcessed.incrementAndGet();
    }
    
    public void incrementErrors() {
        totalErrors.incrementAndGet();
    }
    
    public PetitionCrawlPerformanceMetrics getPerformanceMetrics() {
        long avgCrawlTime = totalCrawlTime.get() / Math.max(1, crawlDurations.size());
        double petitionsPerSecond = calculatePetitionsPerSecond();
        double pagesPerSecond = calculatePagesPerSecond();
        double errorRate = calculateErrorRate();
        
        return PetitionCrawlPerformanceMetrics.builder()
                .totalPetitionsProcessed(totalPetitionsProcessed.get())
                .totalPagesProcessed(totalPagesProcessed.get())
                .totalErrors(totalErrors.get())
                .averageCrawlTimeMs(avgCrawlTime)
                .petitionsPerSecond(petitionsPerSecond)
                .pagesPerSecond(pagesPerSecond)
                .errorRate(errorRate)
                .totalCrawlsCompleted(crawlDurations.size())
                .build();
    }
    
    private double calculatePetitionsPerSecond() {
        if (totalCrawlTime.get() == 0) return 0.0;
        return (double) totalPetitionsProcessed.get() / (totalCrawlTime.get() / 1000.0);
    }
    
    private double calculatePagesPerSecond() {
        if (totalCrawlTime.get() == 0) return 0.0;
        return (double) totalPagesProcessed.get() / (totalCrawlTime.get() / 1000.0);
    }
    
    private double calculateErrorRate() {
        int total = totalPetitionsProcessed.get();
        if (total == 0) return 0.0;
        return (double) totalErrors.get() / total * 100.0;
    }
    
    public void resetMetrics() {
        totalCrawlTime.set(0);
        totalPetitionsProcessed.set(0);
        totalPagesProcessed.set(0);
        totalErrors.set(0);
        crawlStartTimes.clear();
        crawlDurations.clear();
        log.info("성능 지표가 초기화되었습니다.");
    }
}

