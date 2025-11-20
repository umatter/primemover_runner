# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# PrimeMover Codebase Architecture

This document provides a comprehensive overview of the PrimeMover architecture - a Java/Spring Boot web automation and data collection system designed for large-scale browser automation with anti-detection capabilities.

## Quick Reference: Common Commands

### Building and Running

```bash
# Build entire project (parent + all modules)
mvn clean install

# Build without running tests (faster)
mvn clean install -DskipTests

# Run the application (production mode)
cd primemover-runner
mvn spring-boot:run

# Run in local development mode with local config
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"
```

### Testing

```bash
# Run all tests
mvn test

# Run tests in specific module
cd primemover-runner
mvn test

# Run a specific test class
mvn test -Dtest=ClassName

# Run a specific test method
mvn test -Dtest=ClassName#methodName

# Run tests with specific profile
mvn test -Dspring.profiles.active=local
```

### Project Structure

```
primemover_runner/
├── primemover-runner/           # Main application module
│   ├── src/main/java/primemover/
│   │   ├── runner/              # Core execution engine
│   │   ├── service/             # Business logic & integrations
│   │   ├── model/               # Domain models
│   │   ├── api/                 # Backend API models & converters
│   │   ├── capture/             # Network traffic recording
│   │   ├── anticaptcha/         # CAPTCHA handling
│   │   ├── multilogin/          # Browser profile management
│   │   ├── util/                # Utility functions
│   │   ├── config/              # Configuration
│   │   └── lambda/              # AWS Lambda integration
│   ├── config/                  # Application properties
│   └── src/test/java/           # Unit tests
└── primemover-generated/        # Auto-generated OpenAPI client code
```

### Configuration Profiles

The application supports three startup modes via `global.mode` property:

- **DEVELOP**: Local development with mock data, includes assert jobs
- **INTEGRATION**: Real backend connection with test data
- **PRODUCTION**: Live deployment with real jobs

**⚠️ CRITICAL: API Endpoint Selection**

The `global.mode` setting controls which backend API endpoint is used to fetch jobs:

- **PRODUCTION** mode → `/crawlers-unprocessed` (normal production jobs)
- **DEVELOP** or **INTEGRATION** modes → `/crawlers-testing` (test data endpoint)

This is implemented in `CrawlerService.calculateCrawlerUrl()` (lines 61-74). When debugging locally, ensure your test data is in the correct endpoint, or set `global.mode=PRODUCTION` to fetch from `/crawlers-unprocessed`.

**⚠️ CRITICAL: MultiLogin X Core Version Requirements**

MultiLogin X enforces minimum browser core version requirements that increase over time:

- **Current minimum**: Core version 137+ (as of 2025)
- **Error message**: `400 Bad Request: "Can't set core older than X. Please set higher core version"`
- **Solution**: Update agent's MultiLogin profile template with higher core version
- **Recommended**: Set `"auto_update_core": true` in profile template to avoid future version issues

Profile template location: Database agent record, `multilogin_profile` field. Example:
```json
{
  "core_version": 137,
  "auto_update_core": true,
  "browser_type": "mimic",
  ...
}
```

If `auto_update_core: false` with outdated core version, MultiLogin X API will reject profile creation requests.

Configuration files are in `primemover-runner/config/`:
- `application.properties` - Base configuration
- `application-local.properties` - Local overrides (gitignored)
- `application-develop.properties` - Development profile
- `application-integration.properties` - Integration profile
- `application-production.properties` - Production profile

### Key Configuration Properties

Located in `primemover-runner/config/application.properties`:

```properties
# Execution
runner.MaxParallelExecution=2          # Concurrent job threads
runner.UpdateInterval=15               # Minutes between job polls
runner.MaxRedoMinutes=45               # Max job age to execute
runner.ProcessingTimeoutMinutes=180    # Job execution timeout

# MultiLogin
multilogin.StartupRetryCount=3
multilogin.StartupDelaySeconds=30

# Backend API
backend.endpoint.api=http://backend/api/

# HAR Recording Optimization (Feature: selective-har-recording)
har.recording.enabled=true                 # Global HAR recording toggle
har.recording.excludeJobTypes=WaitJob,...  # Job types to skip HAR recording
har.filter.enabled=false                   # Enable HAR content filtering
har.filter.removeStaticResources=false     # Filter images, CSS, fonts
har.filter.includeDomains=                 # Include only these domains (whitelist)
har.filter.excludeDomains=                 # Exclude tracking/analytics domains
har.filter.maxBodySize=                    # Truncate large bodies (bytes)
```

**HAR Recording Optimization:**

PrimeMover includes intelligent HAR (HTTP Archive) recording optimization to reduce storage costs and improve performance:

1. **Job Type Filtering** - Selective HAR recording based on job type
   - Skips jobs that don't generate network traffic (WaitJob, ScrollJobs, HandleAlertJob, AssertJobs)
   - Configurable via `har.recording.excludeJobTypes` property
   - Reduces HAR captures by ~55% in typical joblists
   - Implementation: `HarRecordingFilter` (primemover/capture/)

2. **Content Filtering** - Remove unnecessary data from HAR files
   - Filter static resources (images, CSS, fonts, JavaScript)
   - Include only specific domains (whitelist approach)
   - Exclude tracking/analytics domains (blacklist approach)
   - Truncate large request/response bodies
   - Remove specific HTTP headers
   - Configurable via `har.filter.*` properties
   - Implementation: `HarContentFilter` (primemover/capture/)
   - Applied in `BrowserUpRecorderProxy` before serialization

**Performance Impact:**
- Faster job execution (no HAR serialization for skipped jobs)
- Reduced memory usage during execution
- Smaller storage footprint (S3, file system, backend)
- Faster data transfer to storage backends

**Configuration Examples:**

*Production Optimization (blacklist approach):*
```properties
# Enable job type filtering (recommended)
har.recording.enabled=true
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob,AssertTextInFieldJob,AssertElementExistsJob,AssertScrolledMaxJob,AssertPageLoadedJob,AssertProxyJob,AssertProxyByGeosurfJob,AssertProxyByPrimemoverApiJob

# Enable content filtering - exclude unwanted domains
har.filter.enabled=true
har.filter.removeStaticResources=true
har.filter.excludeDomains=google-analytics.com,facebook.com,doubleclick.net
har.filter.maxBodySize=102400
```

*Focused Capture (whitelist approach):*
```properties
# Only capture traffic from specific domains
har.recording.enabled=true
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob

# Whitelist specific domains - all others filtered out
har.filter.enabled=true
har.filter.includeDomains=example.com,api.example.com
har.filter.removeStaticResources=true
```

**Files:**
- `primemover/capture/HarRecordingFilter.java` - Job type filtering logic
- `primemover/capture/HarContentFilter.java` - HAR content filtering logic
- `primemover/capture/browserup/BrowserUpRecorderProxy.java` - Applies filters before serialization
- `primemover/runner/TaskOperator.java` - Conditional HAR capture in runJob() method

### Common Development Tasks

**Adding a new job type:**
1. Create job model in `primemover/model/` extending `BaseJob`
2. Create handler in `primemover/runner/job/handler/` implementing `IJobHandler`
3. Register handler in `JobDispatcher.setup()`
4. Add API model in `primemover/api/model/`
5. Add conversion logic in `PrimemoverApiConverter`

**Debugging job execution:**
- Check logs for correlation ID (MDC context preserved across threads)
- Job states tracked through 14 different states (see State Transitions section)
- Browser screenshots saved after each job for visual debugging

**Working with MultiLogin:**
- MLX profiles require OpenAPI-generated client in `primemover-generated`
- Regenerate client: run Maven build (openapi-generator-maven-plugin)
- Source spec: `src/main/resources/MultiloginX.yaml`

## Table of Contents

1. [High-Level Architecture](#high-level-architecture)
2. [Package Structure](#package-structure)
3. [Core Components](#core-components)
4. [Job Execution Model](#job-execution-model)
5. [Browser Automation Integration](#browser-automation-integration)
6. [Integration Points](#integration-points)
7. [Configuration System](#configuration-system)
8. [Domain Terminology](#domain-terminology)

---

## High-Level Architecture

PrimeMover operates as a distributed job execution system with the following key characteristics:

### Design Philosophy
- **Scheduler-Executor Pattern**: Decouples job discovery from job execution
- **Pluggable Services**: Multiple implementations for storage, proxies, captcha solving, and browser types
- **Context-Based Execution**: Each job runs within an isolated execution context containing all necessary resources
- **Graceful Failure Handling**: Supports both strict and lenient job execution modes

### Data Flow
```
Backend API
    ↓
CrawlerService (fetch jobs)
    ↓
TaskManager (schedule)
    ↓
TaskExecutor (queue)
    ↓
JobListRunner (setup browser & teardown)
    ↓
TaskOperator (initialize context)
    ↓
JobDispatcher → Job Handlers (execute individual jobs)
    ↓
Data Writers → Storage Services (persist results)
```

---

## Package Structure

### Root Package: `primemover/`

#### 1. **`runner/`** - Core Execution Engine
Primary responsibility: Schedule, execute, and manage job lifecycles.

**Key Classes:**
- `TaskManager` (Main component)
  - Implements periodic job polling via Spring's `@Scheduled`
  - Checks backend for new job lists every 15 minutes (configurable)
  - Delegates to `TaskExecutor` for actual execution
  - Manages job timeouts and retry windows
  - File: `/primemover-runner/src/main/java/primemover/runner/TaskManager.java`

- `TaskExecutor`
  - Maintains a thread pool for parallel job execution
  - Configurable max parallelism via `runner.MaxParallelExecution` (default: 2)
  - Wraps jobs in `CompletableFuture` for async execution
  - Uses custom `MdcAwareThreadPoolExecutor` to preserve logging context
  - File: `/primemover-runner/src/main/java/primemover/runner/TaskExecutor.java`

- `TaskOperator`
  - Handles browser initialization and teardown
  - Coordinates proxy setup, anti-captcha configuration, and WebDriver creation
  - Manages the execution of individual jobs within a joblist
  - Captures data: screenshots, page source, network traffic (HAR), cookies, storage
  - Implements IP logging at job start/end
  - File: `/primemover-runner/src/main/java/primemover/runner/TaskOperator.java`

**Sub-packages:**
- `joblist/` - Orchestrates complete joblist execution
  - `JobListRunner`: Executes setup → job execution → teardown sequence
  - `JobListRunnerFactory`: Creates runner instances with proper dependencies
  - Manages joblist state transitions (RUNNING_INPROGRESS → RUNNING_INITDONE → RUNNING_JOBSDONE → DONE_SUCCESS)
  
- `job/` - Single job execution
  - `JobDispatcher`: Routes each job to its handler implementation
  - Factory pattern: maintains registry of job type → handler mappings
  - `IJobHandler` implementations for each job type
  
- `job/handler/` - Individual job handlers
  - `VisitJobHandler`: Navigate to URL
  - `ClickJobHandler`: Click element
  - `EnterTextFieldJobHandler`: Fill form field with typing simulation
  - `SingleSelectUrlJobHandler`: Select from multiple elements
  - `ScrollByLengthJobHandler`, `ScrollByDurationJobHandler`: Scroll actions
  - `WaitJobHandler`: Delay execution
  - `HandleAlertJobHandler`: Accept/dismiss browser dialogs
  - Assert handlers for testing (only in DEVELOP/INTEGRATION modes)
  - `LambdaSelectJobHandler`: Execute AWS Lambda function for smart selection
  
- `context/` - Execution context management
  - `ContextBase<T>`: Base context holding browser driver, proxy, captcha solver, crawler metadata
  - `ChromeContext`, `FirefoxContext`: Browser-specific contexts
  - `MultiloginContext`: Extends context for MultiLogin browser profiles
  - `JobDataRecorder`: Captures job execution data (page source, HAR, cookies, screenshot, location)
  - `JobAwarnessContextDecorator`: Wraps context to add job-specific awareness

#### 2. **`service/`** - Business Logic Layer
Implements external integrations and data persistence.

**Key Classes:**
- `CrawlerService` (ICrawlerService interface)
  - Fetches unprocessed jobs from backend API
  - Converts API responses to internal model objects
  - Supports retry logic via Spring's `@Retryable`
  - Methods:
    - `getCurrentTasks()`: Poll backend for jobs
    - `getTasksForCrawler(id)`: Get jobs for specific crawler
    - `writeJobData()`: POST job results back to backend
    - `writeS3JobData()`: Log S3 upload references
  - File: `/primemover-runner/src/main/java/primemover/service/CrawlerService.java`

- `JobService` (IJobService interface)
  - Updates job/joblist state in backend
  - Tracks execution progress through various states
  
- `IpService`
  - Detects IP address and geolocation info via external API
  - Used for IP rotation verification and logging
  - Factory pattern to support different IP service providers
  
- `AgentService`
  - Manages agent metadata updates
  - Updates MultiLogin profile IDs when profiles are created

- `Storage Services` (IStorageService interface)
  - `AwsS3StorageService`: Upload job data to AWS S3
  - `BackendStorageService`: POST data to backend API
  - `FileStorageService`: Save to local filesystem
  - Pluggable design: multiple implementations can be active simultaneously

- `DataWriter Services` (IDataWriter interface)
  - Different writers for different data formats
  - Decorated with `DataWriterCachingDecorator` for efficiency
  - Located in `datawriter/` sub-package

#### 3. **`model/`** - Domain Models
Represents the internal data structures (distinct from API models).

**Key Classes:**
- `Crawler`: Represents a browser/agent configuration with proxy and agent details
  - Contains both processed config and raw API configuration
  - File: `/primemover-runner/src/main/java/primemover/model/Crawler.java`

- `Joblist`: Container for ordered list of jobs to execute
  - State management: active, processing, processed, reviewed
  - Start time for scheduled execution
  - File: `/primemover-runner/src/main/java/primemover/model/Joblist.java`

- `IJob` (interface) and implementations: `BaseJob`, `VisitJob`, `ClickJob`, `EnterTextFieldJob`, `SingleSelectJob`, `WaitJob`, `HandleAlertJob`, `LambdaSelectJob`, scroll jobs, assert jobs
  - Base fields: id, name, description, captcha solving mode
  - `isFailsave()`: If true, exceptions don't stop joblist
  - `resetPreviousFailure()`: Override prior failure

- `IAgent` interface with implementations:
  - `BasicAgent`: Base implementation
  - `ChromeAgent`, `FirefoxAgent`: Browser-specific
  - `MultiLoginAgent`: For MultiLogin profiles (versions ML6 and MLX)
  
- Proxy models: `IProxy` interface with `HttpProxy`, `GeoSurfProxy`, `OxylabsProxy`, `BrightdataProxy`
  - Location-aware proxies for geographic spoofing
  
- `Configuration`: Task-specific configuration with selection parameters
  - Contains `SelectionConfiguration` with statistical parameters (alpha, beta, etc.)
  - Maps media outlets for domain-specific behavior

- `AgentType`, `DecisionType`, `HtmlSelectorType`, `TypingModeType`: Enums controlling behavior

#### 4. **`api/`** - Backend API Models and Conversion
Bridges between external backend API and internal models.

**Subpackages:**
- `model/`: API-specific data models (correspond to JSON from backend)
  - `CrawlerResult`, `Queue`, `Job`, `Agent`, `Proxy`, `Configuration`
  - These use Jackson annotations for deserialization
  - Different from `primemover.model` - these are API contracts
  
- `converter/`: Transformation logic
  - `PrimemoverApiConverter`: Massive conversion class (702 lines)
  - Converts API models → internal models
  - Handles job type routing (visitjob → VisitJob, etc.)
  - Applies default captcha solving behavior
  - Complex proxy type detection and configuration
  - File: `/primemover-runner/src/main/java/primemover/api/converter/PrimemoverApiConverter.java`

#### 5. **`capture/`** - Network Traffic Recording
Implements MITM proxy for capturing HTTP/HTTPS traffic.

**Key Classes:**
- `RecordingProxy`: Facade for proxy operations
  - Wraps actual proxy implementation
  - Manages connectivity verification and keep-alive pinging
  - Applies proxy to Selenium capabilities
  - Records HTTP traffic as HAR format
  - Supports authentication headers for proxy credentials
  - File: `/primemover-runner/src/main/java/primemover/capture/RecordingProxy.java`

- `IRecorderProxy` (interface)
  - Implemented by: `BrowserUpRecorderProxy`, `WireMockRecorderProxy`
  - Different backend implementations for network recording

- `RecordingProxyFactory`: Creates recorder instances
- `KeepAlivePing`: Maintains proxy connections
- `UrlVerificator`: Validates proxy connectivity
- `ProxyDecoratorFactory`, `FeedbackProxy`: Additional proxy capabilities

#### 6. **`anticaptcha/`** - CAPTCHA Handling
Integrates with AntiCaptcha service for solving CAPTCHAs.

**Key Classes:**
- `CaptchaSolverFactory`
  - Creates `PluginCaptchaSolver` instances
  - Lazy initialization via ApplicationContext
  - File: `/primemover-runner/src/main/java/primemover/anticaptcha/CaptchaSolverFactory.java`

- `ICaptchaSolver` interface with `PluginCaptchaSolver` implementation
  - Methods: `setOptions()`, `setupDriver()`, `solveCaptcha()`
  - Supports multiple solving modes: NEVER, PLUGIN, AUTOMATIC, MANUAL

- `AntiCaptchaService`: HTTP client for AntiCaptcha API
  - Creates tasks, polls for results
  - Located in `service/` sub-package

- `CaptchaSolvingType` enum: NEVER, PLUGIN, AUTOMATIC, MANUAL
- `CaptchaJobHelper`: Helper utilities for captcha handling
- `ResultStateEnum`: Tracks captcha solving status

#### 7. **`multilogin/`** - Browser Profile Management
Integrates with MultiLogin service for fingerprint management.

**Subpackages:**
- `ml6/`: MultiLogin v6 integration
  - `MultiLoginBrowserProfile`: Wraps ML6 browser profile
  - `MultiLoginBrowserProfileFactory`: Creates/retrieves profiles
  - Manages profile startup/shutdown, proxy configuration
  
- `mlx/`: MultiLogin X integration
  - Uses OpenAPI-generated client code
  - Located in `primemover-generated` module
  - Models and APIs auto-generated from `MultiloginX.yaml`

**MultiLogin Context:**
- Profiles provide isolated browser sessions with unique fingerprints
- Each profile can have custom proxy, plugins, canvas fingerprint
- Profiles must be properly initialized before WebDriver connection
- Profiles need graceful shutdown with data upload delays

#### 8. **`util/`** - Utility Functions
Helper classes for common operations.

**Key Classes:**
- `Page`: DOM manipulation and page source retrieval
- `Typing`: Simulates realistic typing with delays and typos
- `Scrolling`: Programmatic scrolling control
- `LocalStorage`, `SessionStorage`: Browser storage access
- `SelectingHelper`: Selection logic for items on page
- `NavigationHelper`: Page navigation utilities
- `StatisticsHelper`: Statistical calculations for behavior simulation
- `Waiter`: Wait conditions and polling

#### 9. **`config/`** - Configuration Management
- `Config`: Spring configuration class
- `StartupMode` enum: DEVELOP, INTEGRATION, PRODUCTION
- Mode-specific behaviors (e.g., assertion jobs only in DEVELOP/INTEGRATION)

#### 10. **`lambda/`** - AWS Lambda Integration
For serverless job execution and smart selection.

**Classes:**
- `LambdaCaller`: Invokes Lambda functions
- `LambdaCallerFactory`: Creates Lambda callers
- `LambdaException`: Custom exception type

---

## Core Components

### 1. Spring Boot Application Entry Point
**File:** `primemover/RunnerApplication.java`

```java
@SpringBootApplication
@EnableScheduling          // Enables @Scheduled methods
@EnableRetry               // Enables @Retryable annotation
public class RunnerApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RunnerApplication.class);
        app.run(args);
    }
}
```

Enables:
- Periodic job scheduling (TaskManager)
- Automatic retry for resilient operations
- Spring dependency injection throughout

### 2. Architectural Patterns

#### Factory Pattern
- `RecordingProxyFactory`: Creates proxy instances
- `CaptchaSolverFactory`: Creates captcha solvers
- `IpServiceFactory`: Creates IP service implementations
- `TaskOperatorFactory`: Creates task operators
- `JobListRunnerFactory`: Creates joblist runners
- `MultiLoginBrowserProfileFactory`: Creates browser profiles
- `TaskOperatorFactory`: Creates task operators
- `JobDispatcher.setup()`: Registers handlers (variant of factory)

#### Strategy Pattern
- `IStorageService`: Different storage backends (S3, file, API)
- `IRecorderProxy`: Different recording implementations
- `ICaptchaSolver`: Different captcha solving strategies
- Job handlers implement strategy for each job type

#### Decorator Pattern
- `DataWriterCachingDecorator`: Adds caching to data writers
- `JobAwarnessContextDecorator`: Adds job context to runner context
- `ProxyDecoratorFactory`: Adds features to proxy

#### Template Method Pattern
- `BaseJob`: Base implementation with common fields
- Job handlers follow template: setup → execute → cleanup

---

## Job Execution Model

### Execution Lifecycle

```
1. TaskManager.run() 
   ├─ Fetches unprocessed joblists from backend (CrawlerService)
   ├─ Filters by timing and state
   └─ Schedules eligible joblists for execution

2. TaskExecutor.run(crawler, joblist)
   └─ Submits JobListRunner to thread pool

3. JobListRunner.run()
   ├─ setup() - Initialize browser
   │  └─ TaskOperator.prepareExecution(crawler)
   │     ├─ Create proxy
   │     ├─ Initialize WebDriver (Chrome/Firefox/MultiLogin)
   │     ├─ Set captcha solver
   │     └─ Update state: RUNNING_INITDONE
   ├─ runJoblist() - Execute jobs sequentially
   │  └─ TaskOperator.runJobList(jobs)
   │     └─ For each job:
   │        ├─ Create job-aware context
   │        ├─ Capture IP (before first job)
   │        ├─ JobDispatcher.handle(jobContext)
   │        │  └─ Route to job handler
   │        │     └─ Execute job-specific logic
   │        ├─ Capture page source, screenshot, HAR, cookies, storage
   │        ├─ Capture IP (after last job)
   │        ├─ Write data via IDataWriter to IStorageService
   │        └─ Update state: SUCCESS/FAILURE/WARNING
   └─ tearDown() - Cleanup
      └─ TaskOperator.tearDown()
         ├─ Close WebDriver
         ├─ Stop MultiLogin profile (with delay for upload)
         └─ Tear down proxy
```

### State Transitions

**JobList States:**
```
Initial States:
- INACTIVE: Not scheduled for execution
- ACTIVE: Ready to execute

Processing States:
- RUNNING_INPROGRESS: Execution started
- RUNNING_INITDONE: Browser initialized successfully
- RUNNING_INITFAILED: Browser initialization failed
- RUNNING_JOBSDONE: All jobs completed
- RUNNING_JOBSFAILURE: Job execution failed

Terminal States:
- DONE_SUCCESS: Joblist completed successfully
- DONE_FAILURE: Uncaught error during execution
- DONE_INITFAILED: Failed to initialize browser
- DONE_JOBSFAILURE: Jobs failed during execution
- DONE_PROCESSING_TIMEOUT: Execution exceeded max time
- DONE_JOBLIST_EMPTY: No jobs in list
- SKIPPED_INACTIVE: Not active
- SKIPPED_REVIEWED: Already reviewed
- SKIPPED_TIMEDOUT: Started too late (after 45 min max delay)
```

**Job States:**
```
- RUNNING: Job in progress
- STORINGDATA: Writing results
- SUCCESS: Completed successfully
- FAILURE: Execution failed
- WARNING_JOBDATA: Failed to write results
- SKIPPED: Not executed (due to prior job failure, if not failsafe)
```

### Job Types

| Job Type | Purpose | Handler |
|----------|---------|---------|
| `VisitJob` | Navigate to URL | `VisitJobHandler` |
| `ClickJob` | Click HTML element | `ClickJobHandler` |
| `EnterTextFieldJob` | Fill form field | `EnterTextFieldJobHandler` |
| `SingleSelectJob` | Select from elements (random/first/calculated) | `SingleSelectUrlJobHandler` |
| `ScrollByLengthJob` | Scroll fixed pixels | `ScrollByLengthJobHandler` |
| `ScrollByDurationJob` | Scroll for duration | `ScrollByDurationJobHandler` |
| `WaitJob` | Pause execution | `WaitJobHandler` |
| `HandleAlertJob` | Accept/dismiss alert | `AlertJobHandler` |
| `LambdaSelectJob` | Lambda-based selection | `LambdaSelectJobHandler` |
| `SwitchToJob` | Switch to iframe/window | `SwitchToJobHandler` |
| Assert jobs | Test conditions (dev/integration) | Assert*JobHandler |

### Selection Decision Types

For `SingleSelectJob`, after selecting candidate elements:

1. **RANDOM**: Pick random element from candidates
2. **FIRST**: Pick first element
3. **LAST**: Pick last element
4. **CALCULATED**: Use extraction formula to calculate element index
   - Extracted value is numerically evaluated
   - Used to determine which element to select
   - Requires `criteriaExtractor` behavior

### Failsafe Behavior

If job's `isFailsave()` returns true:
- Exception during job execution is caught
- Execution continues to next job
- Joblist still completes successfully
- Previous job's failure doesn't skip subsequent jobs

If failsafe is false:
- Exception stops joblist execution
- Subsequent jobs are marked SKIPPED
- Joblist enters FAILURE state

Exception: `resetPreviousFailure()` overrides prior failures, allowing subsequent jobs to run.

---

## Browser Automation Integration

### WebDriver Management

Three browser implementations supported:

#### 1. Chrome Browser
**Class:** `TaskOperator.startupChrome()`
- Uses `ChromeDriver` from Selenium
- Options:
  - `--start-maximized`
  - `--disable-blink-features=AutomationControlled` (hides automation detection)
  - `--accept-insecure-certs`
- Context: `ChromeContext`

#### 2. Firefox Browser
**Class:** `TaskOperator.startupFirefox()`
- Uses `FirefoxDriver` from Selenium
- Options:
  - `setAcceptInsecureCerts(true)`
- Context: `FirefoxContext`
- Less commonly used for anti-detection (Chrome preferred)

#### 3. MultiLogin Browser
**Class:** `TaskOperator.startupMultilogin()` / `startupMultiloginX()`
- Two versions:
  - **ML6**: Traditional MultiLogin with browser profile management
  - **MLX**: MultiLogin X (newer version)
- Process:
  1. Create/retrieve browser profile (unique fingerprint)
  2. Apply proxy configuration to profile
  3. Start profile via MultiLogin API
  4. Connect RemoteWebDriver to profile endpoint
  5. Retry logic: up to 3 retries with 30-second delays
- Context: `MultiloginContext`
- Graceful shutdown: waits for profile upload before closing

### Capabilities and Options

All drivers receive:
```java
DesiredCapabilities capabilities = new DesiredCapabilities();
capabilities.setAcceptInsecureCerts(true);

// Proxy application
proxy.applyProxy(capabilities);

// Anti-captcha integration
captchaSolver.setOptions(crawler, options, proxy);
```

### Proxy Integration

Proxy is applied to capabilities before WebDriver initialization:
```java
Proxy seleniumProxy = new Proxy();
seleniumProxy.setHttpProxy("host:port");  // with optional auth
seleniumProxy.setSslProxy("host:port");
seleniumProxy.setProxyType(ProxyType.MANUAL);
capabilities.setCapability(CapabilityType.PROXY, seleniumProxy);
```

### MITM Proxy Architecture

The `RecordingProxy` wraps a MITM proxy that:
1. Intercepts all HTTP/HTTPS traffic
2. Records requests/responses (HAR format)
3. Supports proxy authentication (Geosurf, Oxylabs, Brightdata)
4. Validates connectivity before use
5. Maintains keep-alive pings to prevent idle timeouts

Implementations:
- **BrowserUpRecorderProxy**: Uses BrowserUp (littleproxy-based) MITM
- **WireMockRecorderProxy**: Uses WireMock (testing-focused)

### Data Capture

After each job, the following data is captured:

| Data | Source | Purpose |
|------|--------|---------|
| Page Source | `driver.getPageSource()` or JavaScript | HTML content for analysis |
| Screenshot | `TakesScreenshot` interface | Visual record |
| HAR | `recordingProxy.stopCapturing()` | Network traffic log |
| Cookies | `driver.manage().getCookies()` | Session state |
| Local Storage | JavaScript DOM access | Client-side persistence |
| Session Storage | JavaScript DOM access | Temporary client state |
| Location Info | IP service | Geo-location verification |

### Error Handling & Retry

Setup phase has retry logic:
```java
for (int curRetry = 1; curRetry <= maxRetryCountInit; curRetry++) {
    try {
        // Create proxy, initialize WebDriver
        // If successful, break
    } catch (Exception ex) {
        if (curRetry >= maxRetryCountInit) throw;
        // Otherwise retry
    }
}
```

Default: 3 retries for browser initialization.

---

## Integration Points

### 1. Backend API Integration

**Service:** `CrawlerService`

**Endpoints:**
- `GET /crawlers-unprocessed` - Fetch jobs (production)
- `GET /crawlers-testing` - Fetch jobs (testing)
- `GET /crawlers/{id}` - Get specific crawler
- `POST /reports` - Submit job results

**Response Conversion:**
```
API Response (CrawlerResult)
    ↓ (PrimemoverApiConverter)
Internal Models (Crawler, Joblist, IJob implementations)
```

**Retry Logic:** `@Retryable` on CrawlerService methods with configurable backoff.

### 2. AWS S3 Integration

**Service:** `AwsS3StorageService`

**Capabilities:**
- Upload job data (screenshots, page source, HAR)
- Create S3 bucket objects with metadata
- Support for different content types
- File naming: `job_{jobId}_{timestamp}`

**SDK:** AWS SDK v2 with S3 client

### 3. AntiCaptcha Integration

**Service:** `AntiCaptchaService` and `ICaptchaSolver`

**Flow:**
1. Create task via AntiCaptcha API
2. Poll for task status
3. When complete, retrieve solution
4. Apply solution to page (inject into iframe, etc.)

**Modes:**
- NEVER: Disable captcha solving
- PLUGIN: Use browser plugin
- AUTOMATIC: Auto-detect and solve
- MANUAL: User intervention

**Configuration:**
- API key from properties
- Task timeout
- Retry attempts

### 4. MultiLogin Integration

**Service:** `MultiLoginBrowserProfileFactory`

**API Interaction:**
- Create profiles with specific fingerprints
- Start profile (returns RemoteWebDriver endpoint)
- Stop profile (with graceful upload wait)
- Configure proxy on profiles
- Manage cookies/storage

**Versions:**
- **ML6**: REST API via `MultiLoginBrowserProfile`
- **MLX**: OpenAPI-generated client (in primemover-generated module)

### 5. IP Detection Integration

**Service:** `IpService` with factory pattern

**Purpose:**
- Detect current IP address
- Retrieve geolocation data
- Verify proxy effectiveness

**Implementations:**
- Multiple IP service providers supported
- Queryable at job start/end for logging

---

## Configuration System

### Spring Boot Profiles

Three profiles support different deployment modes:

1. **DEVELOP** - Local development
   - Uses mock/test data
   - Includes assert jobs for validation
   - Fake job appended for testing

2. **INTEGRATION** - Integration testing
   - Real backend connection but test data
   - Includes assert jobs

3. **PRODUCTION** - Live deployment
   - Real backend, real jobs
   - No assert jobs
   - Assert jobs filtered in `JobDispatcher.setup()`

### Application Properties

Key configuration parameters (via Spring `@Value` annotations):

```properties
# Global
global.mode=PRODUCTION

# Runner / Execution
runner.MaxParallelExecution=2          # Thread pool size
runner.UpdateInterval=15               # Minutes between job polls
runner.MaxRedoMinutes=45               # Max age for jobs to execute
runner.ProcessingTimeoutMinutes=180    # Timeout for in-progress joblists
runner.StartupRetryCount=3             # Browser init retries
runner.skipIpRecording=false           # Skip IP capture

# MultiLogin
multilogin.StartupRetryCount=3         # Profile startup retries
multilogin.StartupDelaySeconds=30      # Delay between retries

# AntiCaptcha
anticaptcha.check.enabled=true         # Check for captchas in page
anticaptcha.defaultMode=NEVER          # Default solving mode

# Recorder (MITM Proxy)
recorder.gracefullTeardownSeconds=20
recorder.connectTimeoutSeconds=45
recorder.iddleConnectionTimeoutSeconds=60
recorder.requestTimeoutSeconds=300
recorder.connectivityRetryCount=3
recorder.connectivityDelaySeconds=30

# Proxy
proxy.verifyConnections=true           # Validate proxy before use
proxy.pingConnections=true             # Keep-alive pinging

# Backend
backend.endpoint.api=http://backend/api/
```

### Environment-Specific Configuration

Different profiles can override properties:
- `application-develop.properties`
- `application-integration.properties`
- `application-production.properties`

Loaded via: `spring.config.location` or Spring's profile mechanism.

---

## Module Relationship: primemover-generated

**Purpose:** Auto-generated code from OpenAPI specifications

**Content:**
- MultiLogin X API client (Java classes generated from `MultiloginX.yaml`)
- Auto-generated request/response models
- Generated API client methods

**Build Process:**
- Maven OpenAPI Generator plugin
- Input: `src/main/resources/MultiloginX.yaml`
- Output: `target/generated-sources/openapi/primemover/multilogin/mlx/`

**Dependency:**
- primemover-runner depends on primemover-generated (compile scope)
- Provides type-safe MultiLogin X API access

**Key Generated Components:**
- `primemover.multilogin.mlx.api.*` - API methods
- `primemover.multilogin.mlx.model.*` - Data models

---

## Domain Terminology

### Core Entities

**Crawler**
- Represents a browser configuration (agent + proxy)
- Has one or more joblists to execute
- Associated with media outlets (for selection behavior)

**Agent**
- Browser instance (Chrome, Firefox, or MultiLogin)
- Has unique fingerprint (especially for MultiLogin)
- Location-aware for geo-spoofing

**Joblist (Queue)**
- Ordered collection of jobs
- Scheduled for specific time
- Tracks execution state and results
- Has status: active, processing, processed, reviewed

**Job**
- Individual task (visit URL, click element, fill form, etc.)
- Belongs to joblist, executed in order
- Can be failsafe or strict
- Tracks: start time, end time, success, failure, warning, skipped states

**Proxy**
- Connection credentials and endpoint
- Types: HTTP, Geosurf, Oxylabs, Brightdata
- May include authentication (username/password)

**Context**
- Runtime execution environment
- Contains: WebDriver, proxy, captcha solver, crawler metadata
- Job-aware variant tracks current job and data recorder

### Execution Concepts

**Scheduling Window**
- Update interval: Time between job polls (15 minutes default)
- Redo minutes: Maximum age of jobs to execute (45 minutes default)
- Processing timeout: Maximum execution duration (180 minutes default)

**Recording Proxy (MITM)**
- Transparent proxy intercepting HTTP/HTTPS traffic
- Records traffic in HAR (HTTP Archive) format
- Supports authentication for third-party proxies

**Task**
- Internal term for joblist + crawler combination
- Scheduled for execution in future

**Behavior**
- Configuration parameters for jobs (extracted from API)
- Key-value pairs: selector, URL, decision type, etc.
- Converted by `PrimemoverApiConverter`

**Decision Type** (for selection jobs)
- Enum: RANDOM, FIRST, LAST, CALCULATED
- Controls how to pick element from candidates

**Captcha Solving Type**
- NEVER: Disabled
- PLUGIN: Browser plugin
- AUTOMATIC: Auto-detect and solve
- MANUAL: User interaction

### Technical Concepts

**IJobHandler**
- Strategy interface for job execution
- Registered in `JobDispatcher.setup()`
- Route: IJob class → handler implementation

**IStorageService**
- Strategy interface for data persistence
- Multiple implementations active simultaneously
- Data written via IDataWriter

**IDataWriter**
- Format-specific writer (HAR, JSON, etc.)
- Can write to multiple storage backends

**IRunnerContext**
- Holds execution state during job run
- Includes WebDriver, proxy, options, captcha solver
- Decorated with job awareness for per-job metadata

**IProxy**
- Interface for proxy credentials/configuration
- Implementations: HttpProxy, GeoSurfProxy, etc.
- Location-aware for geographic behavior

---

## Key Design Patterns

### 1. Factory Pattern
Creates instances with proper dependency injection:
```java
@Autowired
RecordingProxyFactory recorderFactory;

RecordingProxy recorder = recorderFactory.createRecordingProxy(crawler.getProxy());
```

### 2. Strategy Pattern
Different implementations selected at runtime:
```java
interface ICaptchaSolver {
    void setOptions(...);
    void setupDriver(...);
}
// Implementations: PluginCaptchaSolver, etc.
```

### 3. Decorator Pattern
Adds behavior without modifying original:
```java
IDataWriter writer = new DataWriterCachingDecorator(baseWriter);
IRunnerContext jobContext = JobAwarnessContextDecorator.decorate(context, job, ...);
```

### 4. Template Method
Base class defines algorithm, subclasses implement steps:
```java
abstract class BaseJob implements IJob {
    // Common fields: id, name, description, captchaSolving
}
class VisitJob extends BaseJob {
    private String url;
}
```

### 5. Dispatcher Pattern
Route requests to handlers:
```java
JobDispatcher dispatcher = new JobDispatcher();
dispatcher.setup();  // Register handlers
dispatcher.handle(jobContext);  // Route to handler
```

---

## Execution Flow: Complete Example

```
1. Application starts
   - TaskManager and TaskExecutor initialization
   
2. TaskManager.run() [Every 60 seconds, executes every 15 minutes]
   - Call CrawlerService.getCurrentTasks()
   - Backend API returns crawlers with joblists
   
3. For each joblist within time window:
   - Create Runnable with delayed execution
   - Schedule in TaskManager.scheduler
   
4. At scheduled time:
   - TaskExecutor.run(crawler, joblist)
   - Submit JobListRunner to thread pool
   
5. JobListRunner.run() [In executor thread]
   - setup() phase:
     * TaskOperator.prepareExecution(crawler)
     * Initialize proxy
     * Create WebDriver (Chrome/Firefox/MultiLogin)
     * Set captcha solver
     * Update joblist state: RUNNING_INITDONE
   
   - runJoblist() phase:
     * For each job in joblist:
       - Create job-aware context
       - If first job: capture IP before
       - JobDispatcher.handle(context)
         * Route to handler based on job type
         * Execute job-specific logic (click, type, navigate, etc.)
       - Capture page source, screenshot, HAR, cookies
       - If last job: capture IP after
       - Write data to storage services
       - Update job state (SUCCESS/FAILURE/WARNING)
   
   - tearDown() phase:
     * Close WebDriver
     * Stop MultiLogin profile
     * Tear down proxy
     * Update joblist state: DONE_SUCCESS
     
6. Data persists via:
   - AwsS3StorageService uploads to S3
   - BackendStorageService POSTs to API
   - FileStorageService saves locally

7. JobService updates backend with completion status
```

---

## Dependencies and Technology Stack

### Core Framework
- **Spring Boot 2.x**: Application framework
- **Spring Scheduling**: `@Scheduled` for job polling
- **Spring Retry**: `@Retryable` for resilient operations
- **Spring Actuator**: Health checks and metrics

### Browser Automation
- **Selenium 4.27.0**: WebDriver for browser control
  - selenium-java, selenium-api, selenium-remote-driver
  - selenium-chrome-driver, selenium-firefox-driver
- **WebDriverManager 6.1.0**: Automatic driver management

### Proxy & Network
- **BrowserUp Proxy 2.1.2**: MITM proxy implementation
- **LittleProxy 2.3.1**: Proxy foundation
- **WireMock 3.9.2**: Test mocking

### Cloud Integration
- **AWS SDK 2.26.13**: S3 and Lambda services

### Data Processing
- **Jackson**: JSON serialization/deserialization
- **OpenAPI Generator 7.12.0**: Code generation from specs
- **Commons Lang3, Commons Text**: Utility libraries
- **Commons Math3**: Statistical calculations

### Logging & Monitoring
- **SLF4J**: Logging API with MDC support
- **Logback**: Default logging implementation

### Code Generation
- **Lombok**: Annotation-based code generation
- **Build Helper Maven Plugin**: Source code management

---

## Performance Considerations

### Thread Pool Configuration
- Default: 2 parallel joblists
- Configurable via `runner.MaxParallelExecution`
- Each thread: one browser instance (CPU/memory intensive)

### Job Scheduling
- Polling interval: 15 minutes (default)
- Can be adjusted via `runner.UpdateInterval`
- Too frequent: excessive backend load
- Too infrequent: delayed job execution

### Data Capture
- Page source: Captured via JavaScript (preferred) with fallback to WebDriver
- Screenshots: PNG format (potentially large)
- HAR: Network traffic (can grow large for heavy pages)
- Cookies/Storage: Generally small

### Proxy Performance
- MITM proxy adds ~5-20% latency per request
- Keep-alive pings maintain connections
- Connectivity verification can add startup time

---

## Testing Approach

### Development Mode (DEVELOP profile)
- Uses mock crawler service
- Injects fake jobs for testing
- Includes assert jobs for validation
- Time-adjusted jobs (set to current time)

### Integration Testing
- Real backend connection
- Test credentials required
- Assert jobs enabled for validation

### Unit Testing
- Individual job handlers
- Converter logic
- Service methods
- Located in `src/test/java`

---

This architecture provides a robust, scalable system for web automation with extensive anti-detection capabilities and flexible integration points for various services and technologies.
