package primemover.model.jobs;

import primemover.model.HtmlSelectorType;

public abstract class SelectorBaseJob extends BaseJob {

	private String selector;
	private HtmlSelectorType type;

	public SelectorBaseJob() {
		super();
	}

	public SelectorBaseJob(Long id, String name, String description, String task, HtmlSelectorType type, String selector) {
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

	public HtmlSelectorType getType() {
		return type;
	}

	public void setType(HtmlSelectorType type) {
		this.type = type;
	}

}