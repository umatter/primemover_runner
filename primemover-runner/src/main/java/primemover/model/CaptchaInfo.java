package primemover.model;

public class CaptchaInfo {

	private boolean captchaDetected = false;
	private String captchaType;
	
	public boolean isCaptchaDetected() {
		return captchaDetected;
	}
	public void setCaptchaDetected(boolean captchaDetected) {
		this.captchaDetected = captchaDetected;
	}
	
	public String getCaptchaType() {
		return captchaType;
	}
	
	public void setCaptchaType(String captchaType) {
		this.captchaType = captchaType;
	}
	
	CaptchaInfo of(boolean hasDetected, String captchaType) {
		CaptchaInfo out = new CaptchaInfo();
		out.setCaptchaDetected(hasDetected);
		out.setCaptchaType(captchaType);
		
		return out;
	}
}
