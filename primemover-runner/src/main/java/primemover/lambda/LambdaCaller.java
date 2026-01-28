package primemover.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.time.Duration;

public class LambdaCaller implements AutoCloseable {
	private static Logger logger = LoggerFactory.getLogger(LambdaCaller.class);

	@Value("${awsS3.accessKey}")
	private String accessKey;
	@Value("${awsS3.secretKey}")
	private String secretKey;

	private Region region = Region.EU_CENTRAL_1;

	// Lambda function can take up to 300s, so we need a longer timeout than the default 30s
	// Socket read timeout must be longer than expected Lambda execution time (avg 40s, max 60s)
	private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(120);
	private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(90);
	private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(90);
	private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

	private LambdaClient client = null;

	public LambdaCaller() {

	}

	private void initClient() {

		if (client != null) {
			return;
		}

		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(this.accessKey, this.secretKey);

		// Configure timeouts for Lambda invocation
		ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
				.apiCallTimeout(API_CALL_TIMEOUT)
				.apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
				.build();

		// Configure HTTP client with socket/read timeout
		// Default socket timeout is 30s which is too short for Lambda calls (avg 40s)
		UrlConnectionHttpClient.Builder httpClientBuilder = UrlConnectionHttpClient.builder()
				.socketTimeout(SOCKET_TIMEOUT)
				.connectionTimeout(CONNECTION_TIMEOUT);

		logger.debug("initializing client with API timeout: {}s, socket timeout: {}s",
				API_CALL_TIMEOUT.getSeconds(), SOCKET_TIMEOUT.getSeconds());
		this.client = LambdaClient.builder() //
				.region(this.region) //
				.credentialsProvider(StaticCredentialsProvider.create(awsCreds))//
				.overrideConfiguration(overrideConfig)
				.httpClientBuilder(httpClientBuilder)
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

			// Setup an InvokeRequest.
			InvokeRequest request = InvokeRequest.builder() //
					.functionName(functionName) //
					.payload(payload) //
					.build();

			logger.debug("invoking function");
			res = this.client.invoke(request);

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
