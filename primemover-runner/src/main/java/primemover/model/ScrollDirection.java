package primemover.model;

public enum ScrollDirection {
	UP(1),
	DOWN(2);
	
	private final int code;

    private ScrollDirection(int code) {
        this.code = code;
    }

	public int getCode() {
		return code;
	}
}