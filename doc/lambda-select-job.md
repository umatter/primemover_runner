# Lambda Select Job Documentation

## Overview

The Lambda Select Job (`LambdaSelectJob`) is a specialized job type in PrimeMover that enables complex element selection logic by offloading the selection process to AWS Lambda functions. Instead of using simple CSS or XPath selectors, this job type allows for sophisticated, programmable element selection using external serverless functions.

## Architecture

### Core Components

1. **LambdaSelectJob** - The job definition containing Lambda function details
2. **LambdaSelectJobParam** - Parameters sent to the Lambda function
3. **LambdaSelectJobResult** - Response from the Lambda function
4. **LambdaCaller** - Service that invokes AWS Lambda functions

### Data Flow

```
Web Page → Job Execution → Lambda Function → XPath Result → Element Selection
```

1. **Job Creation**: API converter creates `LambdaSelectJob` from API request
2. **Parameter Assembly**: System creates `LambdaSelectJobParam` with job, crawler, page source, and raw configuration
3. **Lambda Invocation**: `LambdaCaller` sends JSON payload to specified Lambda function
4. **Result Processing**: Lambda returns `LambdaSelectJobResult` with XPath selector
5. **Element Selection**: System uses returned XPath to locate and interact with page elements

## Job Configuration

### API Job Definition

To create a Lambda select job via API, use the following structure:

```json
{
  "name": "Lambda Element Selection",
  "type": "lambdaselectjob",
  "description": "Use Lambda for intelligent element selection",
  "active": true,
  "behaviors": [
    {
      "name": "captchaMode",
      "value": "never"
    },
    {
      "name": "linkType",
      "value": "FUNCTION_NAME"
    },
    {
      "name": "linkValue",
      "value": "my-lambda-function-name"
    },
    {
      "name": "username",
      "value": "optional-auth-user"
    },
    {
      "name": "password",
      "value": "optional-auth-password"
    },
    {
      "name": "task",
      "value": "Select best product recommendation"
    }
  ]
}
```

### Behavior Parameters

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `linkType` | No | Lambda invocation type | `FUNCTION_NAME` (default) |
| `linkValue` | Yes | Lambda function name or URL | `element-selector-v1` |
| `username` | No | Authentication username | `api-user` |
| `password` | No | Authentication password | `secret-key` |
| `task` | No | Human-readable task description | `Select product with highest rating` |
| `captchaMode` | No | CAPTCHA handling mode | `never`, `always`, `before`, `after`, `random` |

## Lambda Function Interface

### Input Payload Structure

Your Lambda function receives a JSON payload with the following structure:

```json
{
  "job": {
    "id": 123,
    "name": "Lambda Element Selection",
    "description": "Use Lambda for intelligent element selection",
    "task": "Select best product recommendation",
    "linkType": "FUNCTION_NAME",
    "linkValue": "my-lambda-function-name",
    "username": "optional-auth-user",
    "password": "optional-auth-password",
    "captchaSolving": "NEVER"
  },
  "crawler": {
    "id": 456,
    "name": "Product Crawler",
    "description": "Crawls e-commerce sites",
    "agent": {
      "type": "CHROME",
      "id": 789,
      "name": "Chrome Agent Denver",
      "description": "Chrome browser agent",
      "location": "US-CO-DENVER"
    },
    "proxy": {
      "url": "proxy.example.com",
      "port": 8080,
      "username": "proxy-user",
      "password": "proxy-pass",
      "useHttps": false,
      "location": "US-CO-DENVER"
    },
    "config": {
      "id": 101,
      "name": "E-commerce Configuration",
      "selectionConfiguration": {
        "alpha": 0.36951623165984,
        "beta": 0.0034437549399552,
        "kappa": 1,
        "pi": -0.50049837032537,
        "psi": 0.086705270313224,
        "tau": 1.0,
        "outlets": {
          "amazon.com": {
            "domain": "amazon.com",
            "pi": 0.85,
            "url": "https://amazon.com"
          }
        }
      }
    }
  },
  "rawConfiguration": {
    "id": 101,
    "name": "E-commerce Configuration",
    "description": "Configuration for e-commerce crawling",
    "active": true,
    "params": {
      "pi": -0.50049837032537,
      "psi": 0.086705270313224,
      "alpha": 0.36951623165984,
      "tau": 1.0,
      "kappa": 1,
      "beta": 0.0034437549399552,
      "active": true,
      "search_terms": [],
      "media_outlet_urls": []
    },
    "preferences": [
      {
        "name": "location",
        "description": "Geographic location preference",
        "value": "US-CO-DENVER",
        "active": true
      },
      {
        "name": "cookie_pref",
        "description": "Cookie handling preference",
        "value": "{\"accept_all\": true}",
        "active": true
      },
      {
        "name": "usage_type",
        "description": "Usage type preference",
        "value": "only_search",
        "active": true
      }
    ]
  },
  "pagesource": "<html><head><title>Product Page</title></head><body>...</body></html>"
}
```

### Required Response Format

Your Lambda function must return a JSON response with this exact structure:

```json
{
  "xpath": "//div[@class='product-item'][1]//button[@class='add-to-cart']"
}
```

The `xpath` field must contain a valid XPath expression that identifies the element(s) to interact with.

## Lambda Function Implementation Examples

### Python Example

```python
import json
from bs4 import BeautifulSoup

def lambda_handler(event, context):
    # Extract data from the payload
    job = event['job']
    crawler = event['crawler']
    raw_config = event['rawConfiguration']
    page_source = event['pagesource']
    
    # Parse HTML
    soup = BeautifulSoup(page_source, 'html.parser')
    
    # Get preferences for decision making
    preferences = {pref['name']: pref['value'] for pref in raw_config.get('preferences', [])}
    location = preferences.get('location', 'US-NY')
    usage_type = preferences.get('usage_type', 'general')
    
    # Example: Select highest-rated product
    if usage_type == 'only_search':
        # Look for search results
        products = soup.find_all('div', class_='search-result-item')
        if products:
            # Select first product with highest rating
            best_product = None
            highest_rating = 0
            
            for i, product in enumerate(products):
                rating_elem = product.find('span', class_='rating')
                if rating_elem:
                    try:
                        rating = float(rating_elem.text.strip())
                        if rating > highest_rating:
                            highest_rating = rating
                            best_product = i + 1  # XPath is 1-indexed
                    except ValueError:
                        continue
            
            if best_product:
                xpath = f"//div[@class='search-result-item'][{best_product}]//button[@class='add-to-cart']"
            else:
                xpath = "//div[@class='search-result-item'][1]//button[@class='add-to-cart']"
        else:
            xpath = "//button[contains(@class, 'add-to-cart')][1]"
    else:
        # Default selection logic
        xpath = "//button[contains(text(), 'Add to Cart')][1]"
    
    return {
        "xpath": xpath
    }
```

### Node.js Example

```javascript
const cheerio = require('cheerio');

exports.handler = async (event) => {
    const { job, crawler, rawConfiguration, pagesource } = event;
    
    // Parse HTML
    const $ = cheerio.load(pagesource);
    
    // Extract preferences
    const preferences = {};
    if (rawConfiguration.preferences) {
        rawConfiguration.preferences.forEach(pref => {
            if (pref.active) {
                preferences[pref.name] = pref.value;
            }
        });
    }
    
    // Selection logic based on preferences
    let xpath;
    const usageType = preferences.usage_type || 'general';
    
    if (usageType === 'only_search') {
        // Find products with ratings
        const products = $('.product-item');
        let bestIndex = 1;
        let highestRating = 0;
        
        products.each((index, element) => {
            const ratingText = $(element).find('.rating').text();
            const rating = parseFloat(ratingText);
            
            if (rating > highestRating) {
                highestRating = rating;
                bestIndex = index + 1;
            }
        });
        
        xpath = `//div[@class='product-item'][${bestIndex}]//button[@class='buy-now']`;
    } else {
        xpath = "//button[contains(@class, 'primary-button')][1]";
    }
    
    return {
        xpath: xpath
    };
};
```

## Use Cases

### 1. Intelligent Product Selection
- Select products based on ratings, reviews, or price
- Consider user preferences and location
- Apply complex business logic for product recommendations

### 2. Dynamic Content Handling
- Handle pages with dynamically generated content
- Select elements based on real-time data analysis
- Adapt to different page layouts or A/B tests

### 3. Machine Learning Integration
- Use ML models to classify and select optimal elements
- Implement computer vision for visual element selection
- Apply NLP for content-based selection

### 4. External Data Integration
- Consult external APIs for selection decisions
- Use databases for historical selection patterns
- Integrate with recommendation engines

## Configuration Integration

### Using Raw Configuration Data

The `rawConfiguration` object provides access to the complete API configuration, including:

- **Preferences**: User-defined preferences that can influence selection logic
- **Parameters**: Statistical parameters for selection algorithms
- **Search Terms**: Available search terms for context
- **Media Outlets**: Configured media outlets with PI scores

### Preference-Based Selection

```python
def select_based_on_preferences(raw_config, page_source):
    preferences = {pref['name']: pref['value'] for pref in raw_config.get('preferences', [])}
    
    # Location-based selection
    location = preferences.get('location', 'US-NY')
    if 'EU' in location:
        return "//button[@data-region='europe']"
    
    # Cookie preference handling
    cookie_pref = json.loads(preferences.get('cookie_pref', '{}'))
    if cookie_pref.get('accept_all'):
        return "//button[@id='accept-all-cookies']"
    
    # Default selection
    return "//button[1]"
```

## Error Handling

### Lambda Function Errors

Your Lambda function should handle errors gracefully:

```python
def lambda_handler(event, context):
    try:
        # Your selection logic here
        xpath = perform_selection(event)
        
        return {"xpath": xpath}
        
    except Exception as e:
        # Log error for debugging
        print(f"Selection error: {str(e)}")
        
        # Return fallback selector
        return {"xpath": "//button[1]"}
```

### PrimeMover Error Handling

If the Lambda function fails or returns invalid data:
1. The job will be marked as failed
2. Error details will be logged
3. The joblist execution may continue or stop based on configuration

## Performance Considerations

### Lambda Function Optimization

1. **Cold Start Minimization**: Keep functions warm with scheduled invocations
2. **Memory Allocation**: Allocate sufficient memory for HTML parsing and processing
3. **Timeout Configuration**: Set appropriate timeout values (recommend 30-60 seconds)
4. **Dependency Management**: Minimize package sizes for faster cold starts

### Payload Size Limits

- AWS Lambda payload limit: 6MB for synchronous invocations
- Large page sources may need compression or truncation
- Consider processing only relevant page sections

## Security Considerations

### Authentication

- Use the `username` and `password` fields for Lambda function authentication
- Implement proper authentication validation in your Lambda function
- Consider using AWS IAM roles for secure access

### Data Privacy

- Page source may contain sensitive user data
- Implement appropriate data handling and retention policies
- Consider data encryption for sensitive information

## Debugging and Monitoring

### Lambda Function Logging

```python
import logging

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    logger.info(f"Processing job: {event['job']['name']}")
    logger.info(f"Page title: {extract_title(event['pagesource'])}")
    
    # Your logic here
    
    logger.info(f"Selected xpath: {xpath}")
    return {"xpath": xpath}
```

### PrimeMover Integration

- Lambda invocation details are logged in PrimeMover
- Job execution results include Lambda response data
- Failed Lambda calls are captured in job failure logs

## Best Practices

### 1. Robust Selection Logic
- Always provide fallback selectors
- Handle missing or malformed HTML gracefully
- Validate XPath expressions before returning

### 2. Performance Optimization
- Cache frequently used data
- Minimize HTML parsing overhead
- Use efficient algorithms for element selection

### 3. Configuration Utilization
- Leverage preferences for personalized selection
- Use statistical parameters for probabilistic selection
- Consider location and agent information for context

### 4. Testing and Validation
- Test Lambda functions with real page sources
- Validate XPath expressions in browser developer tools
- Monitor selection success rates and adjust logic accordingly

## Troubleshooting

### Common Issues

1. **Invalid XPath**: Ensure returned XPath is syntactically correct
2. **Element Not Found**: XPath doesn't match any elements on the page
3. **Lambda Timeout**: Function execution exceeds configured timeout
4. **Payload Too Large**: Page source exceeds Lambda payload limits

### Debugging Steps

1. **Check Lambda Logs**: Review CloudWatch logs for function execution details
2. **Validate XPath**: Test XPath expressions in browser console
3. **Inspect Page Source**: Verify HTML structure matches expectations
4. **Test Locally**: Use local HTML files to test selection logic

## Migration from Simple Selectors

### Converting CSS to Lambda

Before (CSS selector):
```json
{
  "type": "clickjob",
  "behaviors": [
    {"name": "selectionType", "value": "CSS"},
    {"name": "selector", "value": ".product-item:first-child .add-to-cart"}
  ]
}
```

After (Lambda selector):
```json
{
  "type": "lambdaselectjob",
  "behaviors": [
    {"name": "linkValue", "value": "product-selector-lambda"},
    {"name": "task", "value": "Select first product add-to-cart button"}
  ]
}
```

### Converting XPath to Lambda

Before (XPath selector):
```json
{
  "type": "clickjob",
  "behaviors": [
    {"name": "selectionType", "value": "XPATH"},
    {"name": "selector", "value": "//div[@class='product'][1]//button[@class='buy-now']"}
  ]
}
```

After (Lambda with enhanced logic):
```python
def lambda_handler(event, context):
    # Enhanced selection with rating consideration
    soup = BeautifulSoup(event['pagesource'], 'html.parser')
    
    products = soup.find_all('div', class_='product')
    best_product_index = 1
    
    # Find product with highest rating
    for i, product in enumerate(products):
        rating = extract_rating(product)
        if rating > 4.5:  # High-rated product
            best_product_index = i + 1
            break
    
    return {
        "xpath": f"//div[@class='product'][{best_product_index}]//button[@class='buy-now']"
    }
```

This documentation provides a comprehensive guide for implementing and using Lambda Select Jobs in PrimeMover, enabling sophisticated element selection logic through serverless functions.
