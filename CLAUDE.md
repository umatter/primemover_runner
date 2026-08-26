# CLAUDE.md

Guidance for Claude Code working with PrimeMover - a Java/Spring Boot web automation system for browser automation with anti-detection.

## Quick Reference

### Build & Run
```bash
mvn clean install                    # Build all modules
mvn clean install -DskipTests        # Build without tests

# Run application
cd primemover-runner
mvn spring-boot:run                  # Production mode
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"  # Local dev
```

### Testing
```bash
mvn test                             # All tests
mvn test -Dtest=ClassName            # Specific class
mvn test -Dtest=ClassName#methodName # Specific method
```

### Project Structure
```
primemover_runner/
├── primemover-runner/           # Main application
│   ├── src/main/java/primemover/
│   │   ├── runner/              # Core execution (TaskManager, TaskExecutor, TaskOperator)
│   │   ├── service/             # Business logic (CrawlerService, JobService, StorageServices)
│   │   ├── model/               # Domain models (Crawler, Joblist, Job types, Agent, Proxy)
│   │   ├── api/                 # Backend API models & PrimemoverApiConverter
│   │   ├── capture/             # HAR recording (RecordingProxy, HarRecordingFilter, HarContentFilter)
│   │   ├── anticaptcha/         # CAPTCHA handling
│   │   ├── multilogin/          # Browser profile management (ml6/, mlx/)
│   │   ├── util/                # Utilities (Page, Typing, Scrolling, Waiter)
│   │   └── lambda/              # AWS Lambda integration
│   └── config/                  # Application properties
└── primemover-generated/        # Auto-generated MultiLogin X OpenAPI client
```

## Critical Configuration

### Startup Modes (`global.mode`)
- **DEVELOP**: Mock data, `/crawlers-testing` endpoint, includes assert jobs
- **INTEGRATION**: Real backend, `/crawlers-testing` endpoint, includes assert jobs
- **PRODUCTION**: Real backend, `/crawlers-unprocessed` endpoint, no assert jobs

**API endpoint selection**: Implemented in `CrawlerService.calculateCrawlerUrl()` (lines 61-74).

### MultiLogin X Core Version
- **Minimum**: Core version 137+ (as of 2025)
- **Error**: `400 Bad Request: "Can't set core older than X"`
- **Fix**: Set `"auto_update_core": true` in agent's `multilogin_profile` DB field

### Key Properties (`primemover-runner/config/application.properties`)
```properties
runner.MaxParallelExecution=2          # Concurrent browser threads
runner.UpdateInterval=15               # Minutes between job polls
runner.MaxRedoMinutes=45               # Max job age to execute
runner.ProcessingTimeoutMinutes=180    # Job timeout

multilogin.StartupRetryCount=3
multilogin.StartupDelaySeconds=30

har.recording.enabled=true
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,...  # Skip HAR for these
har.filter.enabled=false               # Content filtering

consent.choice=accept                  # cookie/GDPR dialogs: accept | reject | ignore (runner-wide default)
```

## Architecture Overview

### Data Flow
```
Backend API → CrawlerService → TaskManager → TaskExecutor → JobListRunner → TaskOperator → JobDispatcher → Job Handlers → Data Writers → Storage Services
```

### Key Classes
| Class | Location | Purpose |
|-------|----------|---------|
| `TaskManager` | runner/ | Polls backend, schedules joblists |
| `TaskExecutor` | runner/ | Thread pool for parallel execution |
| `TaskOperator` | runner/ | Browser init/teardown, job execution |
| `JobDispatcher` | runner/job/ | Routes jobs to handlers |
| `CrawlerService` | service/ | Backend API communication |
| `PrimemoverApiConverter` | api/converter/ | API → internal model conversion |
| `RecordingProxy` | capture/ | MITM proxy for HAR capture |

### Job Types
| Type | Handler | Purpose |
|------|---------|---------|
| `VisitJob` | `VisitJobHandler` | Navigate to URL |
| `ClickJob` | `ClickJobHandler` | Click element |
| `EnterTextFieldJob` | `EnterTextFieldJobHandler` | Fill form field |
| `SingleSelectJob` | `SingleSelectUrlJobHandler` | Select element (RANDOM/FIRST/LAST/CALCULATED) |
| `ScrollByLengthJob` | `ScrollByLengthJobHandler` | Scroll pixels |
| `ScrollByDurationJob` | `ScrollByDurationJobHandler` | Scroll for duration |
| `WaitJob` | `WaitJobHandler` | Delay |
| `HandleAlertJob` | `HandleAlertJobHandler` | Accept/dismiss dialog |
| `LambdaSelectJob` | `LambdaSelectJobHandler` | Lambda-based selection |

### Consent dialogs (cookie / GDPR)

`primemover.util.consent` answers consent dialogs — the YouTube/Google consent
bump, OneTrust, Cookiebot, Didomi, Usercentrics, TrustArc, Quantcast,
Sourcepoint and a generic cookie/consent container, in EN/DE/FR/IT/ES — after
every `VisitJob` page load, after the `LambdaSelectJob` click, and before the
generic `ModalDismisser` in the scroll jobs. The detection/click logic is
`src/main/resources/consent/consent-handler.js`; buttons are matched by
wording and CMP-specific ids, never by position.

**The answer is a treatment variable, not a nuisance.** "Reject all" switches
off exactly the signed-out personalisation a study may be measuring, so the
choice is never hard-coded. Precedence (`ConsentPolicy`):

1. job behaviour `consent` = `accept | reject | ignore`
2. crawler preference `cookie_pref` = `{"accept_all": true|false}` — the
   experiment designer emits this for every bot; before 2026-08-26 nothing
   consumed it
3. `consent.choice` in `application.properties` (default `accept`)

Every outcome is logged per job: `consent dialog (youtube) answered: accept ->
clicked 'Accept all'`; a dialog still visible after two clicks is a WARN,
because that is the case where an arm silently runs behind a modal.

Why this exists: every run up to 2026-08-26 egressed from a **US** exit IP, so
no bot ever saw the dialog and it went unnoticed that a `visit → wait →
lambda-select` workflow had no consent handling at all (only the scroll jobs
called `ModalDismisser`). From a CH/EU exit YouTube serves
`ytd-consent-bump-v2-lightbox` on **every** session, since each session is a
fresh Multilogin profile.

Tests: `ConsentPolicyTest` (precedence, always on);
`ConsentHandlerBrowserTest` (headless Chrome against fixture pages copied from
live markup, opt-in: `-Dconsent.browserTest=true [-Dconsent.chrome=/usr/bin/google-chrome]`);
`ConsentHandlerLiveTest` (real site, opt-in: `-Dconsent.liveUrl=https://www.youtube.com/watch?v=...`
`-Dconsent.liveChoice=reject` — only meaningful from an IP that is served the
dialog). Re-run the live test when YouTube changes its markup.

### JobList States
- **Initial**: INACTIVE, ACTIVE
- **Running**: RUNNING_INPROGRESS → RUNNING_INITDONE → RUNNING_JOBSDONE
- **Terminal**: DONE_SUCCESS, DONE_FAILURE, DONE_INITFAILED, DONE_JOBSFAILURE, DONE_PROCESSING_TIMEOUT

### Job States
RUNNING → STORINGDATA → SUCCESS | FAILURE | WARNING_JOBDATA | SKIPPED

## Common Development Tasks

### Adding a New Job Type
1. Create model in `primemover/model/` extending `BaseJob`
2. Create handler in `primemover/runner/job/handler/` implementing `IJobHandler`
3. Register in `JobDispatcher.setup()`
4. Add API model in `primemover/api/model/`
5. Add conversion in `PrimemoverApiConverter`

### Debugging Job Execution
- Check logs for correlation ID (MDC context preserved)
- Screenshots saved after each job
- HAR files capture network traffic

### Logging
- `config/logback-spring.xml`. The `local` profile (what the VM runs) has root
  DEBUG; `primemover.*` loggers are DEBUG explicitly.
- **`org.springframework.web` stays at INFO, never lower.** At DEBUG Spring logs
  every request body via `toString()` — under both `RestTemplate` and
  `HttpLogging` — and the generated `UserSignInRequest` prints the Multilogin
  password. That went to the log file, the journal and Loki once a minute until
  2026-08-25. The models are generated, so the logger level is the only fix.
- Log lines carry the MDC context (`correlationId=…, crawler.id=…, joblist.id=…`)
  *between* the timestamp and the level; anything parsing these lines must
  expect that.

### MultiLogin
- MLX uses OpenAPI-generated client in `primemover-generated`
- Regenerate: `mvn clean install` (openapi-generator-maven-plugin)
- Source spec: `src/main/resources/MultiloginX.yaml`

## Key Concepts

**Crawler**: Browser config (agent + proxy) with joblists to execute

**Joblist**: Ordered job collection, scheduled execution, tracks state

**Job**: Individual task with `isFailsave()` (continue on error) and `resetPreviousFailure()`

**Context**: Runtime environment with WebDriver, proxy, captcha solver

**Recording Proxy**: MITM proxy capturing HAR traffic (BrowserUpRecorderProxy)

**Storage Services**: `IStorageService` implementations - AwsS3StorageService, BackendStorageService, FileStorageService

## HAR Recording Optimization

Job type filtering (`HarRecordingFilter`): Skips HAR for jobs without network traffic.

Content filtering (`HarContentFilter`): Remove static resources, filter by domain, truncate bodies.

Config example:
```properties
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob
har.filter.enabled=true
har.filter.removeStaticResources=true
har.filter.excludeDomains=google-analytics.com,facebook.com
```
