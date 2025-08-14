# PrimeMover

PrimeMover is an automated web interaction and data collection system designed to navigate websites, handle complex scenarios like CAPTCHAs, and extract information at scale.

## What Does PrimeMover Do?

Think of PrimeMover as a sophisticated "robot browser" that can:

- **Automate Web Tasks**: Navigate websites, click buttons, fill forms, and interact with web pages just like a human would
- **Handle Anti-Bot Measures**: Automatically detect and solve CAPTCHAs using third-party solving services
- **Manage Multiple Browser Profiles**: Use services like MultiLogin to create isolated browser sessions with different fingerprints
- **Collect and Store Data**: Extract information from websites and store it securely (including AWS S3 integration)
- **Scale Operations**: Run multiple automated tasks simultaneously with proper job scheduling and management

## Key Features

### 🤖 Browser Automation
- Uses Selenium WebDriver to control Chrome and Firefox browsers
- Supports headless operation for server environments
- Handles dynamic content and JavaScript-heavy websites

### 🔐 Anti-Detection Capabilities
- Integrates with MultiLogin for browser fingerprint management
- Automatic CAPTCHA detection and solving via AntiCaptcha service
- Proxy support for IP rotation and geographic distribution

### 📊 Job Management
- Configurable job scheduling and execution
- Support for different job types (data extraction, form submission, etc.)
- Retry mechanisms and failure handling
- Real-time job status monitoring

### 🛡️ Security & Compliance
- Secure credential management
- Configurable rate limiting and delays
- Comprehensive logging and audit trails
- AWS integration for secure data storage

## Architecture

PrimeMover is built as a Spring Boot application with two main modules:

- **primemover-generated**: Auto-generated API clients (e.g., for MultiLogin integration)
- **primemover-runner**: Core application logic, job execution, and web automation



## Technology Stack

- **Java 17** with Spring Boot framework
- **Selenium WebDriver** for browser automation
- **Maven** for dependency management
- **AWS SDK** for cloud storage integration
- **Jackson** for JSON processing
- **Lombok** for code generation

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Chrome or Firefox browser installed
- (Optional) MultiLogin account for advanced browser management
- (Optional) AntiCaptcha account for CAPTCHA solving

### Building the Project

```bash
mvn clean install
```

### Running the Application

```bash
cd primemover-runner
mvn spring-boot:run
```

## Configuration

The application can be configured through standard Spring Boot configuration files. Key configuration areas include:

- Browser settings (Chrome/Firefox paths, options)
- Proxy configurations
- CAPTCHA solving service credentials
- AWS credentials for data storage
- Job scheduling parameters


## Repository Structure

```
primemover/
├── pom.xml                           # Parent Maven POM with shared dependencies
├── README.md                         # This file
├── .gitignore                        # Git ignore patterns
│
├── primemover-generated/             # Auto-generated API clients module
│   ├── pom.xml                       # Module POM with OpenAPI generation
│   └── src/main/resources/
│       └── MultiloginX.yaml          # Complete MultiLogin X API specification (3000+ lines)
│
└── primemover-runner/                # Main application module
    ├── pom.xml                       # Module POM with all dependencies
    └── src/main/java/primemover/
        ├── anticaptcha/              # CAPTCHA detection and solving system
        │   ├── CaptchaSolverResult.java      # Result data from CAPTCHA solving attempts
        │   ├── CaptchaSolvingType.java       # When to solve CAPTCHAs (NEVER/ALWAYS/BEFORE/AFTER/RANDOM)
        │   ├── EmptyPageController.java      # REST endpoint for CAPTCHA plugin initialization
        │   ├── FeedbackProxy.java            # Auto-closing proxy wrapper with port management
        │   ├── FeedbackProxyFactory.java     # Factory for creating feedback proxies with port pooling
        │   └── PluginCaptchaSolver.java      # Main CAPTCHA solving implementation using browser extensions
        │
        ├── api/                      # External API integrations and data models
        │   ├── converter/
        │   │   └── PrimemoverApiConverter.java # Converts between API DTOs and internal models
        │   └── model/                # API data transfer objects
        │       ├── Agent.java                # Browser agent configuration from API
        │       ├── Behaviour.java            # Job behavior parameters
        │       ├── Configuration.java        # System configuration from API
        │       ├── Crawler.java              # Crawler definition from API
        │       ├── CrawlerResult.java        # API response containing crawlers
        │       ├── IpLanguage.java           # Language information from IP geolocation
        │       ├── IpResultData.java         # IP geolocation response data
        │       ├── Job.java                  # Job definition from API
        │       ├── MediaOutlet.java          # Media outlet information
        │       ├── MediaOutletUrls.java      # Dynamic URL mapping for media outlets
        │       ├── OutletResult.java         # API response containing outlets
        │       ├── Proxy.java                # Proxy configuration from API
        │       ├── SearchTerm.java           # Search term with probability
        │       ├── TermResult.java           # API response containing terms
        │       └── TokenResult.java          # Authentication token response
        │
        ├── capture/                  # HTTP traffic capture and recording
        │   ├── BasicAuthorization.java      # HTTP basic auth credentials
        │   └── RecordingProxy.java          # Man-in-the-middle proxy for traffic recording
        │
        ├── model/                    # Core domain models and business logic
        │   ├── Configuration.java           # Internal system configuration
        │   ├── IAgent.java                  # Agent interface for browser automation
        │   ├── IJob.java                    # Job interface with execution metadata
        │   ├── ISelectorType.java           # HTML selector type interface
        │   ├── JobState.java                # Job execution states (RUNNING/SUCCESS/FAILURE/SKIPPED)
        │   ├── Joblist.java                 # Collection of jobs with execution metadata
        │   ├── JoblistState.java            # Joblist execution states with detailed status tracking
        │   ├── LocationInfo.java            # Geographic location from IP lookup
        │   ├── MediaOutlet.java             # Media outlet with domain and PI score
        │   ├── MultiLoginAgent.java         # MultiLogin-specific agent with profile JSON
        │   ├── MultiLoginIBrowserType.java  # Browser types (MIMIC/STEALTHFOX/MIMIC_MOBILE)
        │   ├── MultiLoginIOsType.java       # OS types for MultiLogin (WINDOWS/MAC/LINUX/ANDROID)
        │   ├── jobs/                        # Job type implementations
        │   │   ├── Attribute.java           # HTML attributes to extract (TEXT/HREF)
        │   │   ├── BaseJob.java             # Base class with common job functionality
        │   │   ├── ExtendedSingleSelectJob.java # Extended single element selection
        │   │   ├── LambdaSelectJob.java     # AWS Lambda-based element selection
        │   │   └── SelectorBaseJob.java     # Base for HTML selector-based jobs
        │   └── proxy/                       # Proxy management and configuration
        │       ├── HttpProxy.java           # HTTP proxy implementation with HTTPS support
        │       ├── IProxy.java              # Proxy interface
        │       └── Utils.java               # Proxy utility constants (session/password placeholders)
        │
        ├── multilogin/               # MultiLogin service integrations
        │   ├── ml6/model/
        │   │   └── Extensions.java          # Browser extension configuration with CSV parsing
        │   └── mlx/
        │       └── StartProfileResult.java  # Result from starting a MultiLogin profile
        │
        ├── octobrowser/              # OctoBrowser integration (future expansion)
        │   └── OctologinService.java        # Placeholder service for OctoBrowser
        │
        ├── runner/                   # Job execution engine and workflow management
        │   ├── CaptchaSolverResultHolder.java # Holds before/after CAPTCHA solving results
        │   ├── ITaskManager.java            # Task management interface
        │   ├── RecordedJobData.java         # Complete data captured during job execution
        │   ├── context/                     # Execution context management
        │   │   ├── ContextBase.java         # Base execution context with WebDriver/proxy
        │   │   ├── IJobAwareRunnerContext.java # Context interface with job awareness
        │   │   ├── IRunnerContext.java      # Basic runner context interface
        │   │   └── JobAwarnessContextDecorator.java # Decorator adding job context
        │   ├── job/                         # Job execution handlers
        │   │   ├── IJobHandler.java         # Interface for job-specific handlers
        │   │   └── handler/
        │   │       ├── LambdaSelectJobParam.java  # Parameters for Lambda job execution
        │   │       └── LambdaSelectJobResult.java # Result from Lambda job execution
        │   ├── joblist/
        │   │   └── JobListRunner.java       # Executes collections of jobs with state management
        │   └── specific/                    # Specific job configurations and overrides
        │       ├── Specific.java            # Specific job configuration for selectors
        │       ├── SpecificJoblistRepository.java # Repository for specific configurations
        │       └── SpecificJoblistService.java    # Service for specific job handling
        │
        ├── service/                  # Core business services
        │   ├── IJobService.java             # Job state management and persistence service
        │   └── datawriter/                  # Data persistence framework
        │       ├── DataWriterCachingDecorator.java # Caching decorator for data writers
        │       ├── IDataWriter.java         # Interface for writing captured data
        │       ├── IRecordedData.java       # Interface for recorded job data
        │       ├── RecordedJobDataBaseWriter.java # Base class for job data writers
        │       ├── SingleZipEntryDataWriterBase.java # Base for single-file ZIP writers
        │       └── StorageUtility.java      # File naming and storage utilities
        │
        └── util/                     # Utility classes and helpers
            ├── LoggingInterceptor.java      # HTTP request/response logging interceptor
            ├── NavigationHelper.java        # Smart web page navigation with load detection
            ├── StatisticsHelper.java        # Multinomial selection for randomized behavior
            └── threading/
                └── MdcAwareThreadPoolExecutor.java # Thread pool preserving logging context
```

### Key Components Explained

#### **Core Execution Flow**
1. **Jobs** (`model/jobs/`): Individual atomic tasks (click buttons, fill forms, extract data)
2. **Joblists** (`model/Joblist.java`): Ordered sequences of jobs with timing and state tracking
3. **JobListRunner** (`runner/joblist/`): Executes joblists with retry logic and state persistence
4. **Context System** (`runner/context/`): Provides execution environment (WebDriver, proxies, CAPTCHA solvers)

#### **Job Types Supported**
- **VisitJob**: Navigate to URLs
- **ClickJob/TryClickJob**: Click elements (with failsafe variants)
- **EnterTextFieldJob**: Fill form fields with typing simulation
- **SingleSelectJob**: Select elements based on criteria
- **LambdaSelectJob**: AWS Lambda-based element selection
- **WaitJob**: Pause execution for specified duration
- **ScrollJob**: Scroll pages by duration or length
- **HandleAlertJob**: Handle browser dialogs/alerts
- **SwitchToJob**: Switch between browser windows/frames

#### **Browser Management**
- **MultiLogin Integration**: Complete API client for profile management and browser launching
- **Agent System**: Abstracts Chrome, Firefox, and MultiLogin browser providers
- **Extension Support**: Automatic CAPTCHA solving extensions for Chrome/Firefox
- **Proxy Support**: HTTP/HTTPS proxies with authentication and session management

#### **Anti-Detection Features**
- **CAPTCHA Solving**: Browser extension-based solving via AntiCaptcha service
- **Browser Fingerprinting**: MultiLogin profiles with unique fingerprints
- **Traffic Recording**: Complete HTTP traffic capture via man-in-the-middle proxy
- **Smart Navigation**: Detects page loads and handles extension failures

#### **Data Management**
- **Recording System**: Captures screenshots, page source, cookies, local/session storage
- **Data Writers**: Pluggable system for storing data (ZIP files, cloud storage)
- **Caching**: Efficient data caching to avoid repeated processing
- **State Persistence**: Comprehensive job and joblist state tracking

#### **API Integration**
- **Converter System**: Transforms external API models to internal domain models
- **IP Geolocation**: Location detection and validation
- **Media Outlet Management**: Handles media outlet configurations and URLs
- **Authentication**: Token-based authentication with refresh capabilities

### Generated Code

The `primemover-generated` module uses OpenAPI Generator to create Java client code from the MultiLogin X API specification (`MultiloginX.yaml`). This comprehensive 3000+ line specification covers:

- **Profile Management**: Create, update, delete, clone browser profiles
- **Launcher Control**: Start/stop profiles with automation support (Selenium/Puppeteer/Playwright)
- **Cookie Management**: Import/export cookies, pre-made cookie integration
- **Proxy Integration**: Validate and manage proxy connections
- **2FA Support**: Complete two-factor authentication workflow
- **Object Storage**: Upload/download extensions, scripts, and configurations
- **Script Runner**: Execute Python automation scripts on profiles

The generated code ensures type-safe API interactions and automatically updates when the API specification changes.

### Developer Guide

#### **Understanding the Job System**

Jobs are the fundamental unit of work in PrimeMover. Each job represents a single action that can be performed on a web page:

- **BaseJob**: Abstract base class providing common functionality (ID, name, description, CAPTCHA handling)
- **SelectorBaseJob**: Base for jobs that interact with HTML elements using CSS/XPath selectors
- **Job Execution States**: Tracked via `JobState` enum (RUNNING, SUCCESS, FAILURE, WARNING, SKIPPED)

#### **Working with Joblists**

Joblists group related jobs into executable sequences:

- **Execution Order**: Jobs run sequentially in the order defined
- **State Management**: Each joblist tracks its overall execution state via `JoblistState`
- **Timing Control**: Built-in timeout handling and retry mechanisms
- **Failure Handling**: Individual job failures can be configured to stop or continue execution

#### **Context and Dependency Injection**

The context system provides a clean way to pass execution environment to jobs:

- **IRunnerContext**: Basic context with WebDriver, proxy, and CAPTCHA solver
- **IJobAwareRunnerContext**: Extended context that includes current job information
- **ContextBase**: Concrete implementation with validation and lifecycle management

#### **Data Recording and Persistence**

PrimeMover captures comprehensive data during job execution:

- **RecordedJobData**: Container for all captured data (screenshots, page source, cookies, etc.)
- **IDataWriter**: Interface for pluggable data persistence strategies
- **Caching**: Automatic caching of data to improve performance and reliability

#### **Proxy and Anti-Detection**

The proxy system supports multiple providers and anti-detection techniques:

- **HttpProxy**: Basic HTTP/HTTPS proxy support
- **Specialized Proxies**: GeoSurf, Oxylabs, Brightdata with location-specific features
- **Session Management**: Automatic session handling and rotation
- **Traffic Recording**: Complete HTTP traffic capture for analysis

#### **MultiLogin Integration**

Deep integration with MultiLogin for advanced browser management:

- **Profile Management**: Create, update, and manage browser profiles
- **Fingerprint Control**: Customize browser fingerprints for anti-detection
- **Extension Management**: Automatic installation and configuration of browser extensions
- **API Client**: Auto-generated from OpenAPI specification for type safety

#### **Configuration and Deployment**

Key configuration areas for production deployment:

- **Spring Profiles**: Use different configurations for dev/staging/production
- **Proxy Settings**: Configure proxy providers and rotation strategies
- **CAPTCHA Services**: Set up AntiCaptcha API keys and solving preferences
- **AWS Integration**: Configure S3 buckets and Lambda functions for data processing
- **Rate Limiting**: Configure delays and throttling to respect target websites

#### **Monitoring and Observability**

Built-in monitoring and logging capabilities:

- **MDC Logging**: Correlation IDs for tracking requests across components
- **State Tracking**: Comprehensive state management for jobs and joblists
- **HTTP Logging**: Request/response logging for debugging and analysis
- **Spring Actuator**: Health checks and metrics endpoints

#### **Testing and Development**

Development best practices and testing support:

- **JUnit 5**: Modern testing framework with parameterized test support
- **Mockito**: Mocking framework for unit tests
- **WebDriverManager**: Automatic browser driver management
- **Test Profiles**: Separate configurations for testing environments

### Common Development Patterns

#### **Adding New Job Types**

1. Extend `BaseJob` or `SelectorBaseJob`
2. Implement job-specific logic in the constructor and methods
3. Add conversion logic in `PrimemoverApiConverter`
4. Register the job type in the converter's switch statement

#### **Implementing Custom Data Writers**

1. Implement `IDataWriter` interface
2. Extend `RecordedJobDataBaseWriter` for common functionality
3. Use `DataWriterCachingDecorator` for performance optimization
4. Register in the data writer factory

#### **Adding Proxy Providers**

1. Extend `HttpProxy` class
2. Implement provider-specific authentication and session handling
3. Add conversion logic in `PrimemoverApiConverter.convertApiProxy()`
4. Configure provider-specific settings in application properties

#### **Extending Browser Support**

1. Create new agent class implementing `IAgent`
2. Add browser-specific capabilities and options handling
3. Implement CAPTCHA solver integration if needed
4. Update the agent conversion logic in the API converter



## Legal and Ethical Considerations

⚠️ **Important**: Always ensure your use of PrimeMover complies with:

- Website Terms of Service
- Local and international laws regarding data collection
- Respect for robots.txt files
- Rate limiting to avoid overwhelming target servers
- Data privacy regulations (GDPR, CCPA, etc.)



## Support

This is an enterprise automation tool designed for technical teams. Proper configuration and deployment require understanding of web technologies, browser automation, and the specific websites being targeted.

## License

