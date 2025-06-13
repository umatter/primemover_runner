package primemover.runner.context;

import org.openqa.selenium.MutableCapabilities;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginVersion;
import primemover.multilogin.ml6.MulitLoginBrowserProfile;

public class MultiloginContext extends ContextBase<MutableCapabilities> {
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
	
	public MultiLoginAgent getTypedAgent() {
		if (!(this.getCrawler().getAgent() instanceof MultiLoginAgent)) {
			throw new IllegalStateException("Found non MultiLoginAgent");
		}
		
		return (MultiLoginAgent) this.getCrawler().getAgent();
	}

	@Override
	public boolean isValid() {
		return super.isValid() && (profile != null || this.getTypedAgent().getVersion() == MultiLoginVersion.MLX);
	}

}
