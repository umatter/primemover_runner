package primemover.runner.context;

import org.openqa.selenium.MutableCapabilities;

import primemover.multilogin.MulitLoginBrowserProfile;

public class MultiloginContext<OptionsType extends MutableCapabilities> extends ContextBase<OptionsType> {
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
