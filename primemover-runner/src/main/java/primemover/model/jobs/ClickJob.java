package primemover.model.jobs;

import primemover.model.SelectorType;

public class ClickJob extends SelectorBaseJob {

	public ClickJob() {
	}

	public ClickJob(Long id, String name, String description, SelectorType type, String selector) {
		super(id, name, description, type, selector);
	}

}
