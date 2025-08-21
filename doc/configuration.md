# Configuration Guide

This guide covers how to configure PrimeMover for different environments and external services.

## Configuration Overview

PrimeMover loads configuration from Spring Boot property files. The main configuration file is:

**Primary Configuration File**: `primemover-runner/config/application.properties`

## Required Service Credentials

Before running PrimeMover, you must configure credentials for the external services it integrates with.

### Essential Services

#### PrimeMover API Backend
Main API for job management and configuration:
- API endpoint URLs
- Username and password for authentication

```properties
backend.endpoint.api=https://your-api-endpoint/api/v1/
backend.endpoint.runner=https://your-api-endpoint/api/v1/runner/
backend.username=your-username
backend.password=your-password
backend.connectionTimeoutSeconds=10
backend.requestTimeoutSeconds=90
```

### Browser Management Services

#### MultiLogin (Optional)
Advanced browser profile management with fingerprinting:

```properties
multilogin.enabled=true
multilogin.api.port=35000
multilogin.apiUser=your-multilogin-username
multilogin.apiPassword=your-multilogin-password
multilogin.defaultFolderId=your-folder-id
multilogin.StartupRetryCount=3
multilogin.StartupDelaySeconds=10
multilogin.ConnectTimeoutSeconds=30
```

### Proxy Services (Optional)

#### GeoSurf
Geographic proxy service:

```properties
geosurf.overwrite.user=your-geosurf-username
geosurf.overwrite.password=your-geosurf-password
```

#### Oxylabs
Enterprise proxy provider:

```properties
oxylabs.username=your-oxylabs-username
oxylabs.password=your-oxylabs-password
```

#### Brightdata
Large-scale proxy network:

```properties
brightdata.username=your-brightdata-username
brightdata.password=your-brightdata-password
```

### Anti-Detection Services (Optional)

#### AntiCaptcha
Automated CAPTCHA solving service:

```properties
anticaptcha.enabled=true
anticaptcha.defaultMode=BEFORE
anticaptcha.api.key=your-anticaptcha-api-key
anticaptcha.api.endpoint=https://api.anti-captcha.com
anticaptcha.extension.firefox=anticaptcha/anticaptcha-plugin_v0.60.xpi
anticaptcha.extension.chrome=anticaptcha/anticaptcha-plugin_v0.67.crx
```

### Cloud Storage (Optional)

#### AWS S3
For storing captured data and job results:

```properties
awsS3.accessKey=your-aws-access-key
awsS3.secretKey=your-aws-secret-key
awsS3.jobdata.bucketName=your-bucket-name
awsS3.jobdata.subFolder=reports/
awsS3.finalsource.bucketName=your-bucket-name
awsS3.finalsource.subFolder=finalsource/
```

## Environment-Specific Configuration

### Development Configuration

Create `application-local.properties` for local development:

```properties
# Basic settings
spring.profiles.active=local
global.mode=DEVELOP

# Disable external services for local development
anticaptcha.enabled=false
multilogin.enabled=false
recorder.enabled=false

# Proxy settings (disabled for offline development)
proxy.pingConnections=false
proxy.verifyConnections=false

# Execution settings for development
runner.MaxParallelExecution=1
runner.MaxRedoMinutes=5  # Shorter timeout for development
runner.StartupRetryCount=1

# Development logging
logging.level.primemover=DEBUG
logging.level.primemover.runner=DEBUG
logging.level.primemover.api.converter=DEBUG
```

### Production Configuration

```properties
# Production settings
spring.profiles.active=production
global.mode=PRODUCTION

# Enable all services
anticaptcha.enabled=true
multilogin.enabled=true
recorder.enabled=true

# Production execution settings
runner.MaxParallelExecution=5
runner.MaxRedoMinutes=45
runner.StartupRetryCount=3
runner.ProcessingTimeoutMinutes=180

# Production logging
logging.level.primemover=INFO
logging.level.primemover.runner.joblist=DEBUG
logging.file.name=log/primemover-runner.log
logging.config=config/logback-spring.xml
```

### Testing Configuration

```properties
# Test settings
spring.profiles.active=test
global.mode=DEVELOP

# Disable external services for testing
anticaptcha.enabled=false
multilogin.enabled=false
recorder.enabled=false

# Fast execution for tests
runner.MaxParallelExecution=1
runner.MaxRedoMinutes=1
runner.StartupRetryCount=1

# Test logging
logging.level.primemover=WARN
logging.level.primemover.runner=INFO
```

## Application Settings

### Core Application Configuration

```properties
# Application identity
spring.application.name=primemover-runner

# Server configuration
server.port=8080

# Actuator endpoints for monitoring
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized
```

### Job Execution Settings

```properties
# Parallel execution control
runner.MaxParallelExecution=2
runner.UpdateInterval=10
runner.MaxRedoMinutes=45
runner.StartupRetryCount=3
runner.ProcessingTimeoutMinutes=180

# Retry configuration
spring.retry.max-attempts=3
spring.retry.backoff.delay=1000
spring.retry.backoff.multiplier=2
```

### Data Export Settings

```properties
# File export configuration
export.FileTargetFolder=out/har
export.Compression=ZIP

# Recording proxy settings
recorder.enabled=false
recorder.certificate.type=PKCS12
recorder.certificate.path=cert/2024/ca-cert.p12
recorder.certificate.alias=Primemover
recorder.certificate.password=CHANGEME-password
```

## Configuration Management Best Practices

### Security
- Never commit real credentials to version control
- Use environment variables for sensitive configuration
- Rotate API keys and passwords regularly
- Use encrypted configuration for production

### Environment Separation
- Use Spring profiles for different environments
- Create separate configuration files for each environment
- Override sensitive settings with environment variables
- Document required configuration for each environment

### Validation
- Validate configuration on application startup
- Provide meaningful error messages for missing configuration
- Use default values where appropriate
- Test configuration changes in non-production environments

## Environment Variables

You can override any property using environment variables. Spring Boot automatically maps environment variables to properties:

```bash
# Override database settings
export BACKEND_USERNAME=production-user
export BACKEND_PASSWORD=secure-password

# Override API keys
export ANTICAPTCHA_API_KEY=your-production-key
export MULTILOGIN_APIPASSWORD=your-multilogin-password

# Override execution settings
export RUNNER_MAXPARALLELEXECUTION=10
export RUNNER_MAXREDOMINUTES=60
```

## Configuration Validation

PrimeMover validates configuration on startup and provides helpful error messages:

```java
// Example validation in ContextBase
public boolean isValid() {
    if (driver == null) {
        logger.error("WebDriver is required but not configured");
        return false;
    }
    
    if (captchaSolver == null && anticaptchaEnabled) {
        logger.error("CAPTCHA solver is required when anticaptcha.enabled=true");
        return false;
    }
    
    return true;
}
```

## Troubleshooting Configuration

### Common Issues

1. **Missing API Keys**: Check that all required API keys are configured
2. **Network Connectivity**: Verify that external services are reachable
3. **Proxy Configuration**: Ensure proxy settings are correct for your network
4. **File Permissions**: Check that log and data directories are writable
5. **Port Conflicts**: Ensure configured ports are available

### Debug Configuration

Enable debug logging to troubleshoot configuration issues:

```properties
# Enable configuration debug logging
logging.level.org.springframework.boot.context.config=DEBUG
logging.level.org.springframework.core.env=DEBUG

# Enable web client debug logging
logging.level.org.springframework.web.client.RestTemplate=DEBUG
```

### Configuration Testing

Test your configuration with a minimal setup:

```bash
# Test with minimal configuration
mvn spring-boot:run -Dspring.profiles.active=test

# Test specific configuration
mvn spring-boot:run -Dspring.profiles.active=local \
  -Danticaptcha.enabled=false \
  -Dmultilogin.enabled=false
```
