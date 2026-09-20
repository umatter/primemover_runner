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

Production runs Java 25; build with it, not an older JDK, if you are building against a live jar.

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

Both `/crawlers-unprocessed` and `/crawlers-testing` are also partitioned by
`runner_user_id` since 2026-09-02: a runner only fetches experiments assigned
to it, and the default runner additionally fetches unassigned ones.

### MultiLogin X Core Version
- **Minimum**: Core version 147+ (rejects below); the designer's default profile is 149
- **Error**: `400 Bad Request: "Can't set core older than X"`
- **Fix**: Set `"auto_update_core": true` in agent's `multilogin_profile` DB field

### Key Properties (`primemover-runner/config/application.properties`)
```properties
runner.MaxParallelExecution=2          # Concurrent browser threads (tuned per box: ex63=15, VM=10)
runner.UpdateInterval=15               # Minutes between job polls
runner.MaxRedoMinutes=45               # Max job age to execute
runner.ProcessingTimeoutMinutes=180    # Job timeout

multilogin.StartupRetryCount=3
multilogin.StartupDelaySeconds=30
# Startup retry backoff (see "Lambda-select reliability" below). A 429 from
# Multilogin's profile-create is a rate limit -- only time clears it -- so it
# waits out the window; other failures retry promptly.
multilogin.StartupRetryDelaySeconds=5
multilogin.RateLimitBackoffSeconds=75
# Batch concurrent profile creations into one API call (Multilogin caps `times`
# at 10; the batcher clamps to that). 12 bots -> one call of 10 + one of 2,
# instead of 12 calls that trip the 50/min limit. 1 disables batching.
multilogin.ProfileCreateBatchSize=10
multilogin.ProfileCreateBatchWindowMs=2500
multilogin.ProfileCreateWaitTimeoutMs=120000
# Renderer recycled every N navigations to bound a per-navigation V8 heap leak;
# reload+re-anchor a watch page that came back without recommendations.
runner.RendererRecycleAfterNavigations=25
runner.RecoverUnrenderedWatchPage=true

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

### The bot's watch history (`already_watched`)

`LambdaSelectJobHandler` fetches `GET /crawlers/{id}/watched` and puts the result
in the lambda payload as `already_watched`, so the decision function can drop
videos this bot has already chosen. Afterwards it reports the video that WAS
chosen back on the status PUT as `decision_selected_id`, which is what keeps the
history current: before that step existed the ids reached the database only via a
backfill out of MinIO, so the filter could not see a choice the same session had
just made.

**`null` and an empty list are different answers, and this is the layer that most
easily gets it wrong.** `CrawlerService.getWatchedVideoIds()` returns **null** on
every failure path — unreachable API, the 409 the API answers when the column is
missing, an empty body — and the field is then omitted entirely. The decision
function reads that as *unknown*, reports `already_watched: not_implemented` and
filters nothing, which is exactly what an un-redeployed runner produces. An empty
**list** is the positive claim "this bot has watched nothing" and counts as the
filter applying. Returning `[]` on failure would report a filter as applied while
filtering nothing.

It is deliberately **not** `@Retryable`: a lambdaselect job should not be held up
over history, and filtering nothing is the safe degradation. The injection is
`@Autowired(required = false)` and the call site null-checks, so a context
without the bean still runs the job.

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


## Lambda-select reliability

`LambdaSelectJobHandler` turns a decision into a click. A single YouTube page
quirk used to cost a whole session, because the workflow's two `visitjob`s run
only at the start -- nothing re-navigates -- so one bad landing failed every
remaining job. These layers, added across 2026-08/09, each recover a distinct
failure and mark the job degraded (a recovered session is not an ordinary one):

- **Empty xpath with a selection → navigate by id.** The decision function can
  choose a video from `ytInitialData` when the sidebar never rendered into the
  DOM, returning the id with an empty xpath. `validateXpath` used to throw before
  the fallback; now the runner navigates straight to the video.
- **Found but not clickable → recover, don't fail.** An element that is located
  but never becomes clickable (e.g. obscured by YouTube's endscreen video-wall)
  throws `TimeoutException`, which is *not* `NoSuchElementException`. The retry
  loop now treats both as recoverable (`isRecoverableClickFailure`) and falls
  through to navigate-by-id; other `WebDriverException`s still propagate.
- **Empty shell / unusable page → reload once, then re-anchor**
  (`primemover.util.WatchPageAnchor`, `runner.RecoverUnrenderedWatchPage`). A SPA
  navigation can leave `ytd-watch-flexy` + an empty `#related`. A reload fixes a
  *transiently* broken page; it cannot fix one behaving correctly (an
  age-restricted video reloads back to "Sign in to confirm your age"), so when
  the reload fails the session returns to the last page that had recommendations.
  The last-good URL is keyed by driver, not a field -- the handler is shared
  across bots, and a field would mix two bots' viewing histories.
- **Profile-create rate limit → batch, then back off**
  (`primemover.util.StartupBackoff`, `ProfileCreateBatcher`). 12 bots launching
  together made 12 `POST /profile/create` calls in one second and tripped
  Multilogin's 50/min limit. Batching collapses them to one call with `times`
  (capped at 10); a 429 that still occurs waits out the 60s window rather than
  retrying into it.
- **HAR timings sanitised** (`primemover.util`/`capture.HarTimingSanitizer`, runs
  unconditionally before serialization). A request that never got a response has
  its `wait` computed against an unset time, so the recorder writes `time`
  ≈ -1.3e9 ms. Values below the spec's `-1` are rewritten to `-1` (not 0 -- a
  request nobody timed is not an instantaneous one), so a consumer summing them
  gets a real total.

Queue rollups distinguish loss from degradation: `DONE_JOBSWARNING`
("completed with lost jobs") when jobs failed or were skipped, `DONE_JOBSDEGRADED`
("completed, some jobs degraded") when every job ran but some finished degraded.
Both carry `status_code = warning`; only the sentence differs, so a consumer
gating on `failure OR warning` is unaffected.

PR #82 (`master` `1826600`, deployed 2026-09-03) carries `lambda_select` job
behaviours into the decision payload — this underlies the newly-deployed
strategy-marker gate in `bot-decision-yt` (the behaviours channel activates only
when the job carries a `strategy`; legacy experiments are unaffected).

## Deploying to a node (ex63, pm-vm)

Node checkouts only receive; nothing is edited on a node. The laptop pushes
into the checkout (`receive.denyCurrentBranch=updateInstead`; hub script
`control_room/scripts/deploy.sh <node> primemover_runner`), then on the node:
`cd primemover-runner && mvn -q -DskipTests package`, swap the jar with a
copy kept at `/var/tmp/runner-jar-rollback.jar`, `sudo systemctl restart
primemover-runner`. The unit runs `--spring.profiles.active=local
--spring.config.location=config/`.

**Node-local settings go in `config/application-local.properties`** (gitignored,
loaded by the `local` profile on top of the tracked `application.properties`).
Never edit the tracked file on a node: a dirty checkout refuses the push and
gets merged around by pulls -- `recorder.enabled=false` on both nodes lived
that way until 2026-09-20 and had to be "preserved" through every deploy.
