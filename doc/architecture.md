# PrimeMover Architecture

PrimeMover is built as a Spring Boot application with two main modules that work together to provide sophisticated web automation capabilities.

## System Overview

### Job Execution Flow

PrimeMover follows a sophisticated pipeline for executing web automation tasks:

```
API Request → PrimemoverApiConverter → Job Objects → JobListRunner → Context System → Data Recording → State Management
```

1. **API Input**: External job definitions received via REST API
2. **Conversion**: `PrimemoverApiConverter` transforms API models to internal job objects
3. **Execution**: `JobListRunner` orchestrates job execution with state management
4. **Context**: Job-aware context provides execution environment (WebDriver, proxy, CAPTCHA solver)
5. **Recording**: Comprehensive data capture (screenshots, HAR files, cookies, storage)
6. **State Management**: Sophisticated state tracking with retry logic and failure recovery

## Module Architecture

### primemover-generated - External Service Integration

This module automatically generates Java client libraries from API specifications:

- **Purpose**: Provides type-safe Java clients for external services (primarily MultiLogin)
- **How it works**: Uses OpenAPI Generator to convert the 3000+ line MultiLogin API specification into hundreds of Java classes
- **What it provides**: Complete client library for browser profile management, fingerprinting, and anti-detection features
- **Build process**: Runs during Maven compilation to generate fresh client code from API specs

**Key benefit**: Instead of manually writing HTTP client code for complex external APIs, this module auto-generates it, ensuring type safety and automatic updates when APIs change.

### primemover-runner - Core Application Logic

This is the main application that orchestrates web automation:

- **Purpose**: Contains all business logic, job execution, and web automation capabilities
- **Dependencies**: Uses the generated clients from `primemover-generated` to interact with external services
- **What it does**: Manages job scheduling, browser automation, CAPTCHA solving, data collection, and storage
- **Runtime**: This is the actual Spring Boot application that runs and executes automation tasks

**Key benefit**: Focuses purely on business logic without worrying about low-level API integration details, which are handled by the generated clients.

### How They Work Together

1. **Build time**: `primemover-generated` creates MultiLogin client libraries
2. **Runtime**: `primemover-runner` uses these clients to manage browser profiles and execute automation jobs
3. **Example flow**: Runner creates a MultiLogin profile → starts browser session → runs Selenium automation → collects data → stores results

This separation keeps external API complexity isolated from core business logic, making the system more maintainable and testable.

## Core Components

### Job System Architecture
```
IJob (interface)
└── BaseJob (abstract)
    ├── SelectorBaseJob (abstract) - for HTML element interaction
    │   ├── ClickJob / TryClickJob (failsafe variant)
    │   ├── EnterTextFieldJob / TryEnterTextFieldJob
    │   ├── SingleSelectJob
    │   └── SwitchToJob / TrySwitchJob
    ├── ScrollBaseJob (abstract) - for scrolling actions
    │   ├── ScrollByDurationJob
    │   └── ScrollByLengthJob
    ├── WaitJob
    ├── HandleAlertJob / TryHandleAlertJob
    ├── VisitJob (resets failure state by default)
    └── LambdaSelectJob (AWS Lambda integration)
```

### Context System
```
IRunnerContext (basic execution environment)
└── IJobAwareRunnerContext (job-specific context)
    └── JobAwarnessContextDecorator (implementation with job awareness)
```

### State Management
- **JobState**: `RUNNING`, `SUCCESS`, `FAILURE`, `WARNING`, `SKIPPED`
- **JoblistState**: 14 different states for comprehensive execution tracking
- **StartupMode**: `DEVELOP`, `INTEGRATION`, `PRODUCTION`

## Job Execution Engine

### JobListRunner - Core Orchestration

The `JobListRunner` is the sophisticated execution engine that manages job lifecycle:

#### Execution States
- **Setup Phase**: `SUCCESS`, `FAILURE`, `SKIPPED`, `NO_JOBS`
- **Running Phase**: `NOTSTARTED`, `SUCCESS`, `FAILURE`
- **Comprehensive State Tracking**: 14 different `JoblistState` values

#### Advanced Features
- **Timeout Management**: Configurable timeout (45 minutes default) with automatic skipping
- **Retry Logic**: Spring `@Retryable` annotations for resilient API communication
- **Correlation Tracking**: MDC-based correlation IDs for request tracing
- **Resource Management**: Automatic cleanup and teardown on completion or failure

#### State Management Flow
```java
// Execution lifecycle with state transitions
RUNNING_INPROGRESS → RUNNING_INITDONE → RUNNING_JOBSDONE → DONE_SUCCESS
                  ↘ RUNNING_INITFAILED → DONE_INITFAILED
                  ↘ SKIPPED_TIMEDOUT (if timeout exceeded)
                  ↘ DONE_JOBLIST_EMPTY (if no jobs)
```

### Data Recording Pipeline

#### Comprehensive Data Capture
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

#### Performance Optimization
- **Caching Decorator**: `DataWriterCachingDecorator` prevents repeated data processing
- **Lazy Evaluation**: Data is only processed when actually needed
- **Memory Management**: Efficient byte array handling for large screenshots/HAR files

#### Storage Flexibility
- **Pluggable Writers**: `IDataWriter` interface for different storage backends
- **ZIP Compression**: `SingleZipEntryDataWriterBase` for efficient storage
- **File Naming**: Automatic timestamp and job-based file naming via `StorageUtility`

### Context System

#### Execution Environment
The context system provides clean dependency injection for job execution:

```java
// Basic execution context
IRunnerContext<OptionsType> context = new ContextBase<>()
    .setDriver(webDriver)
    .setRecordingProxy(proxy)
    .setCaptchaSolver(solver)
    .setCrawler(crawler);

// Job-aware context with additional capabilities
IJobAwareRunnerContext<OptionsType> jobContext = 
    JobAwarnessContextDecorator.decorate(context, job, dataRecorder);
```

#### Context Validation
- **Validation**: `ContextBase.isValid()` ensures all required components are present
- **Type Safety**: Generic type parameters ensure WebDriver options compatibility
- **Resource Management**: Automatic cleanup and lifecycle management

## API Conversion System

The `PrimemoverApiConverter` is the heart of job creation, handling:

- **Multi-Version Support**: Supports both "version 1" and "version 2" job formats
- **Complex Configuration**: Transforms behavior maps into strongly-typed job objects
- **Proxy Detection**: Automatically detects and configures proxy types (GeoSurf, Oxylabs, Brightdata, HTTP/HTTPS)
- **Agent Conversion**: Handles Chrome, Firefox, and MultiLogin agent configurations
- **Validation**: Comprehensive parameter validation with meaningful error messages

Example conversion flow:
```java
// API job definition with behaviors
{
  "type": "clickjob",
  "behaviors": [
    {"name": "selectionType", "value": "CSS"},
    {"name": "selector", "value": ".submit-button"},
    {"name": "expectsPageReload", "value": "true"}
  ]
}

// Converts to internal ClickJob object
ClickJob job = new ClickJob(id, name, description, task, 
    HtmlSelectorType.CSS, ".submit-button", true);
```

## Generated Code

The `primemover-generated` module uses OpenAPI Generator to create Java client code from the MultiLogin X API specification (`MultiloginX.yaml`). This comprehensive 3000+ line specification covers:

- **Profile Management**: Create, update, delete, clone browser profiles
- **Launcher Control**: Start/stop profiles with automation support (Selenium/Puppeteer/Playwright)
- **Cookie Management**: Import/export cookies, pre-made cookie integration
- **Proxy Integration**: Validate and manage proxy connections
- **2FA Support**: Complete two-factor authentication workflow
- **Object Storage**: Upload/download extensions, scripts, and configurations
- **Script Runner**: Execute Python automation scripts on profiles

The generated code ensures type-safe API interactions and automatically updates when the API specification changes.
