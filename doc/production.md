# Production Deployment Guide

This guide covers deploying PrimeMover in production environments, including scaling, monitoring, and operational considerations.

## Production Configuration

### Environment-Specific Settings

```properties
# Production configuration
spring.profiles.active=production
global.mode=PRODUCTION

# Security settings
server.port=8080
server.ssl.enabled=false  # Use reverse proxy for SSL termination

# Enable all production services
anticaptcha.enabled=true
multilogin.enabled=true
recorder.enabled=true

# Production execution settings
runner.MaxParallelExecution=10
runner.MaxRedoMinutes=45
runner.StartupRetryCount=3
runner.ProcessingTimeoutMinutes=180

# Retry configuration for resilience
spring.retry.max-attempts=3
spring.retry.backoff.delay=1000
spring.retry.backoff.multiplier=2

# Production logging
logging.level.root=WARN
logging.level.primemover=INFO
logging.level.primemover.runner.joblist=DEBUG
logging.file.name=log/primemover-runner.log
logging.config=config/logback-spring.xml

# Actuator endpoints for monitoring
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized
management.metrics.export.prometheus.enabled=true
```

### Security Configuration

#### Credential Management
```properties
# Use environment variables for sensitive data
backend.username=${BACKEND_USERNAME}
backend.password=${BACKEND_PASSWORD}
anticaptcha.api.key=${ANTICAPTCHA_API_KEY}
multilogin.apiUser=${MULTILOGIN_USERNAME}
multilogin.apiPassword=${MULTILOGIN_PASSWORD}
awsS3.accessKey=${AWS_ACCESS_KEY}
awsS3.secretKey=${AWS_SECRET_KEY}
```

#### Network Security
```properties
# Proxy authentication
geosurf.overwrite.user=${GEOSURF_USERNAME}
geosurf.overwrite.password=${GEOSURF_PASSWORD}

# SSL/TLS configuration for external services
anticaptcha.api.endpoint=https://api.anti-captcha.com
backend.endpoint.api=https://secure-api-endpoint/api/v1/
backend.connectionTimeoutSeconds=30
backend.requestTimeoutSeconds=120
```

#### Data Protection
```properties
# Secure data storage
export.FileTargetFolder=/secure/data/har
export.Compression=ZIP

# Recording proxy with certificates
recorder.enabled=true
recorder.certificate.type=PKCS12
recorder.certificate.path=/secure/certs/ca-cert.p12
recorder.certificate.alias=Primemover
recorder.certificate.password=${RECORDER_CERT_PASSWORD}
```

## Scaling and Performance

### Horizontal Scaling

#### Multiple Instance Deployment
```yaml
# Docker Compose example for multiple instances
version: '3.8'
services:
  primemover-1:
    image: primemover:latest
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - INSTANCE_ID=1
      - RUNNER_MAXPARALLELEXECUTION=5
    volumes:
      - ./config:/app/config
      - ./logs:/app/logs
      - ./data:/app/data

  primemover-2:
    image: primemover:latest
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - INSTANCE_ID=2
      - RUNNER_MAXPARALLELEXECUTION=5
    volumes:
      - ./config:/app/config
      - ./logs:/app/logs
      - ./data:/app/data

  load-balancer:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
```

#### Load Balancing Configuration
```nginx
# nginx.conf for load balancing
upstream primemover_backend {
    server primemover-1:8080;
    server primemover-2:8080;
    
    # Health checks
    keepalive 32;
}

server {
    listen 80;
    
    location / {
        proxy_pass http://primemover_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # Timeouts for long-running jobs
        proxy_connect_timeout 30s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
    
    location /actuator/health {
        proxy_pass http://primemover_backend;
        access_log off;
    }
}
```

### Performance Optimization

#### JVM Configuration
```bash
# Production JVM settings
JAVA_OPTS="-Xmx4g -Xms2g -XX:MaxMetaspaceSize=512m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:G1HeapRegionSize=16m"
JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="$JAVA_OPTS -XX:HeapDumpPath=/var/log/primemover/"
```

#### Connection Pooling
```properties
# HTTP client configuration
spring.http.client.connection-timeout=10000
spring.http.client.read-timeout=30000
spring.http.client.max-connections=100
spring.http.client.max-connections-per-route=20
```

#### Thread Pool Configuration
```properties
# Execution thread pool settings
runner.MaxParallelExecution=10
runner.thread-pool.core-size=5
runner.thread-pool.max-size=20
runner.thread-pool.queue-capacity=200
```

## Monitoring and Observability

### Key Metrics to Track

#### Application Metrics
- **Job Success Rate**: Percentage of successful job completions
- **Average Execution Time**: Performance trends over time
- **CAPTCHA Solve Rate**: Effectiveness of anti-CAPTCHA measures
- **Resource Utilization**: CPU, memory, and network usage
- **Error Rates**: Frequency and types of failures

#### System Metrics
- **Memory Usage**: Heap and non-heap memory consumption
- **Thread Pool Status**: Active threads and queue depth
- **HTTP Response Times**: API endpoint performance
- **Browser Performance**: Page load times and navigation metrics

### Monitoring Configuration

#### Prometheus Integration
```properties
# Enable Prometheus metrics
management.metrics.export.prometheus.enabled=true
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.sla.http.server.requests=50ms,100ms,200ms,500ms
```

#### Health Checks
```properties
# Detailed health checks
management.endpoint.health.show-details=when-authorized
management.health.diskspace.enabled=true
management.health.diskspace.threshold=1GB
```

### Alerting and Notifications

#### Critical Alerts
- **Failed Jobs**: Immediate alerts for job failures exceeding threshold
- **System Health**: Monitoring for service degradation
- **Resource Exhaustion**: Alerts for high CPU/memory usage
- **External Service Issues**: Monitoring for MultiLogin/AntiCaptcha problems

#### Alert Configuration Example
```yaml
# Prometheus alerting rules
groups:
  - name: primemover
    rules:
      - alert: HighJobFailureRate
        expr: rate(primemover_jobs_failed_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High job failure rate detected"
          
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.8
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High memory usage detected"
```

## Deployment Strategies

### Blue-Green Deployment

#### Setup
```bash
# Deploy new version to green environment
docker-compose -f docker-compose.green.yml up -d

# Health check green environment
curl http://green-primemover:8080/actuator/health

# Switch traffic to green
# Update load balancer configuration

# Shutdown blue environment after verification
docker-compose -f docker-compose.blue.yml down
```

### Rolling Updates

#### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: primemover
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  template:
    spec:
      containers:
      - name: primemover
        image: primemover:latest
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

## Backup and Recovery

### Data Backup Strategy

#### Configuration Backup
```bash
# Backup configuration files
tar -czf config-backup-$(date +%Y%m%d).tar.gz \
  primemover-runner/config/ \
  certificates/ \
  logs/

# Store in secure location
aws s3 cp config-backup-$(date +%Y%m%d).tar.gz \
  s3://primemover-backups/config/
```

#### Job Data Backup
```bash
# Backup job execution data
rsync -av --delete /var/primemover/data/ \
  backup-server:/backups/primemover/data/

# Backup logs
rsync -av --delete /var/primemover/logs/ \
  backup-server:/backups/primemover/logs/
```

### Disaster Recovery

#### Recovery Procedures
1. **Service Restoration**: Deploy from known good configuration
2. **Data Recovery**: Restore from latest backup
3. **Verification**: Run health checks and test jobs
4. **Monitoring**: Ensure all metrics return to normal

#### Recovery Testing
```bash
# Test recovery procedure monthly
./scripts/disaster-recovery-test.sh

# Verify all services are operational
curl http://localhost:8080/actuator/health
```

## Security Considerations

### Network Security

#### Firewall Configuration
```bash
# Allow only necessary ports
ufw allow 8080/tcp  # Application port
ufw allow 22/tcp    # SSH
ufw deny incoming
ufw enable
```

#### SSL/TLS Configuration
```nginx
# SSL termination at load balancer
server {
    listen 443 ssl http2;
    ssl_certificate /etc/ssl/certs/primemover.crt;
    ssl_certificate_key /etc/ssl/private/primemover.key;
    
    # Modern SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    ssl_prefer_server_ciphers off;
}
```

### Access Control

#### Authentication
```properties
# Production authentication
management.security.enabled=true
management.endpoints.web.base-path=/actuator
management.endpoints.web.exposure.include=health,info,metrics
spring.security.user.name=${ADMIN_USERNAME}
spring.security.user.password=${ADMIN_PASSWORD}
```

#### Audit Logging
```properties
# Enable audit logging
logging.level.org.springframework.security=DEBUG
logging.level.primemover.security=INFO
```

## Troubleshooting Production Issues

### Common Issues

#### High Memory Usage
```bash
# Generate heap dump
jcmd <pid> GC.run_finalization
jcmd <pid> VM.gc
jmap -dump:format=b,file=heapdump.hprof <pid>

# Analyze with tools like Eclipse MAT
```

#### Job Execution Failures
```bash
# Check job logs
tail -f /var/log/primemover/primemover-runner.log | grep ERROR

# Check correlation IDs for specific job
grep "correlationId=cr-123|jl-456" /var/log/primemover/primemover-runner.log
```

#### External Service Issues
```bash
# Test MultiLogin connectivity
curl -u username:password http://localhost:35000/api/v1/profile/list

# Test AntiCaptcha API
curl -H "Content-Type: application/json" \
  -d '{"clientKey":"your-key"}' \
  https://api.anti-captcha.com/getBalance
```

### Performance Tuning

#### Database Optimization
```properties
# Connection pool tuning (if using database)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

#### Browser Optimization
```properties
# Browser performance settings
webdriver.chrome.args=--no-sandbox,--disable-dev-shm-usage,--disable-gpu
webdriver.firefox.args=-headless
```

## Maintenance Procedures

### Regular Maintenance

#### Log Rotation
```bash
# Configure logrotate
cat > /etc/logrotate.d/primemover << EOF
/var/log/primemover/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
}
EOF
```

#### Certificate Renewal
```bash
# Automated certificate renewal
0 2 * * * /usr/bin/certbot renew --quiet && systemctl reload nginx
```

### Capacity Planning

#### Scaling Indicators
- CPU usage consistently above 70%
- Memory usage above 80%
- Job queue depth increasing
- Response times degrading

#### Scaling Actions
```bash
# Horizontal scaling
docker-compose scale primemover=5

# Vertical scaling
# Update resource limits in deployment configuration
```

This production deployment guide provides comprehensive coverage for running PrimeMover in enterprise environments with proper monitoring, security, and operational procedures.
