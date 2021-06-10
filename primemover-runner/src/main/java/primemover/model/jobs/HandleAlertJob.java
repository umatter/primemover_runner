package primemover.model.jobs;

import primemover.model.DialogHandleType;

public class HandleAlertJob extends BaseJob {
	private DialogHandleType action;

	public DialogHandleType getAction() {
		return this.action;
	}

	public void setAction(DialogHandleType action) {
		this.action = action;
	}

	public HandleAlertJob() {
		super();
	}

	public HandleAlertJob(Long id, String name, String description, DialogHandleType action) {
		super(id, name, description);
		
		this.action = action;
	}
}
