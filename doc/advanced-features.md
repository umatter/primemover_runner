# Advanced Features

This document covers PrimeMover's sophisticated capabilities for web automation, anti-detection, and system monitoring.

## Smart Navigation System

The `NavigationHelper` class provides intelligent page navigation with automatic failure recovery.

### Extension Failure Recovery

PrimeMover automatically handles MultiLogin extension loading issues:

```java
// Automatic workaround for MultiLogin extension loading issues
if (driver.getCurrentUrl().startsWith("chrome-extension://")) {
    logger.warn("Extension still loading. Trying workaround! Start running in new tab...");
    // Automatically opens new tab and switches context
    js.executeScript("window.open('" + target + "', '_blank');");
    driver.switchTo().window(sortedHandles.last());
}
```

### Page Load Detection

- **Smart Waiting**: Detects when new pages have actually loaded vs. still loading
- **Timeout Handling**: Configurable timeouts with graceful degradation
- **Element Monitoring**: Tracks HTML element changes to detect navigation completion

### Usage Example

```java
NavigationHelper navHelper = new NavigationHelper(driver, 30000); // 30 second timeout
boolean success = navHelper.navigateToUrl("https://example.com");
if (success) {
    // Page loaded successfully
} else {
    // Handle navigation failure
}
```

## Statistical Behavior Simulation

The `StatisticsHelper` provides realistic user behavior simulation for anti-detection.

### Multinomial Selection

```java
// Realistic selection based on probability distributions
double[] probabilities = {0.6, 0.3, 0.1}; // Most likely, somewhat likely, unlikely
int selectedIndex = StatisticsHelper.makeMultinominalSelection(probabilities);
```

This enables:

- **Realistic Click Patterns**: Simulate human-like element selection
- **Randomized Timing**: Vary interaction timing based on probability distributions
- **Behavioral Modeling**: Model real user behavior patterns for anti-detection

### Implementation Example

```java
// Select element based on probability distribution
List<WebElement> elements = driver.findElements(By.cssSelector(".clickable"));
if (!elements.isEmpty()) {
    // Define probabilities (first element most likely, last least likely)
    double[] probabilities = new double[elements.size()];
    for (int i = 0; i < elements.size(); i++) {
        probabilities[i] = 1.0 / (i + 1); // Decreasing probability
    }
    
    int selectedIndex = StatisticsHelper.makeMultinominalSelection(probabilities);
    elements.get(selectedIndex).click();
}
```

## Thread-Safe Execution

### MDC-Aware Threading

The `MdcAwareThreadPoolExecutor` preserves logging context across thread boundaries:

```java
// Correlation IDs survive thread switches
MDC.put("correlationId", "cr-" + crawler.getId() + "|jl-" + joblist.getId());
// Execute job in thread pool - correlation ID is preserved
// Automatic cleanup prevents memory leaks
```

### Correlation Tracking

- **Request Tracing**: Track requests across all system components
- **Debug Support**: Easily correlate logs across complex execution flows
- **Performance Monitoring**: Track execution times and resource usage per job/joblist

### Usage Example

```java
// Set up correlation context
MDC.put("crawler.id", crawler.getId().toString());
MDC.put("joblist.id", joblist.getId().toString());
MDC.put("correlationId", "cr-" + crawler.getId() + "|jl-" + joblist.getId());

try {
    // Execute jobs - correlation ID will be preserved across threads
    jobListRunner.execute(joblist);
} finally {
    // Clean up MDC to prevent memory leaks
    MDC.clear();
}
```

## HTTP Traffic Analysis

### Complete Traffic Capture

PrimeMover captures comprehensive HTTP traffic for analysis and debugging:

- **HAR Files**: Complete HTTP Archive format capture
- **Request/Response Logging**: Detailed logging via `LoggingInterceptor`
- **Debugging Support**: Full visibility into browser-server communication

### API Communication Monitoring

```java
// Automatic logging of all REST API calls
@Retryable
public Boolean updateJobState(IJob job, JobState state, ...) {
    logger.info("updating job-state. Retry {}", 
        RetrySynchronizationManager.getContext().getRetryCount());
    // Full request/response logging for debugging
}
```

### Traffic Recording Configuration

```properties
# Enable HTTP traffic recording
recorder.enabled=true
recorder.certificate.type=PKCS12
recorder.certificate.path=cert/2024/ca-cert.p12
recorder.certificate.alias=Primemover
recorder.certificate.password=CHANGEME-password

# Configure logging detail level
logging.level.primemover.util.LoggingInterceptor=DEBUG
logging.level.org.springframework.web.client.RestTemplate=TRACE
```

## Anti-Detection Capabilities

### Browser Fingerprinting

PrimeMover integrates with MultiLogin for advanced browser fingerprint management:

- **Profile Management**: Create, update, and manage browser profiles
- **Fingerprint Control**: Customize browser fingerprints for anti-detection
- **Extension Management**: Automatic installation and configuration of browser extensions
- **API Client**: Auto-generated from OpenAPI specification for type safety

### CAPTCHA Solving

Automatic CAPTCHA detection and solving via AntiCaptcha service:

```java
// Configure CAPTCHA solving behavior
CaptchaSolvingType solvingType = CaptchaSolvingType.BEFORE; // Solve before job execution

// CAPTCHA solver automatically detects and solves CAPTCHAs
CaptchaSolverResult result = captchaSolver.solveCaptcha(driver, solvingType);
if (result.isCaptchaSolved()) {
    logger.info("CAPTCHA solved successfully in {} seconds", result.getSolvingSeconds());
} else {
    logger.warn("CAPTCHA solving failed: {}", result.getErrorMessage());
}
```

### Proxy Support

Multiple proxy providers with anti-detection features:

- **GeoSurf**: Geographic proxy service with location-specific IPs
- **Oxylabs**: Enterprise proxy provider with residential IPs
- **Brightdata**: Large-scale proxy network with session management
- **Session Management**: Automatic session handling and rotation

## Data Recording and Analysis

### Comprehensive Data Capture

The system captures complete browser state during job execution:

```java
RecordedJobData data = new RecordedJobDataBuilder(job)
    .setScreenshot(screenshot)           // Visual state capture
    .setHar(harData)                    // Complete HTTP traffic
    .setPageSource(pageSource)          // DOM state
    .setCookies(cookies)                // Session cookies
    .setSessionStorage(sessionStorage)   // Browser session storage
    .setLocalStorage(localStorage)       // Browser local storage
    .setLocation(locationInfo)          // IP geolocation data
    .setCapchaResultBefore(beforeResult) // CAPTCHA solving results
    .setCapchaResultAfter(afterResult)
    .build();
```

### Performance Optimization

- **Caching Decorator**: `DataWriterCachingDecorator` prevents repeated data processing
- **Lazy Evaluation**: Data is only processed when actually needed
- **Memory Management**: Efficient byte array handling for large screenshots/HAR files

### Storage Flexibility

- **Pluggable Writers**: `IDataWriter` interface for different storage backends
- **ZIP Compression**: `SingleZipEntryDataWriterBase` for efficient storage
- **File Naming**: Automatic timestamp and job-based file naming via `StorageUtility`

## Monitoring and Observability

### Built-in Monitoring Capabilities

#### State Tracking
- **Job States**: `RUNNING`, `SUCCESS`, `FAILURE`, `WARNING`, `SKIPPED`
- **Joblist States**: 14 comprehensive states from `RUNNING_INPROGRESS` to `DONE_SUCCESS`
- **Execution Metrics**: Start times, end times, duration tracking
- **Retry Tracking**: Automatic retry count logging with Spring Retry

#### Correlation and Tracing
```java
// Automatic correlation ID generation
MDC.put("crawler.id", crawler.getId().toString());
MDC.put("joblist.id", jobs.getId().toString());
MDC.put("correlationId", "cr-" + crawler.getId() + "|jl-" + jobs.getId());
```

#### Performance Monitoring
- **Execution Duration**: Complete timing for jobs and joblists
- **Resource Usage**: Memory and thread pool monitoring
- **API Response Times**: REST API call duration tracking
- **Browser Performance**: Page load times and navigation metrics

### Health Checks and Diagnostics

#### System Health Indicators
- **Context Validation**: `ContextBase.isValid()` ensures proper setup
- **Resource Availability**: WebDriver, proxy, and CAPTCHA solver status
- **External Service Health**: MultiLogin, AntiCaptcha, and backend API connectivity
- **Data Writer Status**: Storage system availability and performance

#### Diagnostic Information
- **Browser State**: Current URL, page source, cookies, storage
- **Proxy Status**: Connection health and geographic location
- **Extension Status**: CAPTCHA solver and other browser extension health
- **Job Configuration**: Complete job parameter validation and status

### Monitoring Configuration

```properties
# Enable Spring Actuator endpoints
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized

# Configure health check details
management.health.diskspace.enabled=true
management.health.db.enabled=false  # No database in PrimeMover

# Enable metrics collection
management.metrics.export.prometheus.enabled=true
management.metrics.distribution.percentiles-histogram.http.server.requests=true
```

## Advanced Configuration

### Environment-Specific Settings

```properties
# Production configuration example
spring.profiles.active=production
runner.MaxRedoMinutes=45
browser.headless=true
anticaptcha.enabled=true
multilogin.enabled=true

# Retry configuration
spring.retry.max-attempts=3
spring.retry.backoff.delay=1000
spring.retry.backoff.multiplier=2
```

### Security Configuration

- **Credential Management**: External configuration for API keys and passwords
- **Network Security**: Proxy authentication and encrypted connections
- **Data Protection**: Secure storage of captured data and logs
- **Access Control**: Role-based access to job execution and monitoring

### Performance Tuning

#### Connection Pooling
```properties
# HTTP client configuration
spring.http.client.connection-timeout=10000
spring.http.client.read-timeout=30000
spring.http.client.max-connections=100
spring.http.client.max-connections-per-route=20
```

#### Thread Pool Configuration
```properties
# Execution thread pool settings
runner.MaxParallelExecution=5
runner.thread-pool.core-size=2
runner.thread-pool.max-size=10
runner.thread-pool.queue-capacity=100
```

#### Memory Management
```properties
# JVM memory settings
-Xmx4g
-Xms2g
-XX:MaxMetaspaceSize=512m
-XX:+UseG1GC
-XX:G1HeapRegionSize=16m
```

## Integration Examples

### Custom Job Handler

```java
@Component
public class CustomJobHandler implements IJobHandler {
    
    @Override
    public void handle(IJobAwareRunnerContext<?> context) {
        IJob job = context.getJob();
        
        if (!(job instanceof CustomJob)) {
            throw new IllegalArgumentException("Handler can only process CustomJob instances");
        }
        
        CustomJob customJob = (CustomJob) job;
        WebDriver driver = context.getDriver();
        
        // Use advanced features
        NavigationHelper navHelper = new NavigationHelper(driver, 30000);
        StatisticsHelper statsHelper = new StatisticsHelper();
        
        // Navigate with smart detection
        boolean navigated = navHelper.navigateToUrl(customJob.getTargetUrl());
        if (!navigated) {
            throw new RuntimeException("Navigation failed");
        }
        
        // Select element using probability distribution
        List<WebElement> elements = driver.findElements(By.cssSelector(customJob.getSelector()));
        if (!elements.isEmpty()) {
            double[] probabilities = {0.7, 0.2, 0.1}; // Favor first elements
            int selectedIndex = statsHelper.makeMultinominalSelection(probabilities);
            elements.get(selectedIndex).click();
        }
        
        // Record comprehensive data
        context.getJobDataRecorder().setJobData("custom-action", "completed");
    }
}
```

### Custom Data Writer

```java
public class AdvancedDataWriter extends RecordedJobDataBaseWriter {
    
    @Override
    public String getIdentifier() {
        return "advanced-data";
    }
    
    @Override
    protected void writeJobData(RecordedJobData data, OutputStream os) throws IOException {
        // Use caching for performance
        DataWriterCachingDecorator cachedWriter = new DataWriterCachingDecorator(this);
        
        // Write comprehensive data
        writeScreenshot(data.getScreenshot(), os);
        writeHarData(data.getHar(), os);
        writePageSource(data.getPageSource(), os);
        writeCookies(data.getCookies(), os);
        writeStorageData(data.getSessionStorage(), data.getLocalStorage(), os);
        writeLocationInfo(data.getLocation(), os);
        writeCaptchaResults(data.getCaptchaResults(), os);
    }
}
```

This advanced features documentation provides comprehensive coverage of PrimeMover's sophisticated capabilities for enterprise-grade web automation.
