package primemover.model;

public enum MultiLoginIOsType {
	WINDOWS("windows"),
	MAC("macos"),
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
