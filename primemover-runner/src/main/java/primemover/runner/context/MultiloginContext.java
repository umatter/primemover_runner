package primemover.runner.context;

import primemover.multilogin.MulitLoginBrowserProfile;

public class MultiloginContext extends ContextBase {
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

	@Override
	public boolean isValid() {
		return super.isValid() && profile != null;
	}

}
