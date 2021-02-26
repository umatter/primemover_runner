package primemover.model.jobs;

import primemover.model.SelectorType;

public class TrySwitchToJob extends SelectorBaseJob {

	public TrySwitchToJob() {
	}

	public TrySwitchToJob(Long id, String name, String description, SelectorType type, String selector) {
		super(id, name, description, type, selector);
	}

}
