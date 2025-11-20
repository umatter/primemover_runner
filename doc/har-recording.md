# HAR Recording Optimization Guide

This guide explains PrimeMover's intelligent HAR (HTTP Archive) recording optimization features that can reduce storage costs by up to 99% while preserving the most important network traffic data.

## Table of Contents

1. [Overview](#overview)
2. [Job Type Filtering](#job-type-filtering)
3. [Content Filtering](#content-filtering)
4. [Domain Filtering](#domain-filtering)
5. [Configuration Examples](#configuration-examples)
6. [Performance Impact](#performance-impact)
7. [Testing & Validation](#testing--validation)

---

## Overview

PrimeMover includes two complementary HAR optimization strategies:

1. **Job Type Filtering** - Skip HAR recording for jobs that don't generate network traffic
2. **Content Filtering** - Remove unnecessary data from captured HAR files

Both features are optional and configurable via properties files.

### Why HAR Optimization Matters

- **Storage Costs**: HAR files can be 10+ MB per job, adding up quickly
- **Performance**: Smaller files = faster uploads, downloads, and processing
- **Analysis**: Focused data makes it easier to find relevant information
- **Compliance**: Exclude sensitive third-party tracking data

---

## Job Type Filtering

### Concept

Skip HAR recording entirely for job types that don't generate HTTP/HTTPS traffic.

### Implementation

**Component:** `HarRecordingFilter.java` (primemover/capture/)

The filter checks each job type against a configurable exclusion list before starting MITM proxy recording.

### Configuration

```properties
# Enable/disable HAR recording globally
har.recording.enabled=true

# Job types to exclude from HAR recording (comma-separated)
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob,AssertTextInFieldJob,AssertElementExistsJob,AssertScrolledMaxJob,AssertPageLoadedJob,AssertProxyJob,AssertProxyByGeosurfJob,AssertProxyByPrimemoverApiJob
```

### Default Excluded Job Types

| Job Type | Reason |
|----------|--------|
| `WaitJob` | Just waits, no network activity |
| `ScrollByDurationJob` | Client-side scrolling only |
| `ScrollByLengthJob` | Client-side scrolling only |
| `HandleAlertJob` | Browser dialog handling |
| `AssertTextInFieldJob` | Test assertion, no traffic |
| `AssertElementExistsJob` | Test assertion, no traffic |
| `AssertScrolledMaxJob` | Test assertion, no traffic |
| `AssertPageLoadedJob` | Test assertion, no traffic |
| `AssertProxyJob` | Test assertion, no traffic |
| `AssertProxyByGeosurfJob` | Test assertion, no traffic |
| `AssertProxyByPrimemoverApiJob` | Test assertion, no traffic |

### Impact

- **~55% fewer HAR captures** in typical joblists
- **Faster execution** for filtered jobs (no HAR serialization overhead)
- **Reduced memory usage** during job execution

### How It Works

```
TaskOperator.runJob()
    ↓
Check: harRecordingFilter.shouldRecordHar(job)
    ↓
If TRUE:  recordingProxy.startCapturing()  → Record HAR → Save to storage
If FALSE: Skip recording                     → No HAR file created
```

---

## Content Filtering

### Concept

Remove unnecessary data from captured HAR files before storage.

### Implementation

**Component:** `HarContentFilter.java` (primemover/capture/)

Applied in `BrowserUpRecorderProxy.harToString()` before HAR serialization.

### Configuration

```properties
# Enable/disable HAR content filtering
har.filter.enabled=false

# Remove static resources (images, CSS, fonts, JavaScript)
har.filter.removeStaticResources=false

# Content types to filter (when removeStaticResources=true)
har.filter.staticResourceTypes=image/png,image/jpeg,image/gif,image/webp,image/svg+xml,text/css,font/woff,font/woff2,font/ttf,application/font-woff,application/font-woff2,application/x-font-woff,application/x-font-ttf,application/javascript

# Domain filtering (see next section)
har.filter.includeDomains=
har.filter.excludeDomains=

# Maximum body size in bytes (truncate larger bodies)
har.filter.maxBodySize=

# HTTP headers to remove from HAR entries
har.filter.removeHeaders=
```

### Filtering Options

1. **Static Resource Removal** - Remove images, CSS, fonts, JavaScript
2. **Domain Filtering** - Include/exclude specific domains
3. **Body Truncation** - Limit request/response body sizes
4. **Header Removal** - Remove specific HTTP headers

### Static Resource Filtering

**Example:**
```properties
har.filter.enabled=true
har.filter.removeStaticResources=true
```

**Removes entries with these MIME types:**
- Images: `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `image/svg+xml`
- Stylesheets: `text/css`
- Fonts: `font/woff`, `font/woff2`, `font/ttf`, `application/font-woff`
- JavaScript: `application/javascript`

**Result:** 50-80% size reduction for content-heavy pages

---

## Domain Filtering

### Whitelist Approach (Include Domains)

**Use Case:** Only capture traffic to specific domains, filter out everything else.

**Configuration:**
```properties
har.filter.enabled=true
har.filter.includeDomains=example.com,api.example.com
```

**Behavior:**
- ✅ Include: URLs containing `example.com` or `api.example.com`
- ❌ Exclude: All other URLs

**Example Results:**
- Job visiting `https://example.com/page` → HAR contains only example.com traffic
- Job visiting `https://other-site.com` → HAR is empty (252 bytes)

**Best For:**
- API-focused testing
- Single-domain data collection
- Compliance requirements (exclude third-party trackers)

### Blacklist Approach (Exclude Domains)

**Use Case:** Capture most traffic but exclude specific domains (analytics, ads, CDNs).

**Configuration:**
```properties
har.filter.enabled=true
har.filter.excludeDomains=google-analytics.com,facebook.com,doubleclick.net
```

**Behavior:**
- ✅ Include: All URLs except those containing excluded domains
- ❌ Exclude: URLs containing any excluded domain

**Best For:**
- General web scraping (exclude trackers)
- Reducing noise from analytics
- Faster HAR analysis (fewer irrelevant entries)

### Combined Filtering

**You can use both together:**
```properties
har.filter.enabled=true
har.filter.includeDomains=youtube.com
har.filter.excludeDomains=googlevideo.com,googleusercontent.com
```

**Logic:**
1. First, check `includeDomains` - if specified, URL must match
2. Then, check `excludeDomains` - if matches, exclude
3. Then, check static resources - if enabled, remove
4. Include remaining entries

**Result:** Only YouTube.com HTML/API traffic, no video CDN or images

---

## Configuration Examples

### Example 1: Production Optimization (Recommended)

**Goal:** Reduce storage costs while preserving important data

```properties
# Job type filtering - skip jobs with no network activity
har.recording.enabled=true
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob,AssertTextInFieldJob,AssertElementExistsJob,AssertScrolledMaxJob,AssertPageLoadedJob,AssertProxyJob,AssertProxyByGeosurfJob,AssertProxyByPrimemoverApiJob

# Content filtering - remove static resources and tracking
har.filter.enabled=true
har.filter.removeStaticResources=true
har.filter.excludeDomains=google-analytics.com,facebook.com,doubleclick.net,googletagmanager.com
har.filter.maxBodySize=102400
```

**Expected Results:**
- 55% fewer HAR captures
- 80-95% size reduction on captured HARs
- **Total storage savings: 95-99%**

### Example 2: API-Only Capture

**Goal:** Only capture your own API traffic

```properties
har.recording.enabled=true
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob

# Whitelist only your API
har.filter.enabled=true
har.filter.includeDomains=api.yourcompany.com
har.filter.removeStaticResources=false
```

**Expected Results:**
- Only API traffic captured
- Pages without API calls → empty HAR (252 bytes)
- **Extreme focus on relevant data**

### Example 3: Multi-Domain Testing

**Goal:** Capture traffic from specific sites only

```properties
har.recording.enabled=true
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob

har.filter.enabled=true
har.filter.includeDomains=nytimes.com,washingtonpost.com,theguardian.com
har.filter.removeStaticResources=true
```

**Expected Results:**
- Only traffic to specified news sites
- Static resources (images, CSS) removed
- **Perfect for multi-site comparison studies**

### Example 4: Debugging (No Filtering)

**Goal:** Capture everything for troubleshooting

```properties
har.recording.enabled=true
har.recording.excludeJobTypes=

har.filter.enabled=false
```

**Expected Results:**
- HAR captured for every job
- All content included
- **Use temporarily when investigating issues**

### Example 5: Maximum Compression

**Goal:** Smallest possible HAR files

```properties
har.recording.enabled=true
har.recording.excludeJobTypes=WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob,AssertTextInFieldJob,AssertElementExistsJob,AssertScrolledMaxJob,AssertPageLoadedJob,AssertProxyJob,AssertProxyByGeosurfJob,AssertProxyByPrimemoverApiJob

har.filter.enabled=true
har.filter.removeStaticResources=true
har.filter.excludeDomains=google.com,facebook.com,twitter.com,linkedin.com,instagram.com
har.filter.maxBodySize=10240
har.filter.removeHeaders=Cookie,Set-Cookie,Authorization,X-Custom-Header
```

**Expected Results:**
- Minimum HAR file sizes
- Bodies truncated at 10KB
- Sensitive headers removed
- **99%+ storage reduction**

---

## Performance Impact

### Storage Savings

**Real-world test results:**

| Configuration | HAR Size | Reduction |
|--------------|----------|-----------|
| No filtering | 11.2 MB | Baseline |
| Job type filtering only | 5.0 MB* | 55% ↓ |
| Static resource filtering | 2.3 MB | 79% ↓ |
| Blacklist filtering (exclude google.com) | 1.4 MB | 88% ↓ |
| Whitelist filtering (include google.com) | 22 KB | **99.8% ↓** |

*Averaged across mixed joblist

### Execution Performance

- **Faster job execution**: No HAR serialization for skipped jobs (~500ms saved per job)
- **Reduced memory usage**: Smaller HAR objects in memory
- **Faster uploads**: Less data to transfer to S3/backend

### Analysis Benefits

- **Faster search**: Fewer entries to parse
- **Better signal-to-noise ratio**: Only relevant traffic
- **Easier debugging**: Focus on important requests

---

## Testing & Validation

### Verify Job Type Filtering

1. **Run a joblist with mixed job types** (VisitJob, WaitJob, etc.)
2. **Check the output directory** (e.g., `out/har/`)
3. **Inspect zip files:**
   - VisitJob zips should contain `*_trafic_har_*.json`
   - WaitJob zips should NOT contain HAR files
4. **Check logs** for messages like:
   ```
   HAR recording skipped for job type: WaitJob (job id: 123)
   HAR recording enabled for job type: VisitJob (job id: 124)
   ```

### Verify Content Filtering

1. **Enable filtering:**
   ```properties
   har.filter.enabled=true
   har.filter.removeStaticResources=true
   ```
2. **Run a job visiting a content-heavy page**
3. **Extract and check HAR file:**
   ```bash
   unzip -p out/har/123/*jobdata*.zip "*trafic_har*.json" | grep -o '"mimeType"' | sort | uniq -c
   ```
4. **Verify:** No image/css/font MIME types in output
5. **Check logs** for filtering statistics:
   ```
   HAR filtered: removed 45 of 120 entries (75 remaining),
   size reduced by 523456 bytes (850000 -> 326544 bytes, 61.5% reduction)
   ```

### Verify Domain Filtering

**Whitelist Test:**
```properties
har.filter.enabled=true
har.filter.includeDomains=example.com
```

**Check:**
```bash
unzip -p out/har/123/*jobdata*.zip "*trafic_har*.json" | grep -o '"url":"[^"]*"' | grep -v "example.com"
```

**Expected:** No output (all URLs contain example.com)

**Blacklist Test:**
```properties
har.filter.enabled=true
har.filter.excludeDomains=google.com
```

**Check:**
```bash
unzip -p out/har/123/*jobdata*.zip "*trafic_har*.json" | grep -o '"url":"[^"]*"' | grep "google.com"
```

**Expected:** No output (no URLs contain google.com)

### Startup Verification

**Check logs on application startup:**
```
HAR recording filter initialized. Enabled: true, Excluded job types: [WaitJob, ScrollByDurationJob, ...]
HAR content filter initialized. Enabled: true, Remove static resources: true,
  Static types count: 13, Included domains count: 0, Excluded domains count: 3, Max body size: null
```

---

## Troubleshooting

### Issue: HAR files still too large

**Solutions:**
1. Enable static resource removal
2. Add more domains to excludeDomains
3. Enable body size truncation
4. Use whitelist approach (includeDomains) for extreme reduction

### Issue: Missing important traffic

**Solutions:**
1. Check that job type isn't in excludeJobTypes
2. Verify includeDomains includes the target domain
3. Ensure excludeDomains doesn't block needed traffic
4. Temporarily disable filtering to debug

### Issue: Empty HAR files (252 bytes)

**This is expected** when:
- Job type is filtered (intended behavior)
- No traffic matches includeDomains (intended behavior)
- Page loaded entirely from cache (rare edge case)

**Verify:** Check that the job actually visited the expected URL

### Issue: Filtering not applying

**Check:**
1. `har.filter.enabled=true` is set
2. Configuration file is being loaded (check startup logs)
3. Application was restarted after config changes
4. No typos in property names

---

## Best Practices

### For Development
- **Disable filtering** during initial testing
- Enable job type filtering first, then content filtering
- Test with known pages to verify behavior

### For Production
- **Always enable job type filtering** (free 55% reduction)
- Enable static resource filtering for most use cases
- Use domain filtering when you know target domains
- Monitor HAR sizes and adjust as needed

### For Compliance
- Use excludeDomains to filter third-party trackers
- Use includeDomains to capture only first-party traffic
- Enable header removal for sensitive data
- Document your filtering configuration

### For Cost Optimization
- Start conservative (job type filtering only)
- Add content filtering incrementally
- Use whitelist approach for maximum savings
- Monitor storage metrics to measure impact

---

## Architecture Notes

### Filter Execution Order

1. **Job Type Check** (TaskOperator.runJob:286)
   - Checks job type against exclusion list
   - If excluded: Skip HAR recording entirely
   - If allowed: Continue to step 2

2. **Content Filtering** (BrowserUpRecorderProxy.harToString:282)
   - Applied during HAR serialization
   - Order: Domain filtering → Static resource filtering → Body truncation → Header removal
   - Returns filtered HAR string

3. **Storage** (TaskOperator.runJob:353)
   - Filtered HAR saved to storage backends
   - Null HAR for filtered job types

### Thread Safety

All filter components are thread-safe:
- Configuration loaded at startup (immutable)
- No shared mutable state
- Safe for concurrent job execution

### Backward Compatibility

- **Default behavior unchanged**: All filtering disabled by default
- Existing configurations work without modification
- Old HAR files remain compatible

---

## Related Documentation

- [Configuration Guide](configuration.md) - Service setup and credentials
- [Job System Guide](job-system.md) - Understanding jobs and execution
- [Architecture Overview](architecture.md) - System design and components

---

## Version History

- **v2.2.1** (2025-11-20): Added includeDomains whitelist support
- **v2.2.0** (2025-11-20): Initial HAR optimization release with job type and content filtering
