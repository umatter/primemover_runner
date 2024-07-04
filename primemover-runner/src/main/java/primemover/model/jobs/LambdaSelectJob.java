package primemover.model.jobs;

import javax.annotation.Nullable;

public class LambdaSelectJob extends BaseJob {

	private String linkValue;
	private LambdaLinkType linkType;
	private @Nullable String username = null;
	private @Nullable String password = null;

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

}
