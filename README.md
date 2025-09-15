# PrimeMover

PrimeMover is an automated web interaction and data collection system designed to navigate websites, handle complex scenarios like CAPTCHAs, and extract information at scale.

## What Does PrimeMover Do?

Think of PrimeMover as a sophisticated "robot browser" that can:

- **Automate Web Tasks**: Navigate websites, click buttons, fill forms, and interact with web pages just like a human would
- **Handle Anti-Bot Measures**: Automatically detect and solve CAPTCHAs using third-party solving services
- **Manage Multiple Browser Profiles**: Use services like MultiLogin to create isolated browser sessions with different fingerprints
- **Collect and Store Data**: Extract information from websites and store it securely (including AWS S3 integration)
- **Scale Operations**: Run multiple automated tasks simultaneously with proper job scheduling and management

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Chrome or Firefox browser installed
- Multilogin X

### Building and Running
```bash
# Clone and build the project
git clone <repository-url>
cd primemover
mvn clean install

# Run the application
cd primemover-runner
mvn spring-boot:run
```
#### Run the local version for development

`mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"`

## Documentation

For detailed information, see our comprehensive documentation:

- **[Getting Started Guide](doc/getting-started.md)** - Complete setup for new developers
- **[Architecture Overview](doc/architecture.md)** - System design and components
- **[Job System Guide](doc/job-system.md)** - Understanding jobs and execution
- **[Configuration Guide](doc/configuration.md)** - Service setup and credentials
- **[Development Guide](doc/development.md)** - Development workflow and best practices
- **[Repository Structure](doc/repository-structure.md)** - Codebase organization
- **[Advanced Features](doc/advanced-features.md)** - Smart navigation, anti-detection, monitoring
- **[Production Deployment](doc/production.md)** - Scaling and production considerations

## Key Features

### 🤖 Browser Automation
- Uses Selenium WebDriver to control Chrome and Firefox browsers
- Supports headless operation for server environments
- Smart navigation with automatic page load detection
- Extension failure recovery with tab switching workarounds

### 🔐 Anti-Detection Capabilities
- Integrates with MultiLogin for browser fingerprint management
- Automatic CAPTCHA detection and solving via AntiCaptcha service
- Proxy support for IP rotation and geographic distribution
- Statistical behavior simulation for realistic user patterns

### 📊 Job Management
- Configurable job scheduling and execution
- Support for different job types (data extraction, form submission, etc.)
- Retry mechanisms and failure handling with Spring Retry
- Real-time job status monitoring with 14 different execution states
- Failsafe job variants that don't stop execution on failure

### 🛡️ Security & Compliance
- Secure credential management
- Configurable rate limiting and delays
- Comprehensive logging and audit trails with correlation tracking
- AWS integration for secure data storage
- Thread-safe execution with MDC context preservation

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

## Legal and Ethical Considerations

⚠️ **Important**: Always ensure your use of PrimeMover complies with:

- Website Terms of Service
- Local and international laws regarding data collection
- Respect for robots.txt files
- Rate limiting to avoid overwhelming target servers
- Data privacy regulations (GDPR, CCPA, etc.)

## Support

This is an enterprise automation tool designed for technical teams. Proper configuration and deployment require understanding of web technologies, browser automation, and the specific websites being targeted.

For detailed documentation and guides, see the `doc/` directory.

## License

[Add your license information here]

