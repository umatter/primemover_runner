package primemover.model;

public abstract class BaseJob implements IJob {
	Long id;
	String reference;
	String title;

	public BaseJob() {
		super();
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public String getReference() {
		return reference;
	}

	@Override
	public String getTitle() {
		return title;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public BaseJob(Long id, String reference, String title) {
		super();
		this.id = id;
		this.reference = reference;
		this.title = title;
	}
}