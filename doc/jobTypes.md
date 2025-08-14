# PrimeMover Job Types Reference

This document provides a comprehensive overview of all job types supported by PrimeMover for web automation tasks.

## Overview

PrimeMover uses a job-based system where each job represents a single atomic action that can be performed on a web page. Jobs are executed sequentially within joblists, allowing complex automation workflows to be built from simple, reusable components.

## Job Architecture

### Base Classes

#### **BaseJob**
- **Purpose**: Foundation class for all job types
- **Features**: Common functionality (ID, name, description, CAPTCHA handling, failsafe options)
- **Location**: `primemover.model.jobs.BaseJob`

#### **SelectorBaseJob** 
- **Purpose**: Base for jobs that interact with HTML elements
- **Features**: Extends BaseJob with HTML selector support (CSS selectors, XPath)
- **Location**: `primemover.model.jobs.SelectorBaseJob`

#### **ScrollBaseJob**
- **Purpose**: Base for scrolling-related operations
- **Features**: Extends BaseJob with scroll direction support
- **Location**: `primemover.model.jobs.ScrollBaseJob`

#### **AssertProxyJob**
- **Purpose**: Abstract base for proxy validation jobs
- **Features**: Validates IP, country, and city information
- **Location**: `primemover.model.jobs.AssertProxyJob`

### Job Execution Modes

#### **Strict Mode vs Failsafe Mode**
Many job types come in two variants:

- **Strict Mode** (e.g., `clickjob`): Fails and stops execution if the operation cannot be completed
- **Failsafe Mode** (e.g., `tryclickjob`): Continues execution even if the operation fails, logging the failure

## Supported Job Types

### **Navigation Jobs**

#### **VisitJob** (`visitjob`)
- **Purpose**: Navigate to a specific URL
- **Parameters**: 
  - `url`: Target URL to visit
- **Base Class**: BaseJob
- **Failsafe**: Enabled by default (resets previous failures)
- **Example Use**: Navigate to a login page, visit product pages

#### **SwitchToJob** (`switchjob` / `tryswitchjob`)
- **Purpose**: Switch between browser windows, tabs, or frames
- **Parameters**:
  - `selectionType`: HTML selector type (CSS, XPath)
  - `selector`: Element selector to switch to
- **Base Class**: SelectorBaseJob
- **Modes**: Strict (`switchjob`) and Failsafe (`tryswitchjob`)
- **Example Use**: Switch to popup windows, navigate between frames

### **Element Interaction Jobs**

#### **ClickJob** (`clickjob` / `tryclickjob`)
- **Purpose**: Click on HTML elements
- **Parameters**:
  - `selectionType`: HTML selector type (CSS, XPath)
  - `selector`: Element selector to click
  - `expectsPageReload`: Whether the click triggers a page reload (default: false)
- **Base Class**: SelectorBaseJob
- **Modes**: Strict (`clickjob`) and Failsafe (`tryclickjob`)
- **Example Use**: Click buttons, links, form elements

#### **EnterTextFieldJob** (`entertextfieldjob` / `tryentertextfieldjob`)
- **Purpose**: Enter text into form fields with typing simulation
- **Parameters**:
  - `selectionType`: HTML selector type (CSS, XPath)
  - `selector`: Input field selector
  - `text`: Text to enter
  - `appendReturn`: Whether to press Enter after typing (default: true)
  - `typingMode`: Typing simulation mode (default: SIMULATED_NOTYPOS)
- **Base Class**: SelectorBaseJob
- **Modes**: Strict (`entertextfieldjob`) and Failsafe (`tryentertextfieldjob`)
- **Example Use**: Fill login forms, enter search terms, complete surveys

#### **SingleSelectJob** (`singleselectjob` / `singleselecturljob`)
- **Purpose**: Select single elements based on decision criteria
- **Parameters**:
  - **Version 1** (Legacy):
    - `selectionType`: HTML selector type
    - `selector`: Element selector (used for both clicking and criteria)
    - `criterionExtractor`: Extraction logic for calculated decisions
  - **Version 2** (Extended):
    - `clickSelectionType`: Selector type for clicking
    - `clickSelector`: Element to click
    - `criteriaSelectionType`: Selector type for decision criteria
    - `criteriaSelector`: Element to evaluate for decision
    - `criteriaExtractor`: Extraction logic for calculated decisions
  - `decisionType`: How to make selection decisions
- **Base Class**: SelectorBaseJob
- **Example Use**: Select products from lists, choose options based on criteria

### **Scrolling Jobs**

#### **ScrollByDurationJob** (`scrollbydurationjob`)
- **Purpose**: Scroll for a specific time duration
- **Parameters**:
  - `scrollDuration` or `duration`: Time to scroll in seconds
  - `scrollDirection`: Direction to scroll (UP, DOWN, LEFT, RIGHT)
- **Base Class**: ScrollBaseJob
- **Example Use**: Infinite scroll pages, load more content

#### **ScrollByLengthJob** (`scrollbylengthjob`)
- **Purpose**: Scroll by a specific pixel distance
- **Parameters**:
  - `length`: Number of pixels to scroll
  - `scrollDirection`: Direction to scroll
- **Base Class**: ScrollBaseJob
- **Example Use**: Precise scrolling, navigate to specific page sections

#### **AssertScrolledMaxJob**
- **Purpose**: Assert that the page has been scrolled to maximum position
- **Parameters**:
  - `direction`: Scroll direction to check
- **Base Class**: BaseJob
- **Example Use**: Verify all content has been loaded, ensure complete page coverage

### **Dialog and Alert Handling**

#### **HandleAlertJob** (`handlealertjob` / `tryhandlealertjob`)
- **Purpose**: Handle browser alerts, confirms, and prompts
- **Parameters**:
  - `action`: How to handle the dialog (ACCEPT, DISMISS, etc.)
- **Base Class**: BaseJob
- **Modes**: Strict (`handlealertjob`) and Failsafe (`tryhandlealertjob`)
- **Example Use**: Handle JavaScript alerts, confirm dialogs, permission requests

### **Timing and Flow Control**

#### **WaitJob** (`waitjob`)
- **Purpose**: Pause execution for a specified duration
- **Parameters**:
  - `seconds`: Number of seconds to wait (default: 30)
- **Base Class**: BaseJob
- **Example Use**: Wait for page loads, add delays between actions, rate limiting

### **Advanced Integration Jobs**

#### **LambdaSelectJob** (`lambdaselectjob` / `lambdasingleselectjob`)
- **Purpose**: Use AWS Lambda functions for complex element selection logic
- **Parameters**:
  - `linkValue`: Lambda function identifier or URL
  - `linkType`: Type of Lambda link (FUNCTION_NAME, etc.)
  - `username`: Optional authentication username
  - `password`: Optional authentication password
- **Base Class**: BaseJob
- **Handler**: `LambdaSelectJobParam` and `LambdaSelectJobResult`
- **Example Use**: Complex decision logic, external API integration, advanced element selection

## Job Configuration

### **Common Parameters**

All jobs inherit these parameters from `BaseJob`:

- **`id`**: Unique job identifier
- **`name`**: Human-readable job name
- **`description`**: Job description for documentation
- **`task`**: Optional task identifier
- **`captchaSolving`**: CAPTCHA handling strategy (NEVER, ALWAYS, BEFORE, AFTER, RANDOM)
- **`runFailsave`**: Whether job should continue on failure (default: false)
- **`resetPreviousFailure`**: Whether to reset previous failure state (default: false)

### **Selector-Based Parameters**

Jobs extending `SelectorBaseJob` include:

- **`selector`**: CSS selector or XPath expression
- **`type`**: Selector type (CSS, XPATH, ID, CLASS_NAME, etc.)

### **Behavior Parameters**

Jobs are configured through behavior key-value pairs that get converted from the API:

```java
// Example behavior configuration
Map<String, String> behaviors = {
    "task": "login_flow",
    "url": "https://example.com/login",
    "selectionType": "CSS",
    "selector": "#username",
    "text": "user@example.com",
    "captchaMode": "BEFORE"
}
```

## Job Execution Flow

### **1. API to Internal Conversion**
- API jobs (JSON) → `PrimemoverApiConverter.convertApiJob()` → Internal job objects
- Behavior parameters extracted and mapped to job-specific properties
- CAPTCHA solving strategy applied based on configuration

### **2. Job Execution Context**
- Jobs execute within a `RunnerContext` that provides:
  - WebDriver instance for browser automation
  - Proxy configuration
  - CAPTCHA solver integration
  - Current job awareness

### **3. State Management**
- Each job tracks execution state: RUNNING, SUCCESS, FAILURE, WARNING, SKIPPED
- Failsafe jobs can continue execution chain even after failures
- State persistence for job recovery and monitoring

## Adding New Job Types

To add a new job type to PrimeMover:

### **1. Create Job Class**
```java
public class MyNewJob extends BaseJob {
    private String myParameter;
    
    // Constructors, getters, setters
}
```

### **2. Add Conversion Logic**
In `PrimemoverApiConverter.convertApiJob()`:
```java
case "mynewjob":
    res = convertApiMyNewJob(job.getId(), job.getName(), 
          job.getDescription(), captchaSolving, behaviors);
    break;
```

### **3. Implement Converter Method**
```java
private static MyNewJob convertApiMyNewJob(Long id, String name, 
        String description, CaptchaSolvingType captchaSolving, 
        Map<String, String> behaviours) {
    MyNewJob job = new MyNewJob();
    setBaseFields(job, id, name, description, captchaSolving, behaviours);
    job.setMyParameter(behaviours.get("myParameter"));
    return job;
}
```

### **4. Add Job Handler** (if needed)
For complex jobs requiring special execution logic:
```java
public class MyNewJobHandler implements IJobHandler<MyNewJob> {
    // Implementation
}
```

### **5. Write Tests**
Create unit tests for the new job type and its conversion logic.

## Job Type Categories

### **By Complexity**
- **Simple**: WaitJob, VisitJob
- **Moderate**: ClickJob, EnterTextFieldJob, ScrollJobs
- **Complex**: SingleSelectJob, LambdaSelectJob
- **Specialized**: AssertProxyJob, HandleAlertJob

### **By Interaction Type**
- **Navigation**: VisitJob, SwitchToJob
- **User Input**: ClickJob, EnterTextFieldJob
- **Content Interaction**: SingleSelectJob, ScrollJobs
- **System Control**: WaitJob, HandleAlertJob
- **External Integration**: LambdaSelectJob

### **By Failure Handling**
- **Strict Mode**: Stops execution on failure
- **Failsafe Mode**: Continues execution, logs failures
- **Assert Mode**: Validates conditions, fails if not met

## Best Practices

### **Job Design**
- Keep jobs atomic and focused on single actions
- Use descriptive names and descriptions
- Configure appropriate CAPTCHA solving strategies
- Choose failsafe mode for non-critical operations

### **Selector Strategy**
- Prefer CSS selectors for performance
- Use XPath for complex element relationships
- Test selectors thoroughly across different page states
- Consider dynamic content and loading states

### **Error Handling**
- Use failsafe variants for optional operations
- Configure appropriate timeout values
- Plan for CAPTCHA interruptions
- Test failure scenarios thoroughly

### **Performance**
- Use WaitJob sparingly to avoid unnecessary delays
- Configure realistic timeouts for operations
- Consider scroll performance on large pages
- Monitor job execution times for optimization

## Troubleshooting

### **Common Issues**
- **Element not found**: Check selector accuracy, page loading state
- **Timeout errors**: Increase wait times, check network conditions
- **CAPTCHA interruptions**: Configure appropriate solving strategies
- **Scroll failures**: Verify page scrollability, check for fixed elements

### **Debugging Tips**
- Run with `browser.headless=false` to observe execution
- Enable detailed logging for job execution
- Use browser developer tools to test selectors
- Verify page state before job execution

## Future Extensions

The job system is designed for extensibility. Potential future job types could include:

- **FileUploadJob**: Handle file upload interactions
- **DragDropJob**: Drag and drop operations
- **HoverJob**: Mouse hover interactions
- **KeyboardJob**: Complex keyboard shortcuts
- **CookieJob**: Cookie manipulation
- **LocalStorageJob**: Browser storage operations
- **ScreenshotJob**: Capture specific elements or full pages
- **ValidationJob**: Assert page content or element states

The modular design makes it straightforward to add new job types as automation requirements evolve.
