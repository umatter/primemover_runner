package primemover.anticaptcha;

public enum ResultStateEnum {
	NOTFOUND(false, null), // Captcha not found
	SUCCESS(true, true), // Captcha found and solved
	FAILURE(true, false), // captcha found but not solved
	ERROR(null, null);

	private Boolean captchaFound;
	private Boolean captchaSolved;

	private ResultStateEnum(Boolean captchaFound, Boolean captchaSolved) {
		this.captchaFound = captchaFound;
		this.captchaSolved = captchaSolved;
	}

	public Boolean wasCaptchaFound() {
		return captchaFound;
	}

	public Boolean wasCaptchaSolved() {
		return captchaSolved;
	}

}
