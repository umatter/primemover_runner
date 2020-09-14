package primemover.model;

public enum MultiLoginIBrowserType {
	MIMIC("mimic"),
	STEALTHFOX("stealthfox"),
	MIMIC_MOBILE("mimic_mobile");

	private String multiloginValue;

	private MultiLoginIBrowserType(String multiloginValue) {
		this.multiloginValue = multiloginValue;
	}
	
	public String getMultiLoginValue() {
		return this.multiloginValue;
	}
}
