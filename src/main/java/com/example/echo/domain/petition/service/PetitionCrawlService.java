package com.example.echo.domain.petition.service;

import com.example.echo.domain.member.entity.Member;
import com.example.echo.domain.member.repository.MemberRepository;
import com.example.echo.domain.petition.entity.Category;
import com.example.echo.domain.petition.entity.Petition;
import com.example.echo.domain.petition.crawling.PetitionCrawl;
import com.example.echo.domain.petition.crawling.PetitionDataExtractor;
import com.example.echo.domain.petition.dto.response.PetitionCrawlPerformanceMetrics;
import com.example.echo.domain.petition.repository.PetitionRepository;
import com.example.echo.global.exception.ErrorCode;
import com.example.echo.global.exception.PetitionCustomException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PetitionCrawlService {

    @Autowired
    private PetitionRepository petitionRepository;

    @Autowired
    private PetitionCrawlPerformanceMonitor performanceMonitor;

    @Autowired
    @Qualifier("detailFetchExecutor")
    private Executor detailFetchExecutor;

    private static final Logger logger = LoggerFactory.getLogger(PetitionCrawlService.class);

    // WebDriver 풀 관리를 위한 클래스
    private static class WebDriverPool {
        private final ConcurrentLinkedQueue<WebDriver> availableDrivers = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<WebDriver> busyDrivers = new ConcurrentLinkedQueue<>();
        private final int maxDrivers;

        public WebDriverPool(int maxDrivers) {
            this.maxDrivers = maxDrivers;
            initializeDrivers();
        }

        private void initializeDrivers() {
            for (int i = 0; i < maxDrivers; i++) {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
                WebDriver driver = new ChromeDriver(options);
                availableDrivers.offer(driver);
            }
        }

        public WebDriver acquireDriver() {
            WebDriver driver = availableDrivers.poll();
            if (driver != null) {
                busyDrivers.offer(driver);
                return driver;
            }
            // 모든 드라이버가 사용 중이면 새로 생성
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
            WebDriver newDriver = new ChromeDriver(options);
            busyDrivers.offer(newDriver);
            return newDriver;
        }

        public void releaseDriver(WebDriver driver) {
            if (busyDrivers.remove(driver)) {
                if (availableDrivers.size() < maxDrivers) {
                    availableDrivers.offer(driver);
                } else {
                    driver.quit();
                }
            }
        }

        public void shutdown() {
            availableDrivers.forEach(WebDriver::quit);
            busyDrivers.forEach(WebDriver::quit);
            availableDrivers.clear();
            busyDrivers.clear();
        }
    }

    private final WebDriverPool webDriverPool;

    public PetitionCrawlService() {
        this.webDriverPool = new WebDriverPool(5); // 최대 5개의 WebDriver 관리
    }

    // 비동기 크롤링 메서드
    @Async("petitionCrawlExecutor")
    public CompletableFuture<List<PetitionCrawl>> dynamicCrawlAsync(Long id, String url) {
        String crawlId = UUID.randomUUID().toString();
        performanceMonitor.startCrawl(crawlId);
        
        try {
            List<PetitionCrawl> result = dynamicCrawl(id, url);
            performanceMonitor.incrementPetitionsProcessed(result.size());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            performanceMonitor.incrementErrors();
            logger.error("크롤링 중 오류 발생: ", e);
            throw e;
        } finally {
            performanceMonitor.endCrawl(crawlId);
        }
    }

    // 기존 동기 메서드 (비동기 호출을 위해 유지)
    public List<PetitionCrawl> dynamicCrawl(Long id, String url) {
        WebDriver driver = webDriverPool.acquireDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        
        try {
            driver.manage().window().setSize(new Dimension(390, 844));
            List<PetitionCrawl> crawledData = new ArrayList<>();
            
            driver.get(url);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".list_card")));
            
            int pageCount = 0;
            while (true) {
                pageCount++;
                performanceMonitor.incrementPagesProcessed();
                
                List<WebElement> petitionCards = driver.findElements(By.cssSelector(".item_card"));
                
                // 청원 데이터 수집을 비동기로 처리
                List<CompletableFuture<PetitionCrawl>> futures = petitionCards.stream()
                    .map(card -> CompletableFuture.supplyAsync(() -> 
                        extractPetitionData(card, driver, wait), detailFetchExecutor))
                    .collect(Collectors.toList());
                
                // 모든 비동기 작업 완료 대기
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                // 결과 수집
                for (CompletableFuture<PetitionCrawl> future : futures) {
                    try {
                        PetitionCrawl petition = future.get();
                        if (petition != null) {
                            crawledData.add(petition);
                        }
                    } catch (Exception e) {
                        performanceMonitor.incrementErrors();
                        logger.error("청원 데이터 처리 중 오류: ", e);
                    }
                }
                
                // 다음 페이지로 이동
                if (!navigateToNextPage(driver, wait, pageCount)) {
                    break;
                }
            }
            
            return crawledData;
            
        } finally {
            webDriverPool.releaseDriver(driver);
        }
    }

    // 개별 청원 데이터 추출
    private PetitionCrawl extractPetitionData(WebElement petition, WebDriver driver, WebDriverWait wait) {
        try {
            WebElement link = petition.findElement(By.tagName("a"));
            String href = link.getAttribute("href");
            
            // 이미 존재하는 청원인지 확인
            Optional<Petition> existingPetition = petitionRepository.findByUrl(href);
            if (existingPetition.isPresent()) {
                return null;
            }
            
            String title = petition.findElement(By.cssSelector(".desc")).getText();
            String period = petition.findElement(By.cssSelector(".period")).getText();
            String category = petition.findElement(By.cssSelector(".category")).getText();
            String count = petition.findElement(By.cssSelector(".count")).getText();
            
            PetitionCrawl petitionObj = new PetitionCrawl(title, period, category, count, href, null);
            
            // 상세 정보를 비동기로 가져오기
            CompletableFuture.runAsync(() -> 
                fetchPetitionDetailsAsync(driver, wait, petitionObj), detailFetchExecutor);
            
            return petitionObj;
            
        } catch (Exception e) {
            performanceMonitor.incrementErrors();
            logger.error("청원 데이터 추출 중 오류: ", e);
            return null;
        }
    }

    // 비동기 상세 정보 가져오기
    private void fetchPetitionDetailsAsync(WebDriver driver, WebDriverWait wait, PetitionCrawl petitionCrawl) {
        try {
            driver.get(petitionCrawl.getHref());
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
            
            WebElement contentElement = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".pre.contentTxt"))
            );
            wait.until(webDriver -> !contentElement.getText().trim().isEmpty());
            
            String content = contentElement.getText();
            petitionCrawl.changeContent(content);
            
        } catch (Exception e) {
            performanceMonitor.incrementErrors();
            logger.error("상세 정보 가져오기 중 오류: ", e);
        }
    }

    // 페이지 이동 로직 개선
    private boolean navigateToNextPage(WebDriver driver, WebDriverWait wait, int currentPage) {
        try {
            Thread.sleep(100);
            
            WebElement nextButton = wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector("button.btn.next-button"))
            );
            nextButton.click();
            
            Thread.sleep(100);
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
            
            return true;
            
        } catch (Exception e) {
            logger.info("더 이상 페이지가 없거나 다음 버튼을 찾을 수 없습니다.");
            return false;
        }
    }

    // 동의자 수 업데이트 (비동기 처리)
    @Async("detailFetchExecutor")
    public CompletableFuture<Integer> fetchAgreeCountAsync(String url) {
        WebDriver driver = webDriverPool.acquireDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        
        try {
            driver.get(url);
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
            
            int retries = 3;
            while (retries-- > 0) {
                try {
                    WebElement agreeCountElement = wait.until(
                        ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".count"))
                    );
                    String agreeCountText = agreeCountElement.getText();
                    String agreeCountNum = PetitionDataExtractor.extractNumber(agreeCountText);
                    return CompletableFuture.completedFuture(Integer.parseInt(agreeCountNum));
                } catch (TimeoutException e) {
                    logger.warn("동의자 수 요소 대기 시간 초과. 재시도 중... 남은 시도: {}", retries);
                }
            }
            
            performanceMonitor.incrementErrors();
            return CompletableFuture.completedFuture(-1);
            
        } catch (Exception e) {
            performanceMonitor.incrementErrors();
            logger.error("동의자 수 가져오기 중 오류: ", e);
            return CompletableFuture.completedFuture(-1);
        } finally {
            webDriverPool.releaseDriver(driver);
        }
    }
    // WebDriver 풀 정리
    public void shutdown() {
        webDriverPool.shutdown();
    }
}