package primemover.service;

import java.io.ByteArrayOutputStream;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.IJob;
import primemover.service.datawriter.IDataWriter;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

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

	public void init() {
		try {
			AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);

			client = S3Client.builder().region(Region.EU_CENTRAL_1)
					.credentialsProvider(StaticCredentialsProvider.create(awsCreds)).build();
		} catch (Exception e) {
			logger.error("error creating client for writing to AWS S3");
			client = null;
		}

		isInit = true;
	}

	@Override
	public Boolean writeData(IJob job, IDataWriter writer) {
		logger.debug("Start writing to AWS S3 for job {}", job.getId());
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
