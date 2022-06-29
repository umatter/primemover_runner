package primemover.model.jobs;

import primemover.model.SelectorType;

public class SwitchToJob extends SelectorBaseJob {

	public SwitchToJob() {
	}

	public SwitchToJob(Long id, String name, String description, String task, SelectorType type, String selector) {
		super(id, name, description, task, type, selector);
	}

}
