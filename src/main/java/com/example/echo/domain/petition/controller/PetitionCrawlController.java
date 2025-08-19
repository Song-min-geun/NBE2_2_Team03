package com.example.echo.domain.petition.controller;

import com.example.echo.domain.petition.dto.response.PetitionCrawlPerformanceMetrics;
import com.example.echo.domain.petition.service.PetitionCrawlService;
import com.example.echo.global.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Petition Crawl", description = "청원 크롤링 관련 API")
@RestController
@RequestMapping("/api/petition/crawl")
@RequiredArgsConstructor
public class PetitionCrawlController {

    private final PetitionCrawlService petitionCrawlService;

    @Operation(summary = "크롤링 성능 지표 조회", description = "현재까지의 크롤링 성능 지표를 조회합니다.")
    @GetMapping("/performance")
    public ApiResponse<PetitionCrawlPerformanceMetrics> getPerformanceMetrics() {
        PetitionCrawlPerformanceMetrics metrics = petitionCrawlService.getPerformanceMetrics();
        return ApiResponse.success(metrics);
    }

    @Operation(summary = "크롤링 성능 지표 초기화", description = "크롤링 성능 지표를 초기화합니다.")
    @PostMapping("/performance/reset")
    public ApiResponse<String> resetPerformanceMetrics() {
        petitionCrawlService.resetPerformanceMetrics();
        return ApiResponse.success("성능 지표가 초기화되었습니다.");
    }
}
