package primemover.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LogType;

import java.time.Duration;
import java.util.Base64;

public class LambdaCaller implements AutoCloseable {
	private static Logger logger = LoggerFactory.getLogger(LambdaCaller.class);

	@Value("${awsS3.accessKey}")
	private String accessKey;
	@Value("${awsS3.secretKey}")
	private String secretKey;

	// Lambda execution mode: "local" or "aws"
	@Value("${lambda.mode:aws}")
	private String lambdaMode;

	// Local Lambda server URL (used when lambda.mode=local)
	@Value("${lambda.local.url:http://localhost:5001/invoke}")
	private String localLambdaUrl;

	@Value("${awsLambda.apiCallTimeoutSeconds:120}")
	private int apiCallTimeoutSeconds;
	@Value("${awsLambda.apiCallAttemptTimeoutSeconds:90}")
	private int apiCallAttemptTimeoutSeconds;

	private Region region = Region.EU_CENTRAL_1;

	// API call timeouts are configurable via awsLambda.* above. The transport-level
	// timeouts below are not: the SDK default socket timeout is 30s, far too short for
	// a Lambda that averages 40s and can reach 60s, so it must stay above them.
	private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(90);
	private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

	private LambdaClient awsClient = null;
	private RestTemplate localClient = null;

	public LambdaCaller() {

	}

	private void initAwsClient() {
		if (awsClient != null) {
			return;
		}

		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(this.accessKey, this.secretKey);

		ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
				.apiCallTimeout(Duration.ofSeconds(apiCallTimeoutSeconds))
				.apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeoutSeconds))
				.build();

		// Transport-level timeouts: the SDK default socket timeout (30s) is shorter than a
		// typical Lambda call, so it must be raised independently of the API call timeouts.
		UrlConnectionHttpClient.Builder httpClientBuilder = UrlConnectionHttpClient.builder()
				.socketTimeout(SOCKET_TIMEOUT)
				.connectionTimeout(CONNECTION_TIMEOUT);

		logger.debug("initializing AWS client with apiCallTimeout={}s, apiCallAttemptTimeout={}s, socketTimeout={}s",
				apiCallTimeoutSeconds, apiCallAttemptTimeoutSeconds, SOCKET_TIMEOUT.getSeconds());
		this.awsClient = LambdaClient.builder()
				.region(this.region)
				.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
				.overrideConfiguration(clientConfig)
				.httpClientBuilder(httpClientBuilder)
				.build();
	}

	private void initLocalClient() {
		if (localClient != null) {
			return;
		}

		logger.debug("initializing local Lambda client with URL: {}", localLambdaUrl);
		this.localClient = new RestTemplate();
	}

	/**
	 * Invoke the Lambda function (unified interface for both local and AWS).
	 * Returns a LambdaInvokeResult that works with both modes.
	 */
	public LambdaInvokeResult invoke(String functionName, String param) throws LambdaException {
		if ("local".equalsIgnoreCase(lambdaMode)) {
			return invokeLocal(param);
		} else {
			return invokeAws(functionName, param);
		}
	}

	/**
	 * Invoke the Lambda function via AWS Lambda service.
	 */
	private LambdaInvokeResult invokeAws(String functionName, String param) throws LambdaException {
		this.initAwsClient();

		if (this.awsClient == null) {
			throw new LambdaException("Lambda client is not initialized");
		}

		try {
			logger.info("calling AWS Lambda function: {}", functionName);
			SdkBytes payload = SdkBytes.fromUtf8String(param);

			// Setup an InvokeRequest with CloudWatch log capture
			InvokeRequest request = InvokeRequest.builder()
					.functionName(functionName)
					.payload(payload)
					.logType(LogType.TAIL) // Request last 4KB of CloudWatch logs
					.build();

			logger.debug("invoking AWS function");
			InvokeResponse res = this.awsClient.invoke(request);

			// Extract and log CloudWatch logs if available
			if (res.logResult() != null && !res.logResult().isEmpty()) {
				try {
					String decodedLogs = new String(Base64.getDecoder().decode(res.logResult()));
					logger.info("Lambda CloudWatch logs for '{}': \n{}", functionName, decodedLogs);
				} catch (Exception logEx) {
					logger.warn("Failed to decode Lambda CloudWatch logs", logEx);
				}
			}

			// Enhanced error logging
			if (res.functionError() != null && !res.functionError().isEmpty()) {
				logger.error("Lambda function error type: {}", res.functionError());
				logger.error("Lambda status code: {}", res.statusCode());

				// Log the response payload which may contain error details
				String responsePayload = res.payload().asUtf8String();
				logger.error("Lambda error payload: {}", responsePayload);

				// Log execution details
				if (res.executedVersion() != null) {
					logger.error("Lambda executed version: {}", res.executedVersion());
				}
			}

			// Convert to our unified result type
			return LambdaInvokeResult.builder()
					.statusCode(res.statusCode())
					.payload(res.payload().asUtf8String())
					.functionError(res.functionError())
					.build();

		} catch (ApiCallTimeoutException ex) {
			logger.error("Lambda invocation timed out after {}s (total timeout). Function: {}",
					apiCallTimeoutSeconds, functionName, ex);
			throw new LambdaException("Lambda invocation exceeded total timeout of " +
					apiCallTimeoutSeconds + "s", ex);
		} catch (ApiCallAttemptTimeoutException ex) {
			logger.error("Lambda invocation attempt timed out after {}s (per-attempt timeout). Function: {}",
					apiCallAttemptTimeoutSeconds, functionName, ex);
			throw new LambdaException("Lambda invocation attempt exceeded timeout of " +
					apiCallAttemptTimeoutSeconds + "s", ex);
		} catch (Exception ex) {
			logger.error("error invoking AWS function", ex);
			throw new LambdaException("Failure calling Lambda function", ex);
		}
	}

	/**
	 * Invoke the Lambda function via local HTTP server.
	 * The local server mirrors the AWS Lambda interface.
	 */
	private LambdaInvokeResult invokeLocal(String param) throws LambdaException {
		this.initLocalClient();

		try {
			logger.info("calling local Lambda server at: {}", localLambdaUrl);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<String> request = new HttpEntity<>(param, headers);

			logger.debug("invoking local function");
			ResponseEntity<String> response = localClient.postForEntity(localLambdaUrl, request, String.class);

			if (!response.getStatusCode().is2xxSuccessful()) {
				logger.error("Local Lambda returned error status: {}", response.getStatusCode());
				logger.error("Local Lambda response body: {}", response.getBody());
				throw new LambdaException("Local Lambda returned error: " + response.getStatusCode());
			}

			String responseBody = response.getBody();
			logger.debug("local Lambda response received, length: {}", responseBody != null ? responseBody.length() : 0);

			// Return as our unified result type
			return LambdaInvokeResult.builder()
					.statusCode(response.getStatusCode().value())
					.payload(responseBody != null ? responseBody : "")
					.functionError(null)
					.build();

		} catch (LambdaException ex) {
			throw ex;
		} catch (Exception ex) {
			logger.error("error invoking local function", ex);
			throw new LambdaException("Failure calling local Lambda function", ex);
		}
	}

	@Override
	public void close() throws Exception {
		if (this.awsClient != null) {
			this.awsClient.close();
			this.awsClient = null;
		}
		this.localClient = null;
	}
}
