package primemover.model.jobs;

import primemover.model.HtmlSelectorType;

public class SwitchToJob extends SelectorBaseJob {

	public SwitchToJob() {
	}

	public SwitchToJob(Long id, String name, String description, String task, HtmlSelectorType type, String selector) {
		super(id, name, description, task, type, selector);
	}

}
