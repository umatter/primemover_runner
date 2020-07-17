package primemover.model;

public enum SelectorType {
	XPATH(1),
	CSS(2),
	CLASS(3),
	ID(4);
	
	private final int code;

    private SelectorType(int code) {
        this.code = code;
    }
    
    public int getCode() {
    	return this.code;
    }
}