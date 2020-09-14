package primemover.model;

public enum MultiLoginIOsType {
	WINDOWS("win"),
	MAC("mac"),
	LINUX("linux"),
	ANDROID("android");

	private String multiloginValue;

	private MultiLoginIOsType(String multiloginValue) {
		this.multiloginValue = multiloginValue;
	}
	
	public String getMultiLoginValue() {
		return this.multiloginValue;
	}
	
}
