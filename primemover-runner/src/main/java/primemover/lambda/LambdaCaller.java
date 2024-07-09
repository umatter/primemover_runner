package primemover.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public class LambdaCaller implements AutoCloseable {
	private static Logger logger = LoggerFactory.getLogger(LambdaCaller.class);

	@Value("${awsS3.accessKey}")
	private String accessKey;
	@Value("${awsS3.secretKey}")
	private String secretKey;

	private Region region = Region.EU_CENTRAL_1;

	private LambdaClient client = null;

	public LambdaCaller() {

	}

	private void initClient() {

		if (client != null) {
			return;
		}

		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(this.accessKey, this.secretKey);

		logger.debug("initializing client");
		this.client = LambdaClient.builder() //
				.region(this.region) //
				.credentialsProvider(StaticCredentialsProvider.create(awsCreds))//
				.build();
	}

	public InvokeResponse invoke(String functionName, String param) {
		this.initClient();

		if (this.client == null) {
			throw new IllegalStateException("Lambda client is not initialized");
		}

		InvokeResponse res = null;

		try {
			SdkBytes payload = SdkBytes.fromUtf8String(param);

			// Setup an InvokeRequest.
			InvokeRequest request = InvokeRequest.builder() //
					.functionName(functionName) //
					.payload(payload) //
					.build();

			logger.debug("invoking function");
			res = this.client.invoke(request);

			return res;

		} catch (Exception ex) {

			logger.error("error invoking function", ex);
			throw new IllegalStateException("Failure calling Lambda function");
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
