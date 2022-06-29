package primemover.model.jobs;

import primemover.model.SelectorType;

public abstract class SelectorBaseJob extends BaseJob {

	private String selector;
	private SelectorType type;

	public SelectorBaseJob() {
		super();
	}

	public SelectorBaseJob(Long id, String name, String description, String task, SelectorType type, String selector) {
		super(id, name, description, task);
		this.type = type;
		this.selector = selector;
	}

	public String getSelector() {
		return selector;
	}

	public void setSelector(String selector) {
		this.selector = selector;
	}

	public SelectorType getType() {
		return type;
	}

	public void setType(SelectorType type) {
		this.type = type;
	}
	
	

}