# PrimeMover

PrimeMover is an automated web interaction and data collection system designed to navigate websites, handle complex scenarios like CAPTCHAs, and extract information at scale.

## What Does PrimeMover Do?

Think of PrimeMover as a sophisticated "robot browser" that can:

- **Automate Web Tasks**: Navigate websites, click buttons, fill forms, and interact with web pages just like a human would
- **Handle Anti-Bot Measures**: Automatically detect and solve CAPTCHAs using third-party solving services
- **Manage Multiple Browser Profiles**: Use services like MultiLogin to create isolated browser sessions with different fingerprints
- **Collect and Store Data**: Extract information from websites and store it securely (including AWS S3 integration)
- **Scale Operations**: Run multiple automated tasks simultaneously with proper job scheduling and management

## System Architecture

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

### Core Components

#### **Job System Architecture**
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

#### **Context System**
```
IRunnerContext (basic execution environment)
└── IJobAwareRunnerContext (job-specific context)
    └── JobAwarnessContextDecorator (implementation with job awareness)
```

#### **State Management**
- **JobState**: `RUNNING`, `SUCCESS`, `FAILURE`, `WARNING`, `SKIPPED`
- **JoblistState**: 14 different states for comprehensive execution tracking
- **StartupMode**: `DEVELOP`, `INTEGRATION`, `PRODUCTION`

## Key Features

### 🤖 Browser Automation
- Uses Selenium WebDriver to control Chrome and Firefox browsers
- Supports headless operation for server environments
- Handles dynamic content and JavaScript-heavy websites
- Smart navigation with automatic page load detection
- Extension failure recovery with tab switching workarounds

### 🔐 Anti-Detection Capabilities
- Integrates with MultiLogin for browser fingerprint management
- Automatic CAPTCHA detection and solving via AntiCaptcha service
- Proxy support for IP rotation and geographic distribution
- Statistical behavior simulation for realistic user patterns
- Typing mode simulation (direct, simulated with/without typos)

### 📊 Job Management
- Configurable job scheduling and execution
- Support for different job types (data extraction, form submission, etc.)
- Retry mechanisms and failure handling with Spring Retry
- Real-time job status monitoring with 14 different execution states
- Failsafe job variants that don't stop execution on failure
- Sophisticated timeout handling (45-minute default with configuration)

### 🛡️ Security & Compliance
- Secure credential management
- Configurable rate limiting and delays
- Comprehensive logging and audit trails with correlation tracking
- AWS integration for secure data storage
- Thread-safe execution with MDC context preservation

### 🔧 Advanced Features
- **Smart Navigation**: Automatic detection of page load completion with extension failure recovery
- **Statistical Selection**: Multinomial selection for realistic user behavior simulation
- **Comprehensive Data Recording**: Screenshots, HAR files, cookies, local/session storage capture
- **Caching System**: Performance optimization with decorator pattern for data writers
- **HTTP Traffic Logging**: Complete request/response logging for debugging and analysis

## Architecture

PrimeMover is built as a Spring Boot application with two main modules that work together:

### **primemover-generated** - External Service Integration
This module automatically generates Java client libraries from API specifications:

- **Purpose**: Provides type-safe Java clients for external services (primarily MultiLogin)
- **How it works**: Uses OpenAPI Generator to convert the 3000+ line MultiLogin API specification into hundreds of Java classes
- **What it provides**: Complete client library for browser profile management, fingerprinting, and anti-detection features
- **Build process**: Runs during Maven compilation to generate fresh client code from API specs

**Key benefit**: Instead of manually writing HTTP client code for complex external APIs, this module auto-generates it, ensuring type safety and automatic updates when APIs change.

### **primemover-runner** - Core Application Logic
This is the main application that orchestrates web automation:

- **Purpose**: Contains all business logic, job execution, and web automation capabilities
- **Dependencies**: Uses the generated clients from `primemover-generated` to interact with external services
- **What it does**: Manages job scheduling, browser automation, CAPTCHA solving, data collection, and storage
- **Runtime**: This is the actual Spring Boot application that runs and executes automation tasks

**Key benefit**: Focuses purely on business logic without worrying about low-level API integration details, which are handled by the generated clients.

### **How They Work Together**

1. **Build time**: `primemover-generated` creates MultiLogin client libraries
2. **Runtime**: `primemover-runner` uses these clients to manage browser profiles and execute automation jobs
3. **Example flow**: Runner creates a MultiLogin profile → starts browser session → runs Selenium automation → collects data → stores results

This separation keeps external API complexity isolated from core business logic, making the system more maintainable and testable.

## Job Configuration and Execution

### Job Types and Capabilities

PrimeMover supports a comprehensive set of job types, each configurable via behavior parameters:

#### **Navigation Jobs**
- **VisitJob**: Navigate to URLs (automatically resets failure state)
- **SwitchToJob**: Switch between browser windows/frames

#### **Interaction Jobs**
- **ClickJob**: Click elements with optional page reload expectation
- **EnterTextFieldJob**: Fill form fields with configurable typing simulation
- **SingleSelectJob**: Select elements based on criteria (first, last, random, calculated)
- **HandleAlertJob**: Handle browser dialogs and alerts

#### **Utility Jobs**
- **WaitJob**: Pause execution for specified duration
- **ScrollByDurationJob**: Scroll pages for a specific time period
- **ScrollByLengthJob**: Scroll pages by pixel distance
- **LambdaSelectJob**: AWS Lambda-based element selection

#### **Failsafe Variants**
Most interaction jobs have "Try" variants that don't fail the entire joblist:
- `TryClickJob`, `TryEnterTextFieldJob`, `TrySwitchJob`, `TryHandleAlertJob`

### Job Configuration Parameters

Jobs are configured via behavior parameters that control:

- **Selector Types**: CSS, XPath, ID, Class, Text selectors for element targeting
- **Typing Modes**: 
  - `DIRECT`: Immediate text input
  - `SIMULATED_NOTYPOS`: Human-like typing without errors
  - `SIMULATED_KEEPINGTYPOS`: Realistic typing with intentional errors
  - `SIMULATED_FIXINGTYPOS`: Typing with errors that get corrected
- **Decision Types**: 
  - `FIRST`: Select first matching element
  - `LAST`: Select last matching element
  - `RANDOM`: Random selection from matches
  - `CALCULATED`: Selection based on extracted criteria
- **CAPTCHA Handling**: `NEVER`, `ALWAYS`, `BEFORE`, `AFTER`, `RANDOM`
- **Failsafe Behavior**: Continue vs. stop execution on job failure
- **Reset Behavior**: Whether to reset previous failure states

### API Conversion System

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



## Technology Stack

- **Java 17** with Spring Boot framework
- **Selenium WebDriver** for browser automation
- **Maven** for dependency management
- **AWS SDK** for cloud storage integration
- **Jackson** for JSON processing
- **Lombok** for code generation
- **Spring Retry** for resilient job execution
- **SLF4J with MDC** for correlation-aware logging
- **OpenAPI Generator** for type-safe API clients

## Job Execution Engine

### JobListRunner - Core Orchestration

The `JobListRunner` is the sophisticated execution engine that manages job lifecycle:

#### **Execution States**
- **Setup Phase**: `SUCCESS`, `FAILURE`, `SKIPPED`, `NO_JOBS`
- **Running Phase**: `NOTSTARTED`, `SUCCESS`, `FAILURE`
- **Comprehensive State Tracking**: 14 different `JoblistState` values

#### **Advanced Features**
- **Timeout Management**: Configurable timeout (45 minutes default) with automatic skipping
- **Retry Logic**: Spring `@Retryable` annotations for resilient API communication
- **Correlation Tracking**: MDC-based correlation IDs for request tracing
- **Resource Management**: Automatic cleanup and teardown on completion or failure

#### **State Management Flow**
```java
// Execution lifecycle with state transitions
RUNNING_INPROGRESS → RUNNING_INITDONE → RUNNING_JOBSDONE → DONE_SUCCESS
                  ↘ RUNNING_INITFAILED → DONE_INITFAILED
                  ↘ SKIPPED_TIMEDOUT (if timeout exceeded)
                  ↘ DONE_JOBLIST_EMPTY (if no jobs)
```

### Data Recording Pipeline

#### **Comprehensive Data Capture**
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

#### **Performance Optimization**
- **Caching Decorator**: `DataWriterCachingDecorator` prevents repeated data processing
- **Lazy Evaluation**: Data is only processed when actually needed
- **Memory Management**: Efficient byte array handling for large screenshots/HAR files

#### **Storage Flexibility**
- **Pluggable Writers**: `IDataWriter` interface for different storage backends
- **ZIP Compression**: `SingleZipEntryDataWriterBase` for efficient storage
- **File Naming**: Automatic timestamp and job-based file naming via `StorageUtility`

### Context System

#### **Execution Environment**
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

#### **Context Validation**
- **Validation**: `ContextBase.isValid()` ensures all required components are present
- **Type Safety**: Generic type parameters ensure WebDriver options compatibility
- **Resource Management**: Automatic cleanup and lifecycle management

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

## Development Setup

### Install Prerequisites

#### **Ubuntu/Debian Installation**
```bash
# Update package list
sudo apt update

# Install Java 17 (OpenJDK)
sudo apt install openjdk-17-jdk

# Install Maven
sudo apt install maven

# Install Git
sudo apt install git

# Install Chrome browser
wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | sudo apt-key add -
echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" | sudo tee /etc/apt/sources.list.d/google-chrome.list
sudo apt update
sudo apt install google-chrome-stable

# Install Firefox (usually pre-installed)
sudo apt install firefox
```

#### **Verify Installation**
```bash
# Check Java version (should be 17+)
java -version

# Check Maven version (should be 3.6+)
mvn -version

# Check Git version
git --version
```

#### **Browser Drivers** (Handled Automatically)
PrimeMover uses WebDriverManager to automatically download and manage browser drivers, so manual driver installation is not required.

### Local Development Environment

#### **1. Clone and Build**
```bash
# Clone the repository
git clone <repository-url>
cd primemover

# Build the entire project (generates API clients first)
mvn clean install
```

#### **2. Configure Development Properties**
Create a local configuration file for development:

```bash
# Copy the template configuration
cp primemover-runner/config/application.properties primemover-runner/config/application-local.properties
```

Edit `application-local.properties` with your development credentials:
```properties
# Minimal configuration for local development
spring.profiles.active=local

# Disable services you don't need for development
anticaptcha.enabled=false
multilogin.enabled=false

# Use local browser instead of MultiLogin
browser.type=chrome
browser.headless=false

# Local development endpoints (if available)
backend.endpoint.api=http://localhost:8080/api/v1/
backend.username=dev-user
backend.password=dev-password
```

#### **3. IDE Configuration**

**IntelliJ IDEA:**
1. Import as Maven project
2. Install Lombok plugin: `File → Settings → Plugins → Search "Lombok"`
3. Enable annotation processing: `File → Settings → Build → Compiler → Annotation Processors → Enable`
4. Set project SDK to Java 17+

**Eclipse:**
1. Import as existing Maven project
2. Install Lombok: Download `lombok.jar` and run `java -jar lombok.jar`
3. Refresh and clean the project

**Visual Studio Code:**
1. Install required extensions:
   - `Extension Pack for Java` (includes Maven support)
   - `Lombok Annotations Support for VS Code`
   - `Spring Boot Extension Pack`
2. Open the project folder in VS Code
3. Configure Java version: `Ctrl+Shift+P` → `Java: Configure Runtime` → Set to Java 17+
4. Enable annotation processing: Add to `.vscode/settings.json`:
   ```json
   {
     "java.compile.nullAnalysis.mode": "automatic",
     "java.configuration.updateBuildConfiguration": "automatic"
   }
   ```

#### **4. Running in Development Mode**

**From IDE:**
- Run the main class: `primemover.runner.PrimeMoverApplication`
- Set active profile: `-Dspring.profiles.active=local`
- Set working directory: `primemover-runner/`

**From Command Line:**
```bash
cd primemover-runner
mvn spring-boot:run -Dspring.profiles.active=local
```

### Development Workflow

#### **Code Generation**
The `primemover-generated` module automatically regenerates API clients during Maven compilation:

```bash
# Regenerate API clients after updating MultiloginX.yaml
cd primemover-generated
mvn clean compile

# Or rebuild the entire project
cd ..
mvn clean install
```

#### **Running Tests**
```bash
# Run all tests
mvn test

# Run tests for specific module
cd primemover-runner
mvn test

# Run specific test class
mvn test -Dtest=JobListRunnerTest
```

#### **Debugging Browser Automation**
For debugging browser automation issues:

```properties
# In your local configuration
browser.headless=false
browser.devtools=true
selenium.implicit.wait=10
selenium.page.load.timeout=30
```

#### **Local Testing Without External Services**
For development without external service dependencies:

```properties
# Disable external services
anticaptcha.enabled=false
multilogin.enabled=false
aws.s3.enabled=false

# Use mock implementations
proxy.enabled=false
captcha.solving=NEVER
```

### Common Development Tasks

#### **Adding New Job Types**
1. Create job class extending `BaseJob` or `SelectorBaseJob`
2. Add conversion logic in `PrimemoverApiConverter.convertApiJob()`
3. Add job handler in `runner/job/handler/` if needed
4. Write unit tests for the new job type

#### **Testing Browser Automation**
```bash
# Run with visible browser for debugging
mvn spring-boot:run -Dspring.profiles.active=local -Dbrowser.headless=false
```

#### **Working with Generated Code**
- Never edit files in `primemover-generated/target/` - they're auto-generated
- Modify `MultiloginX.yaml` to change the API specification
- Run `mvn compile` to regenerate after YAML changes

#### **Database and State Management**
PrimeMover uses in-memory state management by default. For persistent development:

```properties
# Enable file-based job state persistence
job.state.persistence=file
job.state.directory=./dev-data/jobs
```

### Troubleshooting Development Issues

#### **Build Failures**
```bash
# Clean and rebuild everything
mvn clean install -U

# Skip tests if they're failing
mvn clean install -DskipTests
```

#### **Browser Driver Issues**
```bash
# Clear WebDriverManager cache
rm -rf ~/.cache/selenium/
```

#### **Port Conflicts**
```properties
# Change default port in application-local.properties
server.port=8081
```

#### **Memory Issues**
```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512m"
```

### Development Best Practices

#### **Configuration Management**
- Use `application-local.properties` for local development
- Never commit real credentials to version control
- Use environment variables for sensitive configuration

#### **Testing Strategy**
- Write unit tests for business logic
- Use integration tests for browser automation
- Mock external services in tests

#### **Code Style**
- Follow existing code conventions
- Use Lombok annotations for boilerplate code
- Add JavaDoc for public APIs
- Keep methods focused and testable

#### **Git Workflow**
- Create feature branches for new development
- Keep commits focused and well-described
- Test locally before pushing
- Use meaningful commit messages

## Advanced Features

### Smart Navigation System

The `NavigationHelper` class provides intelligent page navigation with automatic failure recovery:

#### **Extension Failure Recovery**
```java
// Automatic workaround for MultiLogin extension loading issues
if (driver.getCurrentUrl().startsWith("chrome-extension://")) {
    logger.warn("Extension still loading. Trying workaround! Start running in new tab...");
    // Automatically opens new tab and switches context
    js.executeScript("window.open('" + target + "', '_blank');");
    driver.switchTo().window(sortedHandles.last());
}
```

#### **Page Load Detection**
- **Smart Waiting**: Detects when new pages have actually loaded vs. still loading
- **Timeout Handling**: Configurable timeouts with graceful degradation
- **Element Monitoring**: Tracks HTML element changes to detect navigation completion

### Statistical Behavior Simulation

The `StatisticsHelper` provides realistic user behavior simulation:

#### **Multinomial Selection**
```java
// Realistic selection based on probability distributions
double[] probabilities = {0.6, 0.3, 0.1}; // Most likely, somewhat likely, unlikely
int selectedIndex = StatisticsHelper.makeMultinominalSelection(probabilities);
```

This enables:
- **Realistic Click Patterns**: Simulate human-like element selection
- **Randomized Timing**: Vary interaction timing based on probability distributions
- **Behavioral Modeling**: Model real user behavior patterns for anti-detection

### Thread-Safe Execution

#### **MDC-Aware Threading**
The `MdcAwareThreadPoolExecutor` preserves logging context across thread boundaries:

```java
// Correlation IDs survive thread switches
MDC.put("correlationId", "cr-" + crawler.getId() + "|jl-" + joblist.getId());
// Execute job in thread pool - correlation ID is preserved
// Automatic cleanup prevents memory leaks
```

#### **Correlation Tracking**
- **Request Tracing**: Track requests across all system components
- **Debug Support**: Easily correlate logs across complex execution flows
- **Performance Monitoring**: Track execution times and resource usage per job/joblist

### HTTP Traffic Analysis

#### **Complete Traffic Capture**
- **HAR Files**: Complete HTTP Archive format capture
- **Request/Response Logging**: Detailed logging via `LoggingInterceptor`
- **Debugging Support**: Full visibility into browser-server communication

#### **API Communication Monitoring**
```java
// Automatic logging of all REST API calls
@Retryable
public Boolean updateJobState(IJob job, JobState state, ...) {
    logger.info("updating job-state. Retry {}", 
        RetrySynchronizationManager.getContext().getRetryCount());
    // Full request/response logging for debugging
}
```

## Developer Guide

### Adding New Job Types

1. **Create Job Class**: Extend `BaseJob` or `SelectorBaseJob`
```java
public class CustomJob extends SelectorBaseJob {
    private String customParameter;
    
    public CustomJob(Long id, String name, String description, String task,
                    HtmlSelectorType type, String selector, String customParameter) {
        super(id, name, description, task, type, selector);
        this.customParameter = customParameter;
    }
}
```

2. **Add API Conversion**: Update `PrimemoverApiConverter.convertApiJob()`
```java
case "customjob":
    res = convertApiCustomJob(job.getId(), job.getName(), 
                             job.getDescription(), captchaSolving, behaviors);
    break;
```

3. **Implement Conversion Logic**: Handle behavior parameter mapping
```java
private static CustomJob convertApiCustomJob(Long id, String name, String description,
        CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
    CustomJob job = new CustomJob();
    setBaseFields(job, id, name, description, captchaSolving, behaviours);
    job.setCustomParameter(behaviours.get("customParameter"));
    return job;
}
```

### Understanding Job Execution Flow

1. **API Request**: External system sends job definition
2. **Conversion**: `PrimemoverApiConverter` creates internal job objects
3. **Scheduling**: `JobListRunner` manages execution lifecycle
4. **Context Creation**: Job-aware context provides execution environment
5. **Execution**: Job-specific logic runs within context
6. **Data Recording**: Complete browser state captured
7. **State Updates**: Job and joblist states updated via REST API
8. **Cleanup**: Resources released and context cleaned up

### Extending Data Recording

1. **Custom Data Writer**: Implement `IDataWriter` interface
```java
public class CustomDataWriter implements IDataWriter {
    @Override
    public boolean writeData(OutputStream os) {
        // Custom data writing logic
    }
}
```

2. **Use Caching**: Wrap with `DataWriterCachingDecorator` for performance
```java
IDataWriter cachedWriter = new DataWriterCachingDecorator(customWriter);
```

3. **Integration**: Register in data writer factory or use directly in job handlers

### Debugging and Troubleshooting

#### **Common Issues and Solutions**

1. **Extension Loading Failures**: 
   - Handled automatically by `NavigationHelper`
   - Check logs for "Extension still loading" warnings
   - Verify MultiLogin profile configuration

2. **Job Timeout Scenarios**:
   - Default 45-minute timeout configurable via `runner.MaxRedoMinutes`
   - Check `JoblistState.SKIPPED_TIMEDOUT` in logs
   - Adjust timeout for long-running operations

3. **CAPTCHA Integration Issues**:
   - Verify AntiCaptcha API key configuration
   - Check `CaptchaSolvingType` settings on jobs
   - Monitor `CaptchaSolverResult` in recorded data

4. **State Management Problems**:
   - Enable debug logging for `JobListRunner`
   - Check correlation IDs in logs for request tracing
   - Verify REST API connectivity for state updates

#### **Logging and Monitoring**

- **Correlation IDs**: Every execution has unique correlation ID for tracing
- **State Transitions**: All job and joblist state changes are logged
- **Performance Metrics**: Execution times and resource usage tracked
- **Error Details**: Comprehensive error logging with stack traces and context

## Configuration

### Required Service Credentials

Before running PrimeMover, you must configure credentials for the external services it integrates with. The application loads configuration from:

**Primary Configuration File**: `primemover-runner/config/application.properties`

This file contains all the credentials and API keys needed for the various services. You'll need to obtain accounts and API keys for:

#### **Essential Services**
- **PrimeMover API Backend**: Main API for job management and configuration
  - API endpoint URLs
  - Username and password for authentication

#### **Browser Management Services** (choose one)
- **MultiLogin**: Advanced browser profile management with fingerprinting
  - API username and password
  - Profile folder ID for organizing browser profiles

#### **Proxy Services** (optional, choose based on needs)
- **GeoSurf**: Geographic proxy service
  - Username and password for proxy authentication
- **Oxylabs**: Enterprise proxy provider
- **Brightdata**: Large-scale proxy network

#### **Anti-Detection Services** (optional)
- **AntiCaptcha**: Automated CAPTCHA solving service
  - API key for CAPTCHA solving requests
  - Browser extension files for Chrome/Firefox

#### **Cloud Storage** (optional)
- **AWS S3**: For storing captured data and job results
  - Access key and secret key
  - S3 bucket names for different data types

### Configuration Setup

1. **Copy the template**: Use the provided `config/application.properties` as a starting point
2. **Obtain service accounts**: Sign up for the services you need
3. **Update credentials**: Replace placeholder values with your actual API keys and credentials
4. **Secure the file**: Never commit the configuration file with real credentials to version control

### Configuration Structure

The configuration file uses Spring Boot property syntax:

```properties
# Service endpoints
backend.endpoint.api=https://your-api-endpoint/api/v1/
backend.username=your-username
backend.password=your-password

# External service API keys
anticaptcha.api.key=your-anticaptcha-api-key
multilogin.apiUser=your-multilogin-username
multilogin.apiPassword=your-multilogin-password

# AWS credentials
awsS3.accessKey=your-aws-access-key
awsS3.secretKey=your-aws-secret-key

# Proxy configurations
geosurf.overwrite.user=your-geosurf-username
geosurf.overwrite.password=your-geosurf-password
```

### Environment-Specific Configuration

For different environments (development, staging, production), you can:

1. **Use Spring profiles**: Create `application-{profile}.properties` files
2. **Override with environment variables**: Spring Boot automatically maps environment variables to properties
3. **External configuration**: Place configuration files outside the application JAR




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



## Monitoring and Observability

### Built-in Monitoring Capabilities

#### **State Tracking**
- **Job States**: `RUNNING`, `SUCCESS`, `FAILURE`, `WARNING`, `SKIPPED`
- **Joblist States**: 14 comprehensive states from `RUNNING_INPROGRESS` to `DONE_SUCCESS`
- **Execution Metrics**: Start times, end times, duration tracking
- **Retry Tracking**: Automatic retry count logging with Spring Retry

#### **Correlation and Tracing**
```java
// Automatic correlation ID generation
MDC.put("crawler.id", crawler.getId().toString());
MDC.put("joblist.id", jobs.getId().toString());
MDC.put("correlationId", "cr-" + crawler.getId() + "|jl-" + jobs.getId());
```

#### **Performance Monitoring**
- **Execution Duration**: Complete timing for jobs and joblists
- **Resource Usage**: Memory and thread pool monitoring
- **API Response Times**: REST API call duration tracking
- **Browser Performance**: Page load times and navigation metrics

### Health Checks and Diagnostics

#### **System Health Indicators**
- **Context Validation**: `ContextBase.isValid()` ensures proper setup
- **Resource Availability**: WebDriver, proxy, and CAPTCHA solver status
- **External Service Health**: MultiLogin, AntiCaptcha, and backend API connectivity
- **Data Writer Status**: Storage system availability and performance

#### **Diagnostic Information**
- **Browser State**: Current URL, page source, cookies, storage
- **Proxy Status**: Connection health and geographic location
- **Extension Status**: CAPTCHA solver and other browser extension health
- **Job Configuration**: Complete job parameter validation and status

## Production Deployment Considerations

### Configuration Management

#### **Environment-Specific Settings**
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

#### **Security Configuration**
- **Credential Management**: External configuration for API keys and passwords
- **Network Security**: Proxy authentication and encrypted connections
- **Data Protection**: Secure storage of captured data and logs
- **Access Control**: Role-based access to job execution and monitoring

### Scaling and Performance

#### **Horizontal Scaling**
- **Multiple Instances**: Run multiple PrimeMover instances with shared job queue
- **Load Balancing**: Distribute jobs across available instances
- **Resource Isolation**: Separate browser profiles and proxy pools per instance

#### **Performance Optimization**
- **Connection Pooling**: Efficient HTTP client configuration
- **Caching Strategy**: Data writer caching and result memoization
- **Thread Pool Tuning**: Optimal thread pool sizes for job execution
- **Memory Management**: Efficient handling of screenshots and HAR data

### Monitoring in Production

#### **Key Metrics to Track**
- **Job Success Rate**: Percentage of successful job completions
- **Average Execution Time**: Performance trends over time
- **CAPTCHA Solve Rate**: Effectiveness of anti-CAPTCHA measures
- **Resource Utilization**: CPU, memory, and network usage
- **Error Rates**: Frequency and types of failures

#### **Alerting and Notifications**
- **Failed Jobs**: Immediate alerts for job failures
- **System Health**: Monitoring for service degradation
- **Resource Exhaustion**: Alerts for high resource usage
- **External Service Issues**: Monitoring for MultiLogin/AntiCaptcha problems

## Legal and Ethical Considerations

⚠️ **Important**: Always ensure your use of PrimeMover complies with:

- Website Terms of Service
- Local and international laws regarding data collection
- Respect for robots.txt files
- Rate limiting to avoid overwhelming target servers
- Data privacy regulations (GDPR, CCPA, etc.)

### Best Practices for Responsible Use

#### **Rate Limiting and Throttling**
- Configure appropriate delays between requests
- Respect server response times and implement backoff strategies
- Monitor target website performance impact
- Use distributed execution to reduce per-server load

#### **Data Handling**
- Implement data retention policies
- Secure storage and transmission of collected data
- Anonymization and pseudonymization where appropriate
- Clear audit trails for data access and processing

## Support

This is an enterprise automation tool designed for technical teams. Proper configuration and deployment require understanding of web technologies, browser automation, and the specific websites being targeted.

### Getting Help

#### **Documentation Resources**
- **Code Documentation**: Comprehensive JavaDoc in source code
- **Configuration Examples**: Sample configurations for different environments
- **Troubleshooting Guides**: Common issues and resolution steps

#### **Development Support**
- **Architecture Overview**: Understanding the system design and components
- **Extension Guidelines**: How to add new job types and features
- **Testing Strategies**: Unit and integration testing approaches
- **Performance Tuning**: Optimization techniques for production deployment

## License

