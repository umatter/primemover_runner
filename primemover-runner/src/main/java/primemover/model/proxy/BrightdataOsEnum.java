package primemover.model.proxy;

public enum BrightdataOsEnum {
	WINDOWS("windows"), MAC("osx"), ANDROID("android");

	private String os;

	BrightdataOsEnum(String os) {
		this.os = os;
	}

	public String getOperatingSystem() {
		return this.os;
	}

}
