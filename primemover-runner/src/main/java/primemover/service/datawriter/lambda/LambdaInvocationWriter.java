package primemover.service.datawriter.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.service.datawriter.RecordedJobDataBaseWriter;
import primemover.service.datawriter.StorageUtility;
import software.amazon.awssdk.utils.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LambdaInvocationWriter extends RecordedJobDataBaseWriter<LambdaInvocationData> {

	private final static Logger logger = LoggerFactory.getLogger(LambdaInvocationWriter.class);

	public LambdaInvocationWriter(LambdaInvocationData data) {
		super(data, "lambda-jobdata", "zip", "application/zip", "invocation data of Lambda call");
	}

	@Override
	public boolean hasOutput() {
		return true;
	}

	@Override
	public boolean writeData(OutputStream os) {
		LambdaInvocationData data = this.getData();
		logger.info("writting Lambda-Invocation as Zip for job {}", data.getJob().getId());

		// create filename
		String datePrefix = StorageUtility.formatDateForFilePrefix(new Date());
		String jobId = data.getJob().getId().toString();

		String inputName = datePrefix + "_lambda_input_jobdata_" + jobId + ".json";
		String outputName = datePrefix + "_lambda_output_jobdata_" + jobId + ".json";

		// prepare streams
		try (ZipOutputStream zipOut = new ZipOutputStream(os)) {
			// Input
			if (!StringUtils.isEmpty(data.getInputJson())) {
				ZipEntry zipEntry = new ZipEntry(inputName);
				zipOut.putNextEntry(zipEntry);
				byte[] tmpByte = data.getInputJson().getBytes(StandardCharsets.UTF_8);
				zipOut.write(tmpByte);
			}

			// Output
			if (!StringUtils.isEmpty(data.getOutputJson())) {
				ZipEntry zipEntry = new ZipEntry(outputName);
				zipOut.putNextEntry(zipEntry);
				byte[] tmpByte = data.getOutputJson().getBytes(StandardCharsets.UTF_8);
				zipOut.write(tmpByte);
			}

			// make sure everything is written
			zipOut.finish();
			zipOut.flush();

			logger.debug("Done writing lambda data for job {}", data.getJob().getId());
		} catch (IOException e) {
			logger.warn("Error creating lambda data for job {}", data.getJob().getId(), e);
			return false;
		}

		return true;
	}

}
