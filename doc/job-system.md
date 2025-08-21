# Job System Guide

The job system is the core of PrimeMover's automation capabilities. This guide explains how jobs work, how to configure them, and how to create new job types.

## Understanding Jobs

Jobs are the fundamental unit of work in PrimeMover. Each job represents a single action that can be performed on a web page:

- **BaseJob**: Abstract base class providing common functionality (ID, name, description, CAPTCHA handling)
- **SelectorBaseJob**: Base for jobs that interact with HTML elements using CSS/XPath selectors
- **Job Execution States**: Tracked via `JobState` enum (RUNNING, SUCCESS, FAILURE, WARNING, SKIPPED)

## Job Types and Capabilities

PrimeMover supports a comprehensive set of job types, each configurable via behavior parameters:

### Navigation Jobs
- **VisitJob**: Navigate to URLs (automatically resets failure state)
- **SwitchToJob**: Switch between browser windows/frames

### Interaction Jobs
- **ClickJob**: Click elements with optional page reload expectation
- **EnterTextFieldJob**: Fill form fields with configurable typing simulation
- **SingleSelectJob**: Select elements based on criteria (first, last, random, calculated)
- **HandleAlertJob**: Handle browser dialogs and alerts

### Utility Jobs
- **WaitJob**: Pause execution for specified duration
- **ScrollByDurationJob**: Scroll pages for a specific time period
- **ScrollByLengthJob**: Scroll pages by pixel distance
- **LambdaSelectJob**: AWS Lambda-based element selection

### Failsafe Variants
Most interaction jobs have "Try" variants that don't fail the entire joblist:
- `TryClickJob`, `TryEnterTextFieldJob`, `TrySwitchJob`, `TryHandleAlertJob`

## Job Configuration Parameters

Jobs are configured via behavior parameters that control:

### Selector Types
- **CSS**: CSS selector syntax for element targeting
- **XPath**: XPath expressions for complex element selection
- **ID**: Element ID attribute
- **Class**: CSS class name
- **Text**: Element text content

### Typing Modes
- **DIRECT**: Immediate text input
- **SIMULATED_NOTYPOS**: Human-like typing without errors
- **SIMULATED_KEEPINGTYPOS**: Realistic typing with intentional errors
- **SIMULATED_FIXINGTYPOS**: Typing with errors that get corrected

### Decision Types
- **FIRST**: Select first matching element
- **LAST**: Select last matching element
- **RANDOM**: Random selection from matches
- **CALCULATED**: Selection based on extracted criteria

### CAPTCHA Handling
- **NEVER**: No CAPTCHA solving
- **ALWAYS**: Solve CAPTCHAs before and after job execution
- **BEFORE**: Solve CAPTCHAs before job execution
- **AFTER**: Solve CAPTCHAs after job execution
- **RANDOM**: Randomly decide when to solve CAPTCHAs

### Failsafe Behavior
- **Continue**: Continue execution even if job fails
- **Stop**: Stop execution on job failure
- **Reset**: Whether to reset previous failure states

## Working with Joblists

Joblists group related jobs into executable sequences:

### Execution Order
Jobs run sequentially in the order defined in the joblist.

### State Management
Each joblist tracks its overall execution state via `JoblistState`:
- **RUNNING_INPROGRESS**: Joblist is currently executing
- **RUNNING_INITDONE**: Initialization completed successfully
- **RUNNING_JOBSDONE**: All jobs completed
- **DONE_SUCCESS**: Joblist completed successfully
- **DONE_FAILURE**: Joblist failed
- **SKIPPED_TIMEDOUT**: Joblist skipped due to timeout

### Timing Control
- Built-in timeout handling (45 minutes default)
- Configurable retry mechanisms
- Automatic cleanup on completion or failure

### Failure Handling
Individual job failures can be configured to:
- Stop the entire joblist execution
- Continue with remaining jobs
- Reset previous failure states

## Understanding Job Execution Flow

1. **API Request**: External system sends job definition
2. **Conversion**: `PrimemoverApiConverter` creates internal job objects
3. **Scheduling**: `JobListRunner` manages execution lifecycle
4. **Context Creation**: Job-aware context provides execution environment
5. **Execution**: Job-specific logic runs within context
6. **Data Recording**: Complete browser state captured
7. **State Updates**: Job and joblist states updated via REST API
8. **Cleanup**: Resources released and context cleaned up

## Adding New Job Types

### 1. Create Job Class
Extend `BaseJob` or `SelectorBaseJob`:

```java
public class CustomJob extends SelectorBaseJob {
    private String customParameter;
    
    public CustomJob(Long id, String name, String description, String task,
                    HtmlSelectorType type, String selector, String customParameter) {
        super(id, name, description, task, type, selector);
        this.customParameter = customParameter;
    }
    
    // Add getters and setters
    public String getCustomParameter() {
        return customParameter;
    }
    
    public void setCustomParameter(String customParameter) {
        this.customParameter = customParameter;
    }
}
```

### 2. Add API Conversion
Update `PrimemoverApiConverter.convertApiJob()`:

```java
case "customjob":
    res = convertApiCustomJob(job.getId(), job.getName(), 
                             job.getDescription(), captchaSolving, behaviors);
    break;
```

### 3. Implement Conversion Logic
Handle behavior parameter mapping:

```java
private static CustomJob convertApiCustomJob(Long id, String name, String description,
        CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
    
    // Extract selector information
    HtmlSelectorType selectorType = extractSelectorType(behaviours);
    String selector = behaviours.get("selector");
    
    // Extract custom parameter
    String customParameter = behaviours.get("customParameter");
    
    // Create and configure job
    CustomJob job = new CustomJob(id, name, description, "custom-task", 
                                 selectorType, selector, customParameter);
    
    // Set base fields (CAPTCHA handling, failsafe behavior, etc.)
    setBaseFields(job, id, name, description, captchaSolving, behaviours);
    
    return job;
}
```

### 4. Add Job Handler (if needed)
For complex jobs that require special execution logic, create a handler in `runner/job/handler/`:

```java
@Component
public class CustomJobHandler implements IJobHandler {
    
    @Override
    public void handle(IJobAwareRunnerContext<?> context) {
        // Get the job from context
        IJob job = context.getJob();
        
        // Check if this handler can process this job type
        if (!(job instanceof CustomJob)) {
            throw new IllegalArgumentException("Handler can only process CustomJob instances");
        }
        
        CustomJob customJob = (CustomJob) job;
        
        // Custom execution logic
        WebDriver driver = context.getDriver();
        String selector = customJob.getSelector();
        String customParameter = customJob.getCustomParameter();
        
        // Perform custom automation logic
        WebElement element = driver.findElement(By.cssSelector(selector));
        // ... custom logic using customParameter
    }
}
```

## Best Practices

### Job Design
- Keep jobs focused on a single action
- Use descriptive names and descriptions
- Configure appropriate timeout values
- Consider using failsafe variants for non-critical operations

### Error Handling
- Use try-catch blocks for robust error handling
- Log meaningful error messages with context
- Consider retry logic for transient failures
- Use correlation IDs for debugging

### Performance
- Minimize unnecessary waits and delays
- Use efficient selectors (ID > CSS > XPath)
- Cache frequently used elements
- Clean up resources properly

### Testing
- Write unit tests for job conversion logic
- Test jobs with different browser configurations
- Verify behavior with various selector types
- Test failure scenarios and recovery

## Common Patterns

### Conditional Execution
```java
// Execute job only if certain conditions are met
if (context.getCurrentUrl().contains("target-domain")) {
    // Execute job logic
}
```

### Element Waiting
```java
// Wait for element to be present and clickable
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
WebElement element = wait.until(ExpectedConditions.elementToBeClickable(
    By.cssSelector(job.getSelector())
));
```

### Data Extraction
```java
// Extract data and store in job context
WebElement element = driver.findElement(By.cssSelector(selector));
String extractedData = element.getText();
context.setJobData("extractedValue", extractedData);
```

### Error Recovery
```java
try {
    // Primary job logic
    performMainAction();
} catch (Exception e) {
    if (job.isFailsafe()) {
        logger.warn("Job failed but continuing due to failsafe mode", e);
        return; // Continue execution
    } else {
        throw e; // Stop execution
    }
}
```
