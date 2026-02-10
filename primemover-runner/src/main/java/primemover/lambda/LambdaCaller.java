package primemover.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
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

	@Value("${awsLambda.apiCallTimeoutSeconds:120}")
	private int apiCallTimeoutSeconds;
	@Value("${awsLambda.apiCallAttemptTimeoutSeconds:90}")
	private int apiCallAttemptTimeoutSeconds;

	private Region region = Region.EU_CENTRAL_1;

	private LambdaClient client = null;

	public LambdaCaller() {

	}

	private void initClient() {

		if (client != null) {
			return;
		}

		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(this.accessKey, this.secretKey);

		logger.debug("initializing Lambda client with timeouts: apiCallTimeout={}s, apiCallAttemptTimeout={}s",
				apiCallTimeoutSeconds, apiCallAttemptTimeoutSeconds);

		ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
				.apiCallTimeout(Duration.ofSeconds(apiCallTimeoutSeconds))
				.apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeoutSeconds))
				.build();

		this.client = LambdaClient.builder() //
				.region(this.region) //
				.credentialsProvider(StaticCredentialsProvider.create(awsCreds))//
				.overrideConfiguration(clientConfig) //
				.build();
	}

	public InvokeResponse invoke(String functionName, String param) throws LambdaException {
		this.initClient();

		if (this.client == null) {
			throw new LambdaException("Lambda client is not initialized");
		}

		InvokeResponse res = null;

		try {
			logger.info("calling Lambda function: {}", functionName);
			SdkBytes payload = SdkBytes.fromUtf8String(param);

			// Setup an InvokeRequest with CloudWatch log capture
			InvokeRequest request = InvokeRequest.builder() //
					.functionName(functionName) //
					.payload(payload) //
					.logType(LogType.TAIL) // Request last 4KB of CloudWatch logs
					.build();

			logger.debug("invoking function");
			res = this.client.invoke(request);

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

			return res;

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
			logger.error("error invoking function", ex);
			throw new LambdaException("Failure calling Lambda function", ex);
		}

	}

	@Override
	public void close() throws Exception {
		if (this.client == null) {
			return;
		}

		this.client.close();
		this.client = null;
	}

}
