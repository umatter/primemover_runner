package primemover.model.jobs;

import primemover.captcha.CaptchaSolvingType;
import primemover.model.IJob;

public abstract class BaseJob implements IJob {
	Long id;
	String description;
	String name;
	CaptchaSolvingType captchaSolving;
	Boolean runFailsave = false; // Defaults to false
	
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
	
	public void setCaptchaSolving(CaptchaSolvingType type) {
		this.captchaSolving = type;
	}
	
	@Override
	public CaptchaSolvingType getCaptchaSolving() {
		return this.captchaSolving;
	}

	public void setIsFailsave(Boolean value) {
		this.runFailsave = value;
	}

	@Override
	public Boolean isFailsave() {
		return this.runFailsave;
	}

	public BaseJob(Long id, String name, String description) {
		super();
		this.id = id;
		this.name = name;
		this.description = description;
	}

	public BaseJob(Long id, String name, String description, Boolean isFailsave) {
		super();
		this.id = id;
		this.name = name;
		this.description = description;
		this.runFailsave = isFailsave;
	}
}