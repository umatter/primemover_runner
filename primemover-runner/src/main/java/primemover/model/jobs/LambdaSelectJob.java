package primemover.model.jobs;

import java.util.Map;

import javax.annotation.Nullable;

public class LambdaSelectJob extends BaseJob {

	private String linkValue;
	private LambdaLinkType linkType;
	private @Nullable String username = null;
	private @Nullable String password = null;

	/**
	 * The job's raw behaviours (name -> value), carried verbatim so the decision
	 * function can read arbitrary per-experiment treatment config from them. The
	 * typed fields above consume the job-control behaviours (task, linkType, ...);
	 * this preserves the rest -- notably a {@code strategy} marker and any
	 * strategy-specific keys -- which the runner otherwise dropped. Serialised into
	 * the lambda payload as {@code job.behaviors}; the decision function uses it
	 * only when it carries an explicit treatment marker, else falls back to the
	 * legacy selectionConfiguration.
	 */
	private @Nullable Map<String, String> behaviors = null;

	public LambdaSelectJob() {
		super();
	}

	public LambdaSelectJob(Long id, String name, String description, String task, String linkValue,
			LambdaLinkType linkType, String username, String password) {
		super(id, name, description, task);

		this.linkValue = linkValue;
		this.linkType = linkType;
		this.username = username;
		this.password = password;
	}

	public LambdaLinkType getLinkType() {
		return linkType;
	}

	public void setLinkType(LambdaLinkType linkType) {
		this.linkType = linkType;
	}

	public String getLinkValue() {
		return linkValue;
	}

	public void setLinkValue(String url) {
		this.linkValue = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Map<String, String> getBehaviors() {
		return behaviors;
	}

	public void setBehaviors(Map<String, String> behaviors) {
		this.behaviors = behaviors;
	}

}
