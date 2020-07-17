package primemover.model;

public class EnterTextField extends SelectorBaseJob {
	
	private String value;
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public EnterTextField() {
		super();
	}
	
	public EnterTextField(Long id, String reference, String title, SelectorType type, String fieldSelector, String value) {
		super(id, reference, title, type, fieldSelector);
		this.value = value;
	}
	
}
