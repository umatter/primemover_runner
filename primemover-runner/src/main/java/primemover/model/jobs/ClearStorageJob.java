package primemover.model.jobs;

import java.util.List;

/** Clear browsing data: web storage, or everything a privacy-conscious user clears. */
public class ClearStorageJob extends BaseJob {

	private ClearStorageScope scope;

	/**
	 * Origins whose site data {@link ClearStorageScope#ALL} clears, beyond the browser-wide
	 * cookie and cache clear. Named explicitly because CDP's per-origin clear takes one
	 * origin and a wildcard is not a documented guarantee.
	 */
	private List<String> origins = List.of();

	public ClearStorageJob() {
		super();
	}

	public ClearStorageJob(Long id, String name, String description, ClearStorageScope scope,
			List<String> origins) {
		super();
		setId(id);
		setName(name);
		setDescription(description);
		this.scope = scope;
		setOrigins(origins);
	}

	public List<String> getOrigins() {
		return origins;
	}

	public void setOrigins(List<String> origins) {
		this.origins = origins == null ? List.of() : List.copyOf(origins);
	}

	public ClearStorageScope getScope() {
		return scope;
	}

	public void setScope(ClearStorageScope scope) {
		this.scope = scope;
	}
}
