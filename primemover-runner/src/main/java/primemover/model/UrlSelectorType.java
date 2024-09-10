package primemover.model;

public enum UrlSelectorType implements ISelectorType {

	REGEXP(10);

	private final int code;

	private UrlSelectorType(int code) {
		this.code = code;
	}

	@Override
	public int getCode() {
		return this.code;
	}

}
