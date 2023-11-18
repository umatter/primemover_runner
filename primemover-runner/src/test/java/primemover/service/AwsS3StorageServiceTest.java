package primemover.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.model.IJob;
import primemover.model.jobs.WaitJob;

@SpringBootTest
class AwsS3StorageServiceTest implements ApplicationContextAware {
	private ApplicationContext ctx;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}

	@Value("${awsS3.finalsource.bucketName}")
	private String awsFinalSourceBucketName;
	@Value("${awsS3.finalsource.subFolder}")
	private String awsFinalSourceSubfolder;

	private AwsS3StorageService jobDataSerivce;

	@BeforeEach
	void setUpBeforeTest() {
		jobDataSerivce = new AwsS3StorageService(awsFinalSourceBucketName, awsFinalSourceSubfolder);
		ctx.getAutowireCapableBeanFactory().autowireBean(jobDataSerivce);

		if (!jobDataSerivce.init()) {
			throw new IllegalStateException("AwsS3Storage not initialized");
		}
		;
	}

	@Test
	void WillNotWriteBasicDataWriterWithPredicate() {

		jobDataSerivce.setWriteSelector((writer) -> false);

		IJob job = new WaitJob(1l, "test", "test", "test", 10);
		SimpleTestDataWriter writer = new SimpleTestDataWriter();
		jobDataSerivce.writeData(job, writer);

		assertFalse(writer.wasWriteCalled(), "write was called but not expected");
	}

	@Test
	void WillWriteBasicDataWriterWithoutPredicate() {
		IJob job = new WaitJob(1l, "test", "test", "test", 10);
		SimpleTestDataWriter writer = new SimpleTestDataWriter();
		jobDataSerivce.writeData(job, writer);

		assertTrue(writer.wasWriteCalled(), "write was not called but expected");
	}

	@Test
	void WillWriteBasicDataWriterWithPredicate() {

		jobDataSerivce.setWriteSelector((writer) -> true);

		IJob job = new WaitJob(1l, "test", "test", "test", 10);
		SimpleTestDataWriter writer = new SimpleTestDataWriter();
		jobDataSerivce.writeData(job, writer);

		assertTrue(writer.wasWriteCalled(), "write was not called but expected");
	}

}
