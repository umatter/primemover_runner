package primemover.model.jobs;

import primemover.model.SelectorType;
import primemover.model.TypingModeType;

public class EnterTextFieldJob extends SelectorBaseJob {
	private TypingModeType typingMode;
	private Boolean appendReturn;
	private String value;
	
	public EnterTextFieldJob(Long id, String name, String description, String task, SelectorType type, String fieldSelector, String value, TypingModeType typingMode, Boolean appendEnter) {
		super(id, name, description, task, type, fieldSelector);
		this.value = value;
		this.typingMode = typingMode;
		this.appendReturn = appendEnter;
	}
	
	public EnterTextFieldJob(Long id, String name, String description, String task, SelectorType type, String fieldSelector, String value, TypingModeType typingMode) {
		this(id, name, description, task, type, fieldSelector, value, typingMode, true);
	}
	
	public TypingModeType getTypingMode() {
		return typingMode;
	}
	public void setTypingMode(TypingModeType typingMode) {
		this.typingMode = typingMode;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public EnterTextFieldJob() {
		super();
	}

	public Boolean getAppendReturn() {
		return appendReturn;
	}

	public void setAppendReturn(Boolean appendReturn) {
		this.appendReturn = appendReturn;
	}
	
	
	
}
