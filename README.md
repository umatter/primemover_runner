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

## Use Cases

PrimeMover is typically used for:

- **Market Research**: Automated data collection from e-commerce sites, competitor analysis
- **Lead Generation**: Extracting contact information from business directories
- **Content Monitoring**: Tracking changes on websites, price monitoring
- **Form Automation**: Bulk submission of applications or registrations
- **Quality Assurance**: Automated testing of web applications

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

[Add your license information here]
