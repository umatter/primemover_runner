package primemover.runner;

import primemover.util.MulitLoginBrowserProfile;

public class MultiloginContext extends OperatorContext {
	private MulitLoginBrowserProfile profile;

	public MultiloginContext() {
		super();
	}

	public MulitLoginBrowserProfile getProfile() {
		return profile;
	}

	public void setProfile(MulitLoginBrowserProfile profile) {
		this.profile = profile;
	}

}
