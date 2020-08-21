package primemover.model;

public abstract class BaseJob implements IJob {
	Long id;
	String description;
	String name;

	public BaseJob() {
		super();
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setDescription(String reference) {
		this.description = reference;
	}

	public void setName(String title) {
		this.name = title;
	}

	public BaseJob(Long id, String name, String description) {
		super();
		this.id = id;
		this.name = name;
		this.description = description;
	}
}