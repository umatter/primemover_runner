package primemover.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stored in place of a job's session storage when it is byte-identical to the
 * one an earlier job of the same session stored in full: that job's id, the
 * identifier of its {@code static-jobdata} object (under the job-data prefix,
 * e.g. {@code reports/}), and the SHA-256 of the content, so a reader can check
 * it followed the pointer to the right bytes. The job it names always holds the
 * content in full -- pointers never chain.
 */
public final class SessionStorageRef {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final long jobId;
	private final String object;
	private final String sha256;

	public SessionStorageRef(long jobId, String object, String sha256) {
		this.jobId = jobId;
		this.object = object;
		this.sha256 = sha256;
	}

	public long getJobId() {
		return jobId;
	}

	/** The {@code static-jobdata} object identifier of the job that holds the content. */
	public String getObject() {
		return object;
	}

	/** SHA-256 (hex) of the session storage JSON as stored by that job. */
	public String getSha256() {
		return sha256;
	}

	/** {@code {"same_as_job_id": …, "same_as_object": "…", "sha256": "…"}}. */
	public String toJson() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("same_as_job_id", jobId);
		out.put("same_as_object", object);
		out.put("sha256", sha256);
		try {
			return MAPPER.writeValueAsString(out);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}
}
