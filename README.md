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
- Java 25 (production runtime; CI also builds against 17)
- Maven 3.6+
- Chrome or Firefox browser installed
- Multilogin X (production; not needed with the `chrome-dev` profile below)

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

#### Run without a Multilogin account: the `chrome-dev` profile

`config/application-chrome-dev.properties` boots the runner on a machine that has
no Multilogin account, no residential proxy and no S3 credentials, and drives
bots through a plain local Chrome:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,chrome-dev
```

It sets `multilogin.enabled=false` (no MLX beans and no cloud sign-in — a crawler
whose agent is `MULTILOGIN` fails *its own run* with a clear message instead of
the process dying), `runner.chromeHeadless=true`, `recorder.enabled=false`
(no MITM CA cert needed) and `storage.local.enabled=true` writing to `out/`.

The one secret still required is `backend.password` — the runner has to log in to
the API to be given any work. Every other key in
`config/secrets.properties.template` is annotated with whether `chrome-dev` needs
it. Use crawlers with agent type `CHROME`.

`multilogin.enabled` defaults to `true`, so nothing above affects production.

### ⚠️ Important: API Endpoint Configuration

The `global.mode` setting in your configuration file determines which API endpoint the runner fetches jobs from:

- **`global.mode=PRODUCTION`** → Fetches from `/crawlers-unprocessed`
- **`global.mode=DEVELOP`** → Fetches from `/crawlers-testing`
- **`global.mode=INTEGRATION`** → Fetches from `/crawlers-testing`

**For local development:** Make sure your test data is in the correct API endpoint, or set `global.mode=PRODUCTION` in `application-local.properties` if you want to fetch from `/crawlers-unprocessed`.

See `CrawlerService.calculateCrawlerUrl()` for implementation details.

Both endpoints are also partitioned by `runner_user_id` since 2026-09-02: a runner only fetches experiments assigned to it, and the default runner additionally fetches unassigned ones.

### ⚠️ Important: MultiLogin X Core Version Requirements

MultiLogin X enforces minimum browser core version requirements that can increase over time:

- **Current minimum core version**: 147 (rejects below); the designer's default profile is 149
- **Error to watch for**: `400 Bad Request: "Can't set core older than X. Please set higher core version"`

**If you encounter this error:**

1. Update your agent's MultiLogin profile template in the database
2. Set `"core_version": 149` (or higher, as required)
3. **Recommended**: Set `"auto_update_core": true` to automatically use the latest version and avoid future issues

**Example profile configuration:**
```json
{
  "core_version": 149,
  "auto_update_core": true,
  ...
}
```

Setting `auto_update_core: false` with an outdated core version will cause profile creation to fail. MultiLogin X will reject requests with older core versions as new browser releases become available.

## Documentation

For detailed information, see our comprehensive documentation:

- **[Getting Started Guide](doc/getting-started.md)** - Complete setup for new developers
- **[Architecture Overview](doc/architecture.md)** - System design and components
- **[Job System Guide](doc/job-system.md)** - Understanding jobs and execution
- **[Configuration Guide](doc/configuration.md)** - Service setup and credentials
- **[HAR Recording Guide](doc/har-recording.md)** - Network traffic capture optimization (NEW!)
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

### 🍪 Consent Dialogs (GDPR)
- Cookie/GDPR dialogs (YouTube consent bump, OneTrust, Cookiebot, Didomi, Usercentrics, TrustArc, Quantcast, Sourcepoint, generic) are answered automatically after every navigation, in EN/DE/FR/IT/ES
- The answer — `accept`, `reject` or `ignore` — is part of the experiment design: job behaviour `consent` > crawler preference `cookie_pref` (`{"accept_all": true|false}`) > `consent.choice` property
- Every decision is logged per job; a dialog that survives the click is a warning

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

### 📦 HAR Recording Optimization (New!)
- **Selective Recording**: Skip HAR capture for jobs without network activity (WaitJob, ScrollJobs, etc.)
- **Content Filtering**: Remove static resources (images, CSS, fonts) from HAR files
- **Domain Filtering**: Whitelist or blacklist specific domains
  - **Whitelist**: Only capture traffic to specified domains (e.g., `har.filter.includeDomains=api.example.com`)
  - **Blacklist**: Exclude analytics/tracking domains (e.g., `har.filter.excludeDomains=google-analytics.com`)
- **Body Size Truncation**: Limit request/response body sizes
- **Storage Savings**: Achieve 99%+ reduction in HAR storage costs
- See [HAR Recording Guide](doc/har-recording.md) for details

### 🛡️ Security & Compliance
- Secure credential management
- Configurable rate limiting and delays
- Comprehensive logging and audit trails with correlation tracking
- AWS integration for secure data storage
- Thread-safe execution with MDC context preservation

## Technology Stack

- **Java 25** (production runtime; CI also builds against 17) with Spring Boot framework
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

