package primemover.anticaptcha;

import javax.annotation.Nullable;

public enum ResultStateEnum {
	NOTFOUND(false, null), // Captcha not found
	SUCCESS(true, true), // Captcha found and solved
	FAILURE(true, false), // captcha found but not solved
	ERROR(null, null);

	private @Nullable Boolean captchaFound;
	private @Nullable Boolean captchaSolved;

	private ResultStateEnum(Boolean captchaFound, Boolean captchaSolved) {
		this.captchaFound = captchaFound;
		this.captchaSolved = captchaSolved;
	}

	public @Nullable Boolean foundCaptcha() {
		return captchaFound;
	}

	public @Nullable Boolean solvedCaptcha() {
		return captchaSolved;
	}

}
