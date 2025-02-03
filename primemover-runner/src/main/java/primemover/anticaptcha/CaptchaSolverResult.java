package primemover.anticaptcha;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CaptchaSolverResult {
	// some default results
	public static CaptchaSolverResult _PLUGINFAILURE = of(true, false, false, false, 0, false, true, null);
	public static CaptchaSolverResult _NOCAPTCHADETECTED = of(true, true, false, false, 0, false, false, null);
	public static CaptchaSolverResult DISABLED = of(false, false, false, false, 0, false, false, null);

	private boolean pluginEnabled = true;
	private boolean pluginWorking = true;
	private boolean captchaDetected = false;
	private long solvingSeconds;
	private boolean hasSolvingTimeout = false;
	private boolean captchaSolved = false;
	private boolean hasError = false;

	private CaptchaSolverResult() {
	}

	@Nullable
	private String captchaType;

	public void setPluginEnabled(boolean enabled) {
		this.pluginEnabled = enabled;
	}

	public boolean isPluginEnabled() {
		return pluginEnabled;
	}

	public boolean isCaptchaDetected() {
		return captchaDetected;
	}

	public void setCaptchaDetected(boolean captchaDetected) {
		this.captchaDetected = captchaDetected;
	}

	public String getCaptchaType() {
		return captchaType;
	}

	public void setCaptchaType(@Nullable String captchaType) {
		this.captchaType = captchaType;
	}

	public boolean isCaptchaSolved() {
		return captchaSolved;
	}

	public void setCaptchaSolved(boolean captchaSolved) {
		this.captchaSolved = captchaSolved;
	}

	public boolean hasError() {
		return hasError;
	}

	public void setHasError(boolean hasError) {
		this.hasError = hasError;
	}

	public boolean isPluginWorking() {
		return pluginWorking;
	}

	public void setPluginWorking(boolean pluginWorking) {
		this.pluginWorking = pluginWorking;
	}

	public boolean hasSolvingTimeout() {
		return hasSolvingTimeout;
	}

	public void setHasSolvingTimeout(boolean hasSolvingTimeout) {
		this.hasSolvingTimeout = hasSolvingTimeout;
	}

	public long getSolvingSeconds() {
		return solvingSeconds;
	}

	public void setSolvingSeconds(long solvingSeconds) {
		this.solvingSeconds = solvingSeconds;
	}

	static public @Nonnull CaptchaSolverResult of(boolean pluginEnabled, boolean pluginWorking, boolean captchaDetected,
			boolean captchaSolved, long solvingSeconds, boolean hasSolvingTimeout, boolean hasError,
			@Nullable String captchaType) {
		CaptchaSolverResult out = new CaptchaSolverResult();
		out.setPluginEnabled(pluginEnabled);
		out.setPluginWorking(pluginWorking);
		out.setCaptchaDetected(captchaDetected);
		out.setCaptchaSolved(captchaSolved);
		out.setHasError(hasError);
		out.setSolvingSeconds(solvingSeconds);
		out.setHasSolvingTimeout(hasSolvingTimeout);
		out.setCaptchaType(captchaType);

		return out;
	}
}
