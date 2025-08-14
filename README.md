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

