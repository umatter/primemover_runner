package primemover.model.jobs;

import primemover.model.DialogHandleType;

public class TryHandleAlertJob extends HandleAlertJob {

	public TryHandleAlertJob() {
	}

	public TryHandleAlertJob(Long id, String name, String description, DialogHandleType action) {
		super(id, name, description, action);
	}

}
