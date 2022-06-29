package primemover.model.jobs;

import primemover.model.SelectorType;

public class ClickJob extends SelectorBaseJob {

	private boolean _expectsPageReload = false;

	public ClickJob() {
	}

	public ClickJob(Long id, String name, String description, String task, SelectorType type, String selector, Boolean expectsPageReload) {
		super(id, name, description, task, type, selector);

		this._expectsPageReload = expectsPageReload;
	}

	public Boolean expectsPageReload() {
		return this._expectsPageReload;
	}

	public void setExpectsPageReload(Boolean value) {
		this._expectsPageReload = value;
	}
}
