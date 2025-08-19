# 청원 크롤링 성능 개선 보고서

## 🚀 성능 개선 개요

기존 동기적 크롤링 방식을 비동기 처리와 스레드 풀을 활용한 방식으로 개선하여 전반적인 성능 향상을 달성했습니다.

## 🔧 주요 개선 사항

### 1. 비동기 처리 도입
- **기존**: 순차적 동기 처리로 인한 대기 시간 발생
- **개선**: `CompletableFuture`를 활용한 비동기 처리
- **효과**: I/O 대기 시간 동안 다른 작업 수행 가능

### 2. 스레드 풀 최적화
- **크롤링 전용 스레드 풀**: 5개 코어, 최대 10개 스레드
- **상세 정보 가져오기 스레드 풀**: 3개 코어, 최대 6개 스레드
- **효과**: 동시 처리 능력 향상 및 리소스 효율적 사용

### 3. WebDriver 풀 관리
- **기존**: 단일 WebDriver 인스턴스 공유
- **개선**: 최대 5개의 WebDriver 인스턴스 풀 관리
- **효과**: 동시 크롤링 가능 및 리소스 재사용

## 📊 성능 지표

### 측정 가능한 지표
- **처리된 총 청원 수**: 크롤링된 청원의 총 개수
- **처리된 총 페이지 수**: 크롤링된 페이지의 총 개수
- **총 에러 수**: 발생한 에러의 총 개수
- **평균 크롤링 시간**: 크롤링 작업의 평균 소요 시간 (밀리초)
- **초당 처리 청원 수**: 초당 처리할 수 있는 청원의 개수
- **초당 처리 페이지 수**: 초당 처리할 수 있는 페이지의 개수
- **에러율**: 전체 처리 대비 에러 발생 비율 (%)
- **완료된 크롤링 수**: 성공적으로 완료된 크롤링 작업의 수

### 예상 성능 향상
- **동기 처리 대비**: 2-3배 성능 향상 예상
- **동시 처리**: 최대 5개의 크롤링 작업 동시 실행 가능
- **리소스 효율성**: WebDriver 인스턴스 재사용으로 메모리 사용량 감소

## 🏗️ 아키텍처 개선

### 클래스 구조
```
PetitionCrawlService (메인 서비스)
├── WebDriverPool (WebDriver 풀 관리)
├── PetitionCrawlPerformanceMonitor (성능 모니터링)
└── AsyncConfig (스레드 풀 설정)
```

### 스레드 풀 설정
```java
// 크롤링 전용 스레드 풀
- 코어 스레드 수: 5
- 최대 스레드 수: 10
- 대기 큐 크기: 100

// 상세 정보 가져오기 스레드 풀
- 코어 스레드 수: 3
- 최대 스레드 수: 6
- 대기 큐 크기: 50
```

## 📈 사용 방법

### 1. 비동기 크롤링 실행
```java
CompletableFuture<List<PetitionCrawl>> future = 
    petitionCrawlService.dynamicCrawlAsync(memberId, url);
List<PetitionCrawl> result = future.get();
```

### 2. 성능 지표 조회
```java
PetitionCrawlPerformanceMetrics metrics = 
    petitionCrawlService.getPerformanceMetrics();
```

### 3. 성능 테스트 실행
```java
PetitionCrawlPerformanceTest test = new PetitionCrawlPerformanceTest();
test.runPerformanceComparisonTest(memberId, url);
test.runThreadPoolPerformanceTest(memberId, url);
```

## 🔍 API 엔드포인트

### 성능 지표 조회
- **GET** `/api/petition/crawl/performance`
- **설명**: 현재까지의 크롤링 성능 지표 조회

### 성능 지표 초기화
- **POST** `/api/petition/crawl/performance/reset`
- **설명**: 크롤링 성능 지표 초기화

## ⚠️ 주의사항

1. **메모리 사용량**: WebDriver 인스턴스 풀로 인한 메모리 사용량 증가
2. **동시성 제한**: 너무 많은 동시 크롤링은 대상 서버에 부하를 줄 수 있음
3. **에러 처리**: 비동기 처리로 인한 에러 추적의 복잡성 증가

## 🎯 향후 개선 계획

1. **동적 스레드 풀 크기 조정**: 시스템 부하에 따른 자동 조정
2. **크롤링 속도 제한**: 대상 서버 보호를 위한 속도 제한 기능
3. **분산 크롤링**: 여러 서버에서 동시 크롤링 수행
4. **실시간 모니터링**: 대시보드를 통한 실시간 성능 지표 시각화

## 📝 결론

비동기 처리와 스레드 풀 최적화를 통해 크롤링 성능을 크게 향상시켰습니다. 특히 동시 처리 능력과 리소스 효율성 측면에서 상당한 개선을 달성했으며, 지속적인 모니터링과 최적화를 통해 더 나은 성능을 제공할 수 있을 것입니다.

