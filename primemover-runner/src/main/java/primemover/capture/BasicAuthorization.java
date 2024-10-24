package primemover.capture;

import javax.annotation.Nonnull;

public class BasicAuthorization {

	@Nonnull
	private String username;
	@Nonnull
	private String password;

	public BasicAuthorization(String username, String password) {
		this.username = username;
		this.password = password;
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
