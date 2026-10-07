package primemover.model.jobs;

/** Clear the browser's web storage for the current origin. */
public class ClearStorageJob extends BaseJob {

	private ClearStorageScope scope;

	public ClearStorageJob() {
		super();
	}

	public ClearStorageJob(Long id, String name, String description, ClearStorageScope scope) {
		super();
		setId(id);
		setName(name);
		setDescription(description);
		this.scope = scope;
	}

	public ClearStorageScope getScope() {
		return scope;
	}

	public void setScope(ClearStorageScope scope) {
		this.scope = scope;
	}
}
