# Repository Structure

This document provides a detailed overview of the PrimeMover codebase organization and key components.

## Top-Level Structure

```
primemover/
├── pom.xml                           # Parent Maven POM with shared dependencies
├── README.md                         # Project overview and quick start
├── doc/                              # Documentation files
├── .gitignore                        # Git ignore patterns
│
├── primemover-generated/             # Auto-generated API clients module
│   ├── pom.xml                       # Module POM with OpenAPI generation
│   └── src/main/resources/
│       └── MultiloginX.yaml          # Complete MultiLogin X API specification (3000+ lines)
│
└── primemover-runner/                # Main application module
    ├── pom.xml                       # Module POM with all dependencies
    ├── config/                       # Configuration files
    └── src/main/java/primemover/     # Main source code
```

## primemover-runner Module Structure

### Core Packages

#### anticaptcha/ - CAPTCHA Detection and Solving
```
anticaptcha/
├── CaptchaSolverResult.java          # Result data from CAPTCHA solving attempts
├── CaptchaSolvingType.java           # When to solve CAPTCHAs (NEVER/ALWAYS/BEFORE/AFTER/RANDOM)
├── EmptyPageController.java          # REST endpoint for CAPTCHA plugin initialization
├── FeedbackProxy.java                # Auto-closing proxy wrapper with port management
├── FeedbackProxyFactory.java         # Factory for creating feedback proxies with port pooling
└── PluginCaptchaSolver.java          # Main CAPTCHA solving implementation using browser extensions
```

#### api/ - External API Integrations
```
api/
├── converter/
│   └── PrimemoverApiConverter.java   # Converts between API DTOs and internal models
└── model/                            # API data transfer objects
    ├── Agent.java                    # Browser agent configuration from API
    ├── Behaviour.java                # Job behavior parameters
    ├── Configuration.java            # System configuration from API
    ├── Crawler.java                  # Crawler definition from API
    ├── CrawlerResult.java            # API response containing crawlers
    ├── IpLanguage.java               # Language information from IP geolocation
    ├── IpResultData.java             # IP geolocation response data
    ├── Job.java                      # Job definition from API
    ├── MediaOutlet.java              # Media outlet information
    ├── MediaOutletUrls.java          # Dynamic URL mapping for media outlets
    ├── OutletResult.java             # API response containing outlets
    ├── Proxy.java                    # Proxy configuration from API
    ├── SearchTerm.java               # Search term with probability
    ├── TermResult.java               # API response containing terms
    └── TokenResult.java              # Authentication token response
```

#### model/ - Core Domain Models
```
model/
├── Configuration.java                # Internal system configuration
├── IAgent.java                       # Agent interface for browser automation
├── IJob.java                         # Job interface with execution metadata
├── ISelectorType.java                # HTML selector type interface
├── JobState.java                     # Job execution states (RUNNING/SUCCESS/FAILURE/SKIPPED)
├── Joblist.java                      # Collection of jobs with execution metadata
├── JoblistState.java                 # Joblist execution states with detailed status tracking
├── LocationInfo.java                 # Geographic location from IP lookup
├── MediaOutlet.java                  # Media outlet with domain and PI score
├── MultiLoginAgent.java              # MultiLogin-specific agent with profile JSON
├── MultiLoginIBrowserType.java       # Browser types (MIMIC/STEALTHFOX/MIMIC_MOBILE)
├── MultiLoginIOsType.java            # OS types for MultiLogin (WINDOWS/MAC/LINUX/ANDROID)
├── jobs/                             # Job type implementations
│   ├── Attribute.java                # HTML attributes to extract (TEXT/HREF)
│   ├── BaseJob.java                  # Base class with common job functionality
│   ├── ExtendedSingleSelectJob.java  # Extended single element selection
│   ├── LambdaSelectJob.java          # AWS Lambda-based element selection
│   └── SelectorBaseJob.java          # Base for HTML selector-based jobs
└── proxy/                            # Proxy management and configuration
    ├── HttpProxy.java                # HTTP proxy implementation with HTTPS support
    ├── IProxy.java                   # Proxy interface
    └── Utils.java                    # Proxy utility constants (session/password placeholders)
```

#### runner/ - Job Execution Engine
```
runner/
├── CaptchaSolverResultHolder.java    # Holds before/after CAPTCHA solving results
├── ITaskManager.java                 # Task management interface
├── RecordedJobData.java              # Complete data captured during job execution
├── context/                          # Execution context management
│   ├── ContextBase.java              # Base execution context with WebDriver/proxy
│   ├── IJobAwareRunnerContext.java   # Context interface with job awareness
│   ├── IRunnerContext.java           # Basic runner context interface
│   └── JobAwarnessContextDecorator.java # Decorator adding job context
├── job/                              # Job execution handlers
│   ├── IJobHandler.java              # Interface for job-specific handlers
│   └── handler/
│       ├── LambdaSelectJobParam.java # Parameters for Lambda job execution
│       └── LambdaSelectJobResult.java # Result from Lambda job execution
├── joblist/
│   └── JobListRunner.java            # Executes collections of jobs with state management
└── specific/                         # Specific job configurations and overrides
    ├── Specific.java                 # Specific job configuration for selectors
    ├── SpecificJoblistRepository.java # Repository for specific configurations
    └── SpecificJoblistService.java   # Service for specific job handling
```

#### service/ - Core Business Services
```
service/
├── IJobService.java                  # Job state management and persistence service
└── datawriter/                       # Data persistence framework
    ├── DataWriterCachingDecorator.java # Caching decorator for data writers
    ├── IDataWriter.java              # Interface for writing captured data
    ├── IRecordedData.java            # Interface for recorded job data
    ├── RecordedJobDataBaseWriter.java # Base class for job data writers
    ├── SingleZipEntryDataWriterBase.java # Base for single-file ZIP writers
    └── StorageUtility.java           # File naming and storage utilities
```

#### util/ - Utility Classes
```
util/
├── LoggingInterceptor.java           # HTTP request/response logging interceptor
├── NavigationHelper.java            # Smart web page navigation with load detection
├── StatisticsHelper.java            # Multinomial selection for randomized behavior
└── threading/
    └── MdcAwareThreadPoolExecutor.java # Thread pool preserving logging context
```

### Supporting Packages

#### capture/ - HTTP Traffic Capture
```
capture/
├── BasicAuthorization.java          # HTTP basic auth credentials
└── RecordingProxy.java               # Man-in-the-middle proxy for traffic recording
```

#### multilogin/ - MultiLogin Service Integration
```
multilogin/
├── ml6/model/
│   └── Extensions.java               # Browser extension configuration with CSV parsing
└── mlx/
    └── StartProfileResult.java       # Result from starting a MultiLogin profile
```

#### octobrowser/ - OctoBrowser Integration
```
octobrowser/
└── OctologinService.java             # Placeholder service for OctoBrowser (future expansion)
```

## Key Components Explained

### Core Execution Flow

1. **Jobs** (`model/jobs/`): Individual atomic tasks (click buttons, fill forms, extract data)
2. **Joblists** (`model/Joblist.java`): Ordered sequences of jobs with timing and state tracking
3. **JobListRunner** (`runner/joblist/`): Executes joblists with retry logic and state persistence
4. **Context System** (`runner/context/`): Provides execution environment (WebDriver, proxies, CAPTCHA solvers)

### Job Types Supported

- **VisitJob**: Navigate to URLs
- **ClickJob/TryClickJob**: Click elements (with failsafe variants)
- **EnterTextFieldJob**: Fill form fields with typing simulation
- **SingleSelectJob**: Select elements based on criteria
- **LambdaSelectJob**: AWS Lambda-based element selection
- **WaitJob**: Pause execution for specified duration
- **ScrollJob**: Scroll pages by duration or length
- **HandleAlertJob**: Handle browser dialogs/alerts
- **SwitchToJob**: Switch between browser windows/frames

### Browser Management

- **MultiLogin Integration**: Complete API client for profile management and browser launching
- **Agent System**: Abstracts Chrome, Firefox, and MultiLogin browser providers
- **Extension Support**: Automatic CAPTCHA solving extensions for Chrome/Firefox
- **Proxy Support**: HTTP/HTTPS proxies with authentication and session management

### Anti-Detection Features

- **CAPTCHA Solving**: Browser extension-based solving via AntiCaptcha service
- **Browser Fingerprinting**: MultiLogin profiles with unique fingerprints
- **Traffic Recording**: Complete HTTP traffic capture via man-in-the-middle proxy
- **Smart Navigation**: Detects page loads and handles extension failures

### Data Management

- **Recording System**: Captures screenshots, page source, cookies, local/session storage
- **Data Writers**: Pluggable system for storing data (ZIP files, cloud storage)
- **Caching**: Efficient data caching to avoid repeated processing
- **State Persistence**: Comprehensive job and joblist state tracking

### API Integration

- **Converter System**: Transforms external API models to internal domain models
- **IP Geolocation**: Location detection and validation
- **Media Outlet Management**: Handles media outlet configurations and URLs
- **Authentication**: Token-based authentication with refresh capabilities

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

## Navigation Guide

### For New Developers

Start with these key files to understand the system:

1. **`model/jobs/BaseJob.java`** - Understand the job system foundation
2. **`api/converter/PrimemoverApiConverter.java`** - See how API requests become jobs
3. **`runner/joblist/JobListRunner.java`** - Understand job execution orchestration
4. **`runner/context/ContextBase.java`** - See how execution environment is managed

### For Job Development

Focus on these areas:

1. **`model/jobs/`** - All job type implementations
2. **`api/converter/PrimemoverApiConverter.java`** - Job conversion logic
3. **`runner/job/handler/`** - Custom job execution handlers
4. **Test files** - Examples of job testing patterns

### For Integration Work

Key integration points:

1. **`api/model/`** - External API data models
2. **`multilogin/`** - Browser profile management
3. **`anticaptcha/`** - CAPTCHA solving integration
4. **`capture/`** - HTTP traffic recording
5. **`service/datawriter/`** - Data persistence

### For System Administration

Configuration and monitoring:

1. **`config/application.properties`** - Main configuration
2. **`util/LoggingInterceptor.java`** - HTTP logging
3. **`runner/joblist/JobListRunner.java`** - Execution monitoring
4. **`service/`** - Core business services

## Common Development Patterns

### Adding New Job Types

1. Extend `BaseJob` or `SelectorBaseJob` in `model/jobs/`
2. Add conversion logic in `PrimemoverApiConverter`
3. Register the job type in the converter's switch statement
4. Add tests following existing patterns

### Implementing Custom Data Writers

1. Implement `IDataWriter` interface in `service/datawriter/`
2. Extend `RecordedJobDataBaseWriter` for common functionality
3. Use `DataWriterCachingDecorator` for performance optimization
4. Register in the data writer factory

### Adding Proxy Providers

1. Extend `HttpProxy` class in `model/proxy/`
2. Implement provider-specific authentication and session handling
3. Add conversion logic in `PrimemoverApiConverter.convertApiProxy()`
4. Configure provider-specific settings in application properties

### Extending Browser Support

1. Create new agent class implementing `IAgent` in `model/`
2. Add browser-specific capabilities and options handling
3. Implement CAPTCHA solver integration if needed
4. Update the agent conversion logic in the API converter
