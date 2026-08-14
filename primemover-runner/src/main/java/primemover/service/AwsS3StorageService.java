package primemover.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import primemover.model.IJob;
import primemover.service.datawriter.IDataWriter;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayOutputStream;
import java.util.function.Predicate;

public class AwsS3StorageService implements IStorageService {

	private static Logger logger = LoggerFactory.getLogger(AwsS3StorageService.class);

	@Autowired
	private ICrawlerService crawlerService;

	private String bucketName;
	private String subFolder;
	@Value("${awsS3.accessKey}")
	private String accessKey;
	@Value("${awsS3.secretKey}")
	private String secretKey;
	// Blank => real AWS (eu-central-1) exactly as before; set => S3-compatible
	// store (e.g. MinIO: http://127.0.0.1:9000) with path-style addressing.
	@Value("${awsS3.endpoint:}")
	private String endpoint;
	@Value("${awsS3.region:eu-central-1}")
	private String region;

	private Predicate<IDataWriter> writeSelector = null;

	public AwsS3StorageService(String bucketName, String subFolder) {
		this.bucketName = bucketName;
		this.subFolder = subFolder;
	}

	public void setWriteSelector(Predicate<IDataWriter> selector) {
		this.writeSelector = selector;
	}

	boolean isInit = false;
	S3Client client = null;

	public boolean init() {
		try {
			AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);

			software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
					.region(Region.of(region))
					.credentialsProvider(StaticCredentialsProvider.create(awsCreds));
			if (endpoint != null && !endpoint.isBlank()) {
				// MinIO / self-hosted: custom endpoint + path-style addressing
				// (virtual-host style needs per-bucket DNS, which MinIO doesn't provide).
				builder = builder.endpointOverride(java.net.URI.create(endpoint)).forcePathStyle(true);
			}
			client = builder.build();
		} catch (Exception e) {
			logger.error("error creating client for writing to AWS S3");
			client = null;
		}

		isInit = true;

		return client != null;
	}

	@Retryable
	@Override
	public Boolean writeData(IJob job, IDataWriter writer) {
		logger.debug("Start writing to AWS S3 for job {}, retry {}", job.getId(),
				RetrySynchronizationManager.getContext().getRetryCount());
		if (isInit == false) {
			throw new IllegalStateException("call init() first");
		}

		if (client == null) {
			logger.warn("client is not initialised");
			return false;
		}

		// exit fast if we don't need to write from this source
		if (writeSelector != null && !writeSelector.test(writer)) {
			logger.debug("storage {}/{} is skiping for '{}'", this.bucketName, this.subFolder, writer.getIdentifier());
			return true;
		}

		// write data to AWS
		logger.debug("storage {}/{} is starting preparing for '{}'", this.bucketName, this.subFolder,
				writer.getIdentifier());
		try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(bucketName)
					.contentType(writer.getContentType()).key(subFolder + writer.getIdentifier()).build();

			writer.writeData(os);

			PutObjectResponse response = client.putObject(objectRequest, RequestBody.fromBytes(os.toByteArray()));
			logger.debug("data written so AWS S3 for job {} with response {}", job.getId(), response);

			// write report info to backend (without data)
			String identifier = writer.getIdentifier();
			boolean writeResult = crawlerService.writeS3JobData(job.getId(), identifier,
					"s3://" + bucketName + "/" + subFolder + identifier, "fixed job data");
			if (writeResult) {
				logger.debug("wrote info to backend for job {}", job.getId());
				return true;
			} else {
				logger.warn("failed writing info to backend for job {}", job.getId());
				return false;
			}

		} catch (Exception e) {
			logger.error("error writing data to AWS S3 for job {}", job.getId(), e);
			return false;
		}
	}

}
