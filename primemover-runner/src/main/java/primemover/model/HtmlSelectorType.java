package primemover.model;

public enum HtmlSelectorType implements ISelectorType {
	XPATH(1), //
	CSS(2), //
	CLASS(3), //
	ID(4), //
	TEXT(5);

	private final int code;

	private HtmlSelectorType(int code) {
		this.code = code;
	}

	@Override
	public int getCode() {
		return this.code;
	}
}
