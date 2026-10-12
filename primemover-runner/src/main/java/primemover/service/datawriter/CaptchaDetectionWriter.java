package primemover.service.datawriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.anticaptcha.CaptchaSolverResult;
import primemover.runner.CaptchaSolverResultHolder;
import primemover.runner.RecordedJobData;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code static-captcha} object: what the captcha solver met and did during
 * one job, as JSON --
 * {@code {"job_id": …, "before": {…} | null, "after": {…} | null}}, each attempt
 * carrying {@code detected}, {@code solved}, {@code type} (the challenge label),
 * {@code solving_seconds}, {@code solving_timeout} and {@code error}. An attempt
 * the job's {@code captchaMode} did not ask for is {@code null}.
 *
 * <p>
 * Until 2026-10-12 this built the JSON of the whole recorded job and then wrote
 * the page source instead. Nobody noticed: on Multilogin X the solver never
 * detected anything, so {@link #hasOutput()} was false and the object empty.
 */
public class CaptchaDetectionWriter extends RecordedJobDataBaseWriter<RecordedJobData> {

	private final static Logger logger = LoggerFactory.getLogger(CaptchaDetectionWriter.class);

	public CaptchaDetectionWriter(RecordedJobData data) {
		super(data, "static-captcha", "json", "application/json", "captcha data stored as json");
	}

	@Override
	public boolean writeData(OutputStream os) {
		RecordedJobData data = getData();
		logger.info("writting captcha data as JSON for job {}", data.getJob().getId());

		if (!data.hasAnyCaptchaResult()) {
			return false;
		}

		try {
			CaptchaSolverResultHolder results = data.getCaptchaResults();
			Map<String, Object> record = new LinkedHashMap<>();
			record.put("job_id", data.getJob().getId());
			record.put("before", attempt(results.getBeforeJob()));
			record.put("after", attempt(results.getAfterJob()));

			os.write(new ObjectMapper().writeValueAsBytes(record));
			return true;

		} catch (Exception ex) {
			logger.warn("Error writing captcha data", ex);
			return false;
		}
	}

	private static @Nullable Map<String, Object> attempt(@Nullable CaptchaSolverResult result) {
		if (result == null) {
			return null;
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("detected", result.isCaptchaDetected());
		out.put("solved", result.isCaptchaSolved());
		out.put("type", result.getCaptchaType());
		out.put("solving_seconds", result.getSolvingSeconds());
		out.put("solving_timeout", result.hasSolvingTimeout());
		out.put("error", result.hasError());
		return out;
	}

	@Override
	public boolean hasOutput() {
		if (!getData().hasAnyCaptchaResult()) {
			return false;
		}

		return getData().getCaptchaResults().isAnyCaptchaDetected();
	}
}
