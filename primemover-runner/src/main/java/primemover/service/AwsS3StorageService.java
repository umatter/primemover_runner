package primemover.service;

import java.io.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.IJob;
import primemover.service.jobdatawriter.IJobDataWriter;
import primemover.service.jobdatawriter.StorageHelper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class AwsS3StorageService implements IStorageService {

	private static Logger logger = LoggerFactory.getLogger(AwsS3StorageService.class);
	
	@Autowired
	private ICrawlerService crawlerService;

	@Value("${awsS3.bucketName}")
	private String bucketName;
	@Value("${awsS3.accessKey}")
	private String accessKey;
	@Value("${awsS3.secretKey}")
	private String secretKey;
	@Value("${awsS3.subFolder}")
	private String subFolder;
	
	public AwsS3StorageService() {
	}

	@Override
	public Boolean writeFixedJobData(IJob job, JobData data) {	
		StorageHelper helper = new StorageHelper();
				
		try (ByteArrayOutputStream os = new ByteArrayOutputStream(4048)) {
			logger.debug("Prepare writing fixed data to AWS S3 for job {}", job.getId());
			AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
					accessKey,
				    secretKey);
				
	        S3Client s3 = S3Client.builder()
	                .region(Region.EU_CENTRAL_1)
	                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
	                .build();
	        
			logger.debug("Start writing data for job {}", job.getId());
			if (helper.writeJobDataAsZip(os, job, data)) {
				String identifier = helper.getIdentifier(job);
				PutObjectRequest objectRequest = PutObjectRequest.builder()
	                    .bucket(bucketName)
	                    .contentType("application/zip")
	                    .key(subFolder + identifier)
	                    .build();
	            

	            s3.putObject(objectRequest, RequestBody.fromBytes(os.toByteArray()));

	            logger.debug("fixed data written so AWS S3 for job {}", job.getId());
	            
	            crawlerService.writeS3JobData(job.getId(), identifier, "s3://" + bucketName + "/" + subFolder + identifier, "fixed job data");
	            logger.debug("wrote info to backend for job {}", job.getId());
	            return true;
			} else {
				logger.warn("error creating zip for job {}", job.getId());
				return false;
			}
			
		} catch (Exception e) {
			logger.error("error writing data to AWS S3 for job {}", job.getId(), e);
			return false;
		}
	}

	@Override
	public Boolean writeData(IJob job, IJobDataWriter writer) {
		logger.debug("Start writing to AWS S3 for job {}", job.getId());
		
        try {
        	AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
    				accessKey,
    			    secretKey);
    			
            S3Client s3 = S3Client.builder()
                    .region(Region.EU_CENTRAL_1)
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .build();
            
        	PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .contentType(writer.getContentType())
                    .key(subFolder + writer.getIdentifier())
                    .build();
            
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            
            writer.writeData(os);

            s3.putObject(objectRequest, RequestBody.fromBytes(os.toByteArray()));
            logger.debug("data written so AWS S3 for job {}", job.getId());
            
            String identifier = writer.getIdentifier();
            
            crawlerService.writeS3JobData(job.getId(), identifier, "s3://" + bucketName + "/" + subFolder + identifier, "fixed job data");
            logger.debug("wrote info to backend for job {}", job.getId());
            return true;
		} catch (Exception e) {
			logger.error("error writing data to AWS S3 for job {}", job.getId());
			return false;
		}
	}

	
}
