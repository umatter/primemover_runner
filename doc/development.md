# Development Guide

This guide covers advanced development workflows, best practices, and common development tasks for PrimeMover.

## Development Environment Setup

### IDE Configuration

#### IntelliJ IDEA
1. Import as Maven project
2. Install Lombok plugin: `File → Settings → Plugins → Search "Lombok"`
3. Enable annotation processing: `File → Settings → Build → Compiler → Annotation Processors → Enable`
4. Set project SDK to Java 17+

#### Eclipse
1. Import as existing Maven project
2. Install Lombok: Download `lombok.jar` and run `java -jar lombok.jar`
3. Refresh and clean the project

#### Visual Studio Code
1. Install required extensions:
   - `Extension Pack for Java` (includes Maven support)
   - `Lombok Annotations Support for VS Code`
   - `Spring Boot Extension Pack`
2. Open the project folder in VS Code
3. Configure Java version: `Ctrl+Shift+P` → `Java: Configure Runtime` → Set to Java 17+

### Running in Development Mode

#### From IDE
- Run the main class: `primemover.runner.PrimeMoverApplication`
- Set active profile: `-Dspring.profiles.active=local`
- Set working directory: `primemover-runner/`

#### From Command Line
```bash
cd primemover-runner
mvn spring-boot:run -Dspring.profiles.active=local
```

## Development Workflow

### Code Generation

The `primemover-generated` module automatically regenerates API clients during Maven compilation:

```bash
# Regenerate API clients after updating MultiloginX.yaml
cd primemover-generated
mvn clean compile

# Or rebuild the entire project
cd ..
mvn clean install
```

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for specific module
cd primemover-runner
mvn test

# Run specific test class
mvn test -Dtest=JobListRunnerTest

# Run tests with specific profile
mvn test -Dspring.profiles.active=test
```

### Debugging Browser Automation

For debugging browser automation issues:

```properties
# In your local configuration (application-local.properties)
# Execution and timing
runner.MaxRedoMinutes=5  # Shorter timeout for development
runner.StartupRetryCount=1  # Fewer retries for faster feedback

# Detailed logging
logging.level.primemover=DEBUG
logging.level.primemover.util.NavigationHelper=TRACE
logging.level.primemover.runner.joblist=DEBUG
logging.level.primemover.api.converter=DEBUG
logging.level.org.springframework.web.client.RestTemplate=TRACE

# MultiLogin debugging (if using MultiLogin)
multilogin.StartupRetryCount=1
multilogin.StartupDelaySeconds=5
multilogin.ConnectTimeoutSeconds=10

# Proxy debugging
proxy.pingConnections=true
proxy.verifyConnections=true
```

### Local Testing Without External Services

For development without external service dependencies:

```properties
# application-local.properties for offline development
spring.profiles.active=local
global.mode=DEVELOP

# Disable all external services
anticaptcha.enabled=false
anticaptcha.defaultMode=NEVER
multilogin.enabled=false
recorder.enabled=false

# Proxy settings (disabled for offline development)
proxy.pingConnections=false
proxy.verifyConnections=false

# Execution settings for local testing
runner.MaxParallelExecution=1
runner.MaxRedoMinutes=5  # Shorter timeout for development
runner.StartupRetryCount=1

# Mock backend API (if you have a local mock server)
backend.endpoint.api=http://localhost:3000/mock-api/
backend.endpoint.runner=http://localhost:3000/mock-api/runner/
backend.username=mock-user
backend.password=mock-password

# Development logging
logging.level.primemover=DEBUG
logging.level.primemover.runner=TRACE
logging.level.org.springframework.web.client.RestTemplate=DEBUG
```

## Common Development Tasks

### Adding New Job Types

1. Create job class extending `BaseJob` or `SelectorBaseJob`
2. Add conversion logic in `PrimemoverApiConverter.convertApiJob()`
3. Add job handler in `runner/job/handler/` if needed
4. Write unit tests for the new job type

See the [Job System Guide](job-system.md) for detailed instructions.

### Testing Browser Automation

```bash
# Run local setup
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--spring.config.location=config/"
```

### Working with Generated Code

- Never edit files in `primemover-generated/target/` - they're auto-generated
- Modify `MultiloginX.yaml` to change the API specification
- Run `mvn compile` to regenerate after YAML changes

### Database and State Management

PrimeMover uses in-memory state management by default. For persistent development:

```properties
# Enable file-based job state persistence
job.state.persistence=file
job.state.directory=./dev-data/jobs
```

## Troubleshooting Development Issues

### Build Failures

```bash
# Clean and rebuild everything
mvn clean install -U

# Skip tests if they're failing
mvn clean install -DskipTests

# Check for dependency conflicts
mvn dependency:tree
```

### Browser Driver Issues

```bash
# Clear WebDriverManager cache
rm -rf ~/.cache/selenium/

# Force download of latest drivers
mvn test -Dwebdrivermanager.forceDownload=true
```

### Port Conflicts

```properties
# Change default port in application-local.properties
server.port=8081

# Or use random port
server.port=0
```

### Memory Issues

```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512m"

# Increase application memory
mvn spring-boot:run -Dspring.profiles.active=local -Xmx1g
```

## Development Best Practices

### Configuration Management

- Use `application-local.properties` for local development
- Never commit real credentials to version control
- Use environment variables for sensitive configuration
- Document required configuration for each environment

### Testing Strategy

- Write unit tests for business logic
- Use integration tests for browser automation
- Mock external services in tests
- Test with different browser configurations

### Code Style

- Follow existing code conventions
- Use Lombok annotations for boilerplate code
- Add JavaDoc for public APIs
- Keep methods focused and testable
- Use meaningful variable and method names

### Git Workflow

- Create feature branches for new development
- Keep commits focused and well-described
- Test locally before pushing
- Use meaningful commit messages
- Rebase feature branches before merging

### Logging and Debugging

- Use correlation IDs for request tracing
- Log at appropriate levels (DEBUG, INFO, WARN, ERROR)
- Include context in error messages
- Use structured logging where possible

### Performance Considerations

- Profile code to identify bottlenecks
- Use caching where appropriate
- Minimize browser automation overhead
- Optimize database queries
- Monitor memory usage

## Common Development Patterns

### Adding Custom Data Writers

1. Implement `IDataWriter` interface
2. Extend `RecordedJobDataBaseWriter` for common functionality
3. Use `DataWriterCachingDecorator` for performance optimization
4. Register in the data writer factory

```java
public class CustomDataWriter implements IDataWriter {
    @Override
    public String getIdentifier() {
        return "custom-data";
    }
    
    @Override
    public boolean writeData(OutputStream os) throws IOException {
        // Custom data writing logic
        return true;
    }
}
```

### Adding Proxy Providers

1. Extend `HttpProxy` class
2. Implement provider-specific authentication and session handling
3. Add conversion logic in `PrimemoverApiConverter.convertApiProxy()`
4. Configure provider-specific settings in application properties

### Extending Browser Support

1. Create new agent class implementing `IAgent`
2. Add browser-specific capabilities and options handling
3. Implement CAPTCHA solver integration if needed
4. Update the agent conversion logic in the API converter

### Custom Context Decorators

```java
public class CustomContextDecorator implements IJobAwareRunnerContext {
    private final IJobAwareRunnerContext delegate;
    
    public CustomContextDecorator(IJobAwareRunnerContext delegate) {
        this.delegate = delegate;
    }
    
    // Add custom functionality while delegating to original context
    @Override
    public WebDriver getDriver() {
        // Add custom logic before/after
        return delegate.getDriver();
    }
}
```

## Testing Guidelines

### Unit Testing

- Test business logic in isolation
- Mock external dependencies
- Use parameterized tests for multiple scenarios
- Test error conditions and edge cases

### Integration Testing

- Test complete workflows end-to-end
- Use test profiles with minimal configuration
- Clean up resources after tests
- Use test containers for external dependencies

### Browser Testing

- Test with multiple browser types
- Use headless mode for CI/CD
- Test responsive design scenarios
- Verify cross-browser compatibility

## Debugging Tips

### Browser Automation Issues

1. Run with visible browser to see what's happening
2. Add breakpoints in job execution code
3. Check browser console for JavaScript errors
4. Verify element selectors are correct
5. Use browser developer tools to inspect elements

### API Integration Issues

1. Enable HTTP request/response logging
2. Check API endpoint URLs and credentials
3. Verify request/response formats
4. Test API endpoints manually with curl or Postman
5. Check for network connectivity issues

### Performance Issues

1. Profile application with JProfiler or similar tools
2. Monitor memory usage and garbage collection
3. Check database query performance
4. Analyze browser automation timing
5. Review logging overhead

### Configuration Issues

1. Enable configuration debug logging
2. Verify property file locations and syntax
3. Check environment variable overrides
4. Validate required properties are set
5. Test with minimal configuration first
